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

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.driver.DeviceDriver.LocationType;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestConfig;

/**
 * Loading of the drivers: the user drivers from their folder, then the embedded ones, from the class path folder
 * (as in the tests and the dev stack) or from the Netshot jar (as in a release).
 */
@ScriptEngineTest
@DisplayName("Driver loader")
class DriverLoaderTest {

	@TempDir
	Path userFolder;

	@TempDir
	Path jarFolder;

	private Properties previousConfig;

	@BeforeEach
	void setUp() {
		this.previousConfig = TestConfig.properties();
		Properties config = new Properties();
		config.setProperty("netshot.drivers.path", this.userFolder.toString());
		TestConfig.init(config);
	}

	@AfterEach
	void tearDown() {
		TestConfig.init(this.previousConfig);
	}

	/**
	 * Builds the code of a minimal driver.
	 *
	 * @param name the name of the driver
	 * @param version the version of the driver
	 * @return the code
	 */
	private static String driver(String name, String version) {
		return """
			var Info = { name: "%s", author: "test", description: "%s driver", version: "%s" };
			var Config = {};
			var Device = {};
			var CLI = { ssh: {} };
			function snapshot(cli, device, config) {}
			""".formatted(name, name, version);
	}

	private void writeUserFile(String fileName, String content) throws IOException {
		Files.writeString(this.userFolder.resolve(fileName), content, StandardCharsets.UTF_8);
	}

	/**
	 * Builds a jar holding the given files, and gives the URL of its drivers folder.
	 *
	 * @param files the content of the files, by path in the jar
	 * @return the jar: URL of the drivers/ folder of the jar
	 */
	private URL jar(Map<String, String> files) throws IOException {
		Path jar = this.jarFolder.resolve("netshot.jar");
		try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
			output.putNextEntry(new JarEntry("drivers/"));
			output.closeEntry();
			for (Map.Entry<String, String> file : files.entrySet()) {
				output.putNextEntry(new JarEntry(file.getKey()));
				output.write(file.getValue().getBytes(StandardCharsets.UTF_8));
				output.closeEntry();
			}
		}
		return URI.create("jar:" + jar.toUri() + "!/drivers/").toURL();
	}

	private DriverLoader.LoadedDrivers load(URL embedded) throws Exception {
		return new DriverLoader(TestConfig.store()).load(embedded);
	}

	@Test
	@DisplayName("The user drivers are loaded from their folder; broken files, other files and duplicate names are skipped")
	void userDrivers() throws Exception {
		this.writeUserFile("lab.js", driver("LabOS", "1.0"));
		this.writeUserFile("lab-copy.js", driver("LabOS", "1.0"));
		this.writeUserFile("broken.js", "var Info = ;");
		this.writeUserFile("notes.txt", driver("NotesOS", "1.0"));
		Files.createDirectory(this.userFolder.resolve("sub.js"));

		Map<String, DeviceDriver> drivers = this.load(null).drivers();
		Assertions.assertEquals(1, drivers.size(), "Drivers " + drivers.keySet());
		DeviceDriver lab = drivers.get("LabOS");
		Assertions.assertEquals(LocationType.FILE, lab.getLocation().getType());
	}

	@Test
	@DisplayName("A user driver replaces the embedded driver of the same name (class path folder)")
	void userDriverReplacesEmbedded() throws Exception {
		this.writeUserFile("ios.js", driver("CiscoIOS12", "99.0"));
		Map<String, DeviceDriver> drivers = this.load(DeviceDriver.class.getResource("/drivers/")).drivers();
		Assertions.assertTrue(drivers.size() > 30, "The embedded drivers are loaded");
		Assertions.assertEquals("99.0", drivers.get("CiscoIOS12").getVersion());
		Assertions.assertEquals(LocationType.FILE, drivers.get("CiscoIOS12").getLocation().getType());
		Assertions.assertEquals(LocationType.EMBEDDED, drivers.get("JuniperJunos").getLocation().getType());
	}

	@Test
	@DisplayName("From a jar: the .js files of the drivers folder, replaced by the user drivers of the same name")
	void embeddedInJar() throws Exception {
		this.writeUserFile("override.js", driver("OverriddenOS", "2.0"));
		URL embedded = this.jar(Map.of(
			"drivers/Lab.js", driver("LabOS", "1.0"),
			"drivers/Overridden.js", driver("OverriddenOS", "1.0"),
			"drivers/Broken.js", "var Info = ;",
			"drivers/README.md", "Not a driver",
			"other/Other.js", driver("OtherOS", "1.0")));
		Map<String, DeviceDriver> drivers = this.load(embedded).drivers();
		Assertions.assertEquals(Set.of("LabOS", "OverriddenOS"), drivers.keySet());
		DeviceDriver lab = drivers.get("LabOS");
		Assertions.assertEquals(LocationType.EMBEDDED, lab.getLocation().getType());
		Assertions.assertTrue(lab.getLocation().getFileName().endsWith("netshot.jar"), lab.getLocation().getFileName());
		Assertions.assertEquals("2.0", drivers.get("OverriddenOS").getVersion(), "The user driver wins");
	}

	@Test
	@DisplayName("The global hash depends on the loaded drivers")
	void hash() throws Exception {
		URL embedded = this.jar(Map.of("drivers/Lab.js", driver("LabOS", "1.0")));
		String first = this.load(embedded).hash();
		Assertions.assertEquals(first, this.load(embedded).hash());
		this.writeUserFile("other.js", driver("OtherOS", "1.0"));
		Assertions.assertNotEquals(first, this.load(embedded).hash());
	}
}
