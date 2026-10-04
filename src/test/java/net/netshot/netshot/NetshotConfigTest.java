/**
 * Copyright 2013-2025 Netshot
 * 
 * This file is part of Netshot project.
 * 
 * Netshot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Netshot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Netshot.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.netshot.netshot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.database.Database;

@DisplayName("Secret configuration items")
public class NetshotConfigTest {

	private static final String KEY = "netshot.test.secret";

	@TempDir
	Path tempDir;

	@AfterEach
	void resetConfig() {
		Netshot.initConfig(new Properties());
	}

	private Path writeSecretFile(String content) throws Exception {
		Path file = tempDir.resolve("secret");
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}

	private void initConfig(String inlineValue, Path secretFile) {
		Properties config = new Properties();
		if (inlineValue != null) {
			config.setProperty(KEY, inlineValue);
		}
		if (secretFile != null) {
			config.setProperty(KEY + ".file", secretFile.toString());
		}
		Netshot.initConfig(config);
	}

	@Test
	@DisplayName("Secret is read from the file, which takes precedence over the inline value")
	void fileOverInline() throws Exception {
		initConfig("inline", writeSecretFile("fromfile"));
		Assertions.assertEquals("fromfile", Netshot.getSecretConfig(KEY, "default"));
	}

	@Test
	@DisplayName("Inline value is used when no secret file is set")
	void inlineValue() {
		initConfig("inline", null);
		Assertions.assertEquals("inline", Netshot.getSecretConfig(KEY, "default"));
	}

	@Test
	@DisplayName("Default value is used when nothing is set")
	void defaultValue() {
		initConfig(null, null);
		Assertions.assertEquals("default", Netshot.getSecretConfig(KEY, "default"));
		Assertions.assertNull(Netshot.getSecretConfig(KEY));
	}

	@Test
	@DisplayName("Trailing line breaks are removed, other whitespace is kept")
	void trailingLineBreaks() throws Exception {
		initConfig(null, writeSecretFile(" pass word \r\n\n"));
		Assertions.assertEquals(" pass word ", Netshot.getSecretConfig(KEY));
	}

	@Test
	@DisplayName("Missing secret file is an error, without fallback")
	void missingFile() {
		initConfig("inline", tempDir.resolve("missing"));
		Assertions.assertThrows(IllegalStateException.class, () -> Netshot.getSecretConfig(KEY, "default"));
	}

	@Test
	@DisplayName("Empty secret file is an error")
	void emptyFile() throws Exception {
		initConfig("inline", writeSecretFile("\n"));
		Assertions.assertThrows(IllegalStateException.class, () -> Netshot.getSecretConfig(KEY, "default"));
	}

	@Test
	@DisplayName("Too big secret file is an error")
	void tooBigFile() throws Exception {
		initConfig(null, writeSecretFile("x".repeat(64 * 1024 + 1)));
		Assertions.assertThrows(IllegalStateException.class, () -> Netshot.getSecretConfig(KEY));
	}

	@Test
	@DisplayName("Secret is cached until the configuration is reloaded")
	void cache() throws Exception {
		Path file = writeSecretFile("first");
		initConfig(null, file);
		Assertions.assertEquals("first", Netshot.getSecretConfig(KEY));
		Files.writeString(file, "second", StandardCharsets.UTF_8);
		Assertions.assertEquals("first", Netshot.getSecretConfig(KEY), "The secret wasn't cached");
		initConfig(null, file);
		Assertions.assertEquals("second", Netshot.getSecretConfig(KEY), "The cache wasn't cleared on config reload");
	}

	@Test
	@DisplayName("Encryption password: legacy key name, then default value")
	void encryptionPasswordDefault() {
		Properties config = new Properties();
		Netshot.initConfig(config);
		Assertions.assertEquals("NETSHOT", Database.getEncryptionPassword());
		config.setProperty("netshot.db.encryptionPassword", "legacy");
		Netshot.initConfig(config);
		Assertions.assertEquals("legacy", Database.getEncryptionPassword());
	}

	@Test
	@DisplayName("Encryption password: error if required but not set")
	void encryptionPasswordRequired() throws Exception {
		Properties config = new Properties();
		config.setProperty("netshot.db.encryptionpassword.required", "true");
		Netshot.initConfig(config);
		Assertions.assertThrows(IllegalStateException.class, Database::getEncryptionPassword);
		config.setProperty("netshot.db.encryptionpassword.file", writeSecretFile("fromfile").toString());
		Netshot.initConfig(config);
		Assertions.assertEquals("fromfile", Database.getEncryptionPassword());
	}
}
