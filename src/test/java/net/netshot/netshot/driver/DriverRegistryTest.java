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
package net.netshot.netshot.driver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.ScriptEngineTest;

@ScriptEngineTest
@DisplayName("Driver registry")
class DriverRegistryTest {

	private static final String BUNDLED_DRIVER = "CiscoIOS12";

	@TempDir
	Path driversFolder;

	private Properties previousConfig;

	private DriverRegistry registry;

	@BeforeEach
	void setUp() {
		this.previousConfig = TestConfig.properties();
		Properties config = new Properties();
		config.setProperty("netshot.drivers.path", this.driversFolder.toString());
		TestConfig.init(config);
		this.registry = new DriverRegistry(new DriverLoader(TestConfig.store()));
	}

	@AfterEach
	void tearDown() {
		TestConfig.init(this.previousConfig);
	}

	private void writeUserDriver(String version) throws Exception {
		String source;
		try (var stream = DriverRegistryTest.class.getResourceAsStream("/drivers/Cisco_IOS.js")) {
			source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
		source = source.replace("name: \"CiscoIOS12\"", "name: \"UserTestDriver\"")
			.replace("description: \"Cisco IOS and IOS-XE\"", "description: \"User Test Driver\"")
			.replaceFirst("version: \"[^\"]*\"", "version: \"" + version + "\"");
		Files.writeString(this.driversFolder.resolve("UserTestDriver.js"), source, StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("Empty before the first reload")
	void emptyBeforeReload() {
		Assertions.assertTrue(this.registry.getAllDrivers().isEmpty());
		Assertions.assertEquals("", this.registry.getAllDriverHash());
		Assertions.assertEquals(0L, this.registry.stats().get("loads"));
		Assertions.assertNull(this.registry.getDriverByName(BUNDLED_DRIVER));
		Assertions.assertFalse(this.registry.isHealthy(), "Healthy without loaded drivers");
	}

	@Test
	@DisplayName("Lookups by name and description")
	void lookups() throws Exception {
		this.writeUserDriver("1.0");
		this.registry.start();
		Assertions.assertTrue(this.registry.isHealthy(), "Unhealthy with loaded drivers");
		Assertions.assertEquals(1L, this.registry.stats().get("loads"));
		Assertions.assertEquals(this.registry.getAllDrivers().size(), this.registry.stats().get("drivers"));
		Assertions.assertNotNull(this.registry.stats().get("lastLoad"));
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
	void hashChangesOnReload() throws Exception {
		this.writeUserDriver("1.0");
		this.registry.reload();
		String hash = this.registry.getAllDriverHash();
		Assertions.assertTrue(hash.matches("[0-9a-f]{64}"), hash);
		this.registry.reload();
		Assertions.assertEquals(hash, this.registry.getAllDriverHash());
		this.writeUserDriver("2.0");
		this.registry.reload();
		Assertions.assertNotEquals(hash, this.registry.getAllDriverHash());
		Assertions.assertEquals("2.0", this.registry.getDriverByName("UserTestDriver").getVersion());
		Files.delete(this.driversFolder.resolve("UserTestDriver.js"));
		this.registry.reload();
		Assertions.assertNull(this.registry.getDriverByName("UserTestDriver"));
	}

	/**
	 * Reloads the drivers with the given user drivers path, and gets what the loader logged.
	 *
	 * @param path the folder of the user drivers
	 * @return the events logged by the driver loader
	 * @throws Exception if the reload fails
	 */
	private List<ILoggingEvent> reloadWithPath(Path path) throws Exception {
		Properties config = new Properties();
		config.setProperty("netshot.drivers.path", path.toString());
		TestConfig.init(config);
		Logger logger = (Logger) LoggerFactory.getLogger(DriverLoader.class);
		ListAppender<ILoggingEvent> logs = new ListAppender<>();
		logs.start();
		logger.addAppender(logs);
		try {
			this.registry.reload();
		}
		finally {
			logger.detachAppender(logs);
		}
		return logs.list;
	}

	@Test
	@DisplayName("A missing user drivers folder is optional, an unreadable one is an error")
	void userDriversFolder() throws Exception {
		List<ILoggingEvent> missing = this.reloadWithPath(this.driversFolder.resolve("missing"));
		Assertions.assertTrue(missing.stream().noneMatch(e -> e.getLevel() == Level.ERROR), "No error for a missing folder");
		Assertions.assertTrue(missing.stream().anyMatch(e -> e.getLevel() == Level.INFO && e.getFormattedMessage().contains("doesn't exist")));
		Assertions.assertNotNull(this.registry.getDriverByName(BUNDLED_DRIVER), "The bundled drivers are loaded");

		Path notAFolder = Files.writeString(this.driversFolder.resolve("file"), "", StandardCharsets.UTF_8);
		List<ILoggingEvent> unreadable = this.reloadWithPath(notAFolder);
		Assertions.assertTrue(unreadable.stream().anyMatch(e -> e.getLevel() == Level.ERROR
			&& e.getFormattedMessage().contains("Unable to read the folder")));
		Assertions.assertNotNull(this.registry.getDriverByName(BUNDLED_DRIVER), "The bundled drivers are loaded");
	}
}
