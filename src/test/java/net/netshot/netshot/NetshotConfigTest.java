/**
 * Copyright 2013-present Netshot
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
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigException;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.TestConfig;

@UnitTest
@DisplayName("Configuration items")
class NetshotConfigTest {

	private static final String KEY = "netshot.test.secret";

	@TempDir
	Path tempDir;

	private static Config current() {
		return TestConfig.store().current();
	}

	@AfterEach
	void resetConfig() {
		TestConfig.init(new Properties());
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
		TestConfig.init(config);
	}

	@Test
	@DisplayName("Secret is read from the file, which takes precedence over the inline value")
	void fileOverInline() throws Exception {
		initConfig("inline", writeSecretFile("fromfile"));
		Assertions.assertEquals("fromfile", current().getSecret(KEY, "default"));
	}

	@Test
	@DisplayName("Inline value is used when no secret file is set")
	void inlineValue() {
		initConfig("inline", null);
		Assertions.assertEquals("inline", current().getSecret(KEY, "default"));
	}

	@Test
	@DisplayName("Default value is used when nothing is set")
	void defaultValue() {
		initConfig(null, null);
		Assertions.assertEquals("default", current().getSecret(KEY, "default"));
		Assertions.assertNull(current().getSecret(KEY));
	}

	@Test
	@DisplayName("Trailing line breaks are removed, other whitespace is kept")
	void trailingLineBreaks() throws Exception {
		initConfig(null, writeSecretFile(" pass word \r\n\n"));
		Assertions.assertEquals(" pass word ", current().getSecret(KEY));
	}

	@Test
	@DisplayName("Missing secret file is an error, without fallback")
	void missingFile() {
		initConfig("inline", tempDir.resolve("missing"));
		Assertions.assertThrows(IllegalStateException.class, () -> current().getSecret(KEY, "default"));
	}

	@Test
	@DisplayName("Empty secret file is an error")
	void emptyFile() throws Exception {
		initConfig("inline", writeSecretFile("\n"));
		Assertions.assertThrows(IllegalStateException.class, () -> current().getSecret(KEY, "default"));
	}

	@Test
	@DisplayName("Too big secret file is an error")
	void tooBigFile() throws Exception {
		initConfig(null, writeSecretFile("x".repeat(64 * 1024 + 1)));
		Assertions.assertThrows(IllegalStateException.class, () -> current().getSecret(KEY));
	}

	@Test
	@DisplayName("Secret is cached until the configuration is reloaded")
	void cache() throws Exception {
		Path file = writeSecretFile("first");
		initConfig(null, file);
		Assertions.assertEquals("first", current().getSecret(KEY));
		Files.writeString(file, "second", StandardCharsets.UTF_8);
		Assertions.assertEquals("first", current().getSecret(KEY), "The secret wasn't cached");
		initConfig(null, file);
		Assertions.assertEquals("second", current().getSecret(KEY), "The cache wasn't cleared on config reload");
	}

	@Test
	@DisplayName("The first readable configuration file is used")
	void readConfigFile() throws Exception {
		Path file = tempDir.resolve("netshot.conf");
		Files.writeString(file, "netshot.test.item = fromfile\n", StandardCharsets.UTF_8);
		Config config = Config.load(List.of(tempDir.resolve("missing.conf").toString(), file.toString()));
		Assertions.assertEquals("fromfile", config.get("netshot.test.item"));
	}

	@Test
	@DisplayName("Reading fails if no configuration file can be read, or if it's empty")
	void readConfigFailure() throws Exception {
		String missing = tempDir.resolve("missing.conf").toString();
		ConfigException e = Assertions.assertThrows(ConfigException.class, () -> Config.load(List.of(missing)));
		Assertions.assertTrue(e.getMessage().contains(missing), "The message tells the tried files");
		Path empty = tempDir.resolve("empty.conf");
		Files.writeString(empty, "# Nothing\n", StandardCharsets.UTF_8);
		Assertions.assertThrows(ConfigException.class, () -> Config.load(List.of(empty.toString())));
	}

	@Test
	@DisplayName("Encryption password: legacy key name, then default value")
	void encryptionPasswordDefault() {
		Properties config = new Properties();
		TestConfig.init(config);
		Assertions.assertEquals("NETSHOT", Database.getEncryptionPassword(TestConfig.store().current()));
		config.setProperty("netshot.db.encryptionPassword", "legacy");
		TestConfig.init(config);
		Assertions.assertEquals("legacy", Database.getEncryptionPassword(TestConfig.store().current()));
	}

	@Test
	@DisplayName("Encryption password: error if required but not set")
	void encryptionPasswordRequired() throws Exception {
		Properties config = new Properties();
		config.setProperty("netshot.db.encryptionpassword.required", "true");
		TestConfig.init(config);
		Assertions.assertThrows(IllegalStateException.class, () -> Database.getEncryptionPassword(TestConfig.store().current()));
		config.setProperty("netshot.db.encryptionpassword.file", writeSecretFile("fromfile").toString());
		TestConfig.init(config);
		Assertions.assertEquals("fromfile", Database.getEncryptionPassword(TestConfig.store().current()));
	}
}
