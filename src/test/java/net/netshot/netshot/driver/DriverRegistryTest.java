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
package net.netshot.netshot.driver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.TestConfig;


@DisplayName("Driver registry")
public class DriverRegistryTest {

	private static final String BUNDLED_DRIVER = "CiscoIOS12";

	@TempDir
	Path driversFolder;

	private Properties previousConfig;

	private DriverRegistry registry;

	@BeforeEach
	void setUp() {
		this.previousConfig = Netshot.getConfig();
		Properties config = new Properties();
		config.setProperty("netshot.drivers.path", this.driversFolder.toString());
		TestConfig.init(config);
		this.registry = new DriverRegistry(new DriverLoader());
	}

	@AfterEach
	void tearDown() {
		TestConfig.init(this.previousConfig == null ? new Properties() : this.previousConfig);
	}

	private void writeUserDriver(String version) throws Exception {
		String source;
		try (var stream = DriverRegistryTest.class.getResourceAsStream("/drivers/Cisco_IOS.js")) {
			source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
		source = source.replace("name: \"CiscoIOS12\"", "name: \"UserTestDriver\"")
			.replace("description: \"Cisco IOS and IOS-XE\"", "description: \"User Test Driver\"")
			.replace("version: \"2.7\"", "version: \"" + version + "\"");
		Files.writeString(this.driversFolder.resolve("UserTestDriver.js"), source, StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("Empty before the first refresh")
	void emptyBeforeRefresh() {
		Assertions.assertTrue(this.registry.getAllDrivers().isEmpty());
		Assertions.assertEquals("", this.registry.getAllDriverHash());
		Assertions.assertNull(this.registry.getDriverByName(BUNDLED_DRIVER));
	}

	@Test
	@DisplayName("Lookups by name and description")
	void lookups() throws Exception {
		this.writeUserDriver("1.0");
		this.registry.refresh();
		DeviceDriver bundled = this.registry.getDriverByName(BUNDLED_DRIVER);
		Assertions.assertNotNull(bundled);
		Assertions.assertSame(bundled, this.registry.getDriverByDescription(bundled.getDescription().toUpperCase()));
		DeviceDriver user = this.registry.getDriverByName("UserTestDriver");
		Assertions.assertNotNull(user);
		Assertions.assertSame(user, this.registry.getDriverByDescription("user test driver"));
		Assertions.assertNull(this.registry.getDriverByName("NoSuchDriver"));
		Assertions.assertNull(this.registry.getDriverByName(null));
		Assertions.assertNull(this.registry.getDriverByDescription("No such driver"));
		Assertions.assertNull(this.registry.getDriverByDescription(null));
		Assertions.assertTrue(this.registry.getAllDrivers().contains(user));
		Assertions.assertEquals(this.registry.getAllDrivers().size(), this.registry.getDrivers().size());
		Assertions.assertEquals(this.registry.getAllDrivers().stream().sorted().toList(), this.registry.getAllDrivers());
	}

	@Test
	@DisplayName("The global hash changes when a driver changes, and only then")
	void hashChangesOnRefresh() throws Exception {
		this.writeUserDriver("1.0");
		this.registry.refresh();
		String hash = this.registry.getAllDriverHash();
		Assertions.assertTrue(hash.matches("[0-9a-f]{64}"), hash);
		this.registry.refresh();
		Assertions.assertEquals(hash, this.registry.getAllDriverHash());
		this.writeUserDriver("2.0");
		this.registry.refresh();
		Assertions.assertNotEquals(hash, this.registry.getAllDriverHash());
		Assertions.assertEquals("2.0", this.registry.getDriverByName("UserTestDriver").getVersion());
		Files.delete(this.driversFolder.resolve("UserTestDriver.js"));
		this.registry.refresh();
		Assertions.assertNull(this.registry.getDriverByName("UserTestDriver"));
	}
}
