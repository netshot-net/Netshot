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
package net.netshot.netshot.platform.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Configuration settings")
public class SettingTest {

	@TempDir
	Path tempDir;

	private static Config config(Map<String, String> file, Map<String, String> environment) {
		Properties properties = new Properties();
		properties.putAll(file);
		return Config.of(properties, environment);
	}

	private static Config config(Map<String, String> file) {
		return config(file, Map.of());
	}

	@Test
	@DisplayName("Typed values, defaults and ranges")
	void typedValues() {
		Setting<Integer> port = Setting.integer("netshot.test.port").defaultValue(514).range(1, 65535).restart();
		Setting<Boolean> flag = Setting.bool("netshot.test.flag").defaultValue(true).live();
		Setting<Long> limit = Setting.longInteger("netshot.test.limit").defaultValue(10L).live();
		Setting<String> text = Setting.text("netshot.test.text").live();
		Setting<List<String>> list = Setting.list("netshot.test.list", ", *").defaultValue(List.of("a")).live();

		Config empty = config(Map.of());
		Assertions.assertEquals(514, empty.get(port));
		Assertions.assertTrue(empty.get(flag));
		Assertions.assertEquals(10L, empty.get(limit));
		Assertions.assertNull(empty.get(text));
		Assertions.assertEquals(List.of("a"), empty.get(list));

		Config valid = config(Map.of(
			"netshot.test.port", "1514",
			"netshot.test.flag", "false",
			"netshot.test.limit", "8589934592",
			"netshot.test.text", "hello",
			"netshot.test.list", "x, y,z"));
		Assertions.assertEquals(1514, valid.get(port));
		Assertions.assertFalse(valid.get(flag));
		Assertions.assertEquals(8589934592L, valid.get(limit));
		Assertions.assertEquals("hello", valid.get(text));
		Assertions.assertEquals(List.of("x", "y", "z"), valid.get(list));

		Config invalid = config(Map.of(
			"netshot.test.port", "70000",
			"netshot.test.flag", "yes",
			"netshot.test.limit", "big"));
		Assertions.assertEquals(514, invalid.get(port), "Out of range");
		Assertions.assertTrue(invalid.get(flag), "Only true/false are booleans");
		Assertions.assertEquals(10L, invalid.get(limit), "Not a number");
	}

	@Test
	@DisplayName("The environment overrides the file")
	void environmentOverridesFile() {
		Setting<String> text = Setting.text("netshot.test.some.text").live();
		Assertions.assertEquals("NETSHOT_TEST_SOME_TEXT", Config.environmentName(text.key()));
		Config config = config(Map.of("netshot.test.some.text", "file"), Map.of("NETSHOT_TEST_SOME_TEXT", "env"));
		Assertions.assertEquals("env", config.get(text));
		Assertions.assertEquals("file", config(Map.of("netshot.test.some.text", "file")).get(text));
	}

	@Test
	@DisplayName("The legacy key is read when the setting isn't set")
	void alias() {
		Setting<String> driver = Setting.text("netshot.db.driverclass").alias("netshot.db.driver_class")
			.defaultValue("org.postgresql.Driver").restart();
		Assertions.assertEquals("org.postgresql.Driver", config(Map.of()).get(driver));
		Assertions.assertEquals("legacy", config(Map.of("netshot.db.driver_class", "legacy")).get(driver));
		Assertions.assertEquals("new", config(Map.of("netshot.db.driver_class", "legacy", "netshot.db.driverclass", "new")).get(driver));
		Assertions.assertTrue(driver.matches("netshot.db.driver_class"));
	}

	@Test
	@DisplayName("Secret settings are read from their file")
	void secret() throws Exception {
		Path file = this.tempDir.resolve("secret");
		Files.writeString(file, "fromfile\n", StandardCharsets.UTF_8);
		Setting<String> secret = Setting.text("netshot.test.secret").secret().live();
		Assertions.assertTrue(secret.isSecret());
		Assertions.assertEquals("inline", config(Map.of("netshot.test.secret", "inline")).get(secret));
		Assertions.assertEquals("fromfile", config(Map.of("netshot.test.secret", "inline",
			"netshot.test.secret.file", file.toString())).get(secret));
		Assertions.assertEquals("fromfile", config(Map.of(), Map.of("NETSHOT_TEST_SECRET_FILE", file.toString())).get(secret));
	}

	@Test
	@DisplayName("Templates: actual settings and matching keys")
	void templates() {
		Setting<Integer> timeout = Setting.integer("netshot.aaa.radiusN.timeout").defaultValue(5).range(1, 300).live();
		Assertions.assertTrue(timeout.isTemplate());
		Setting<Integer> second = timeout.at(2);
		Assertions.assertEquals("netshot.aaa.radius2.timeout", second.key());
		Assertions.assertFalse(second.isTemplate());
		Assertions.assertEquals(5, config(Map.of()).get(second));
		Assertions.assertEquals(9, config(Map.of("netshot.aaa.radius2.timeout", "9")).get(second));
		Assertions.assertEquals(Setting.Reload.LIVE, second.reload());
		Assertions.assertTrue(timeout.matches("netshot.aaa.radius12.timeout"));
		Assertions.assertFalse(timeout.matches("netshot.aaa.radiusx.timeout"));
		Assertions.assertFalse(timeout.matches("netshot.aaa.radius.timeout"));

		Setting<String> logClass = Setting.text("netshot.log.class.<ClassName>").live();
		Assertions.assertTrue(logClass.isTemplate());
		Assertions.assertTrue(logClass.matches("netshot.log.class.org.apache.sshd"));
		Assertions.assertEquals("netshot.log.class.org.jooq", logClass.at("org.jooq").key());

		Setting<String> plain = Setting.text("netshot.snmptrap.engineid").live();
		Assertions.assertFalse(plain.isTemplate());
		Assertions.assertFalse(plain.matches("netshot.snmptrap.engineidx"));
		Assertions.assertThrows(IllegalStateException.class, () -> plain.at(1));
	}
}
