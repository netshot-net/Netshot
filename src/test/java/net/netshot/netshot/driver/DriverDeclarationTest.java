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

import java.io.StringReader;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.driver.AttributeDefinition.AttributeLevel;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.driver.DeviceDriver.AccessDefinition;
import net.netshot.netshot.driver.DeviceDriver.DriverProtocol;
import net.netshot.netshot.driver.DeviceDriver.Location;
import net.netshot.netshot.driver.DeviceDriver.LocationType;
import net.netshot.netshot.testing.ScriptEngineTest;

/**
 * What a driver declares in its header (Info, Config, Device, CLI, SNMP, HTTP), as read when the driver is loaded.
 */
@ScriptEngineTest
@DisplayName("Declarations of a driver")
class DriverDeclarationTest {

	private static final String INFO = """
		var Info = { name: "LabOS", author: "test", description: "Lab OS", version: "1.0" };
		""";

	private static final String SNAPSHOT = """
		function snapshot(cli, device, config) {}
		""";

	private static DeviceDriver load(String js) throws Exception {
		return new DeviceDriver(new StringReader(js), "LabOS.js", new Location(LocationType.EMBEDDED, "LabOS.js"));
	}

	/**
	 * Loads an invalid driver and gets the message of the deepest cause of the failure.
	 *
	 * @param js the code of the driver
	 * @return the messages of the failure and its causes, joined
	 */
	private static String failure(String js) {
		Exception error = Assertions.assertThrows(IllegalArgumentException.class, () -> load(js));
		StringBuilder messages = new StringBuilder();
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			messages.append(cause.getMessage()).append(" / ");
		}
		return messages.toString();
	}

	@Test
	@DisplayName("Named accesses: explicit protocol, description, default port, HTTP base path, CLI modes")
	void namedAccesses() throws Exception {
		DeviceDriver driver = load(INFO + """
			var Config = {};
			var Device = {};
			var CLI = {
				console: { protocol: "SSH", description: "Console server", config: { defaultPort: 2222 },
					macros: { enable: {}, configure: {} } },
				legacy: { protocol: "telnet" },
				enable: { prompt: /^#$/ },
				banner: "not an access",
			};
			var SNMP = {
				poller: { protocol: "snmpv3", description: "Poller" },
				old: { protocol: "SNMPv1" },
				snmpv2c: { group: "monitoring", priority: 3 },
			};
			var HTTP = {
				api: { protocol: "http", config: { basePath: "/api", defaultPort: 8080 } },
				rest: { protocol: "https", config: { basePath: "/rest" } },
				mgmt: {},
			};
			""" + SNAPSHOT);

		AccessDefinition console = driver.getAccessDefinition("console");
		Assertions.assertEquals(DriverProtocol.SSH, console.getProtocol());
		Assertions.assertEquals("Console server", console.getDescription());
		Assertions.assertEquals(2222, console.getDefaultPort());
		Assertions.assertNotNull(console.getSshConfig());
		Assertions.assertNull(console.getTelnetConfig());
		Assertions.assertEquals(Set.of("enable", "configure"), driver.getCliMainModes());

		AccessDefinition legacy = driver.getAccessDefinition("legacy");
		Assertions.assertEquals(DriverProtocol.TELNET, legacy.getProtocol());
		Assertions.assertEquals(23, legacy.getDefaultPort());
		Assertions.assertNotNull(legacy.getTelnetConfig());
		Assertions.assertNull(legacy.getDescription());
		Assertions.assertNull(driver.getAccessDefinition("enable"), "A CLI mode isn't an access");
		Assertions.assertNull(driver.getAccessDefinition("banner"));

		Assertions.assertEquals(DriverProtocol.SNMPV3, driver.getAccessDefinition("poller").getProtocol());
		Assertions.assertEquals("Poller", driver.getAccessDefinition("poller").getDescription());
		Assertions.assertEquals(DriverProtocol.SNMPV1, driver.getAccessDefinition("old").getProtocol());
		AccessDefinition snmpv2c = driver.getAccessDefinition("snmpv2c");
		Assertions.assertEquals(DriverProtocol.SNMPV2C, snmpv2c.getProtocol());
		Assertions.assertEquals("monitoring", snmpv2c.getGroup());
		Assertions.assertEquals(3, snmpv2c.getPriority());

		AccessDefinition api = driver.getAccessDefinition("api");
		Assertions.assertEquals(DriverProtocol.HTTP, api.getProtocol());
		Assertions.assertFalse(api.getHttpConfig().isTls());
		Assertions.assertEquals("/api", api.getHttpConfig().getBasePath());
		Assertions.assertEquals(8080, api.getDefaultPort());
		AccessDefinition rest = driver.getAccessDefinition("rest");
		Assertions.assertTrue(rest.getHttpConfig().isTls());
		Assertions.assertEquals("/rest", rest.getHttpConfig().getBasePath());
		Assertions.assertEquals(443, rest.getDefaultPort());
		AccessDefinition mgmt = driver.getAccessDefinition("mgmt");
		Assertions.assertEquals(DriverProtocol.HTTPS, mgmt.getProtocol(), "An HTTP access is HTTPS unless named 'http'");
		Assertions.assertEquals(443, mgmt.getDefaultPort());

		Assertions.assertEquals(List.of("api", "legacy"),
			driver.getAccessDefinitions(List.of("api", "legacy")).stream().map(AccessDefinition::getName).toList());
		IllegalArgumentException unknown = Assertions.assertThrows(IllegalArgumentException.class,
			() -> driver.getAccessDefinitions(List.of("api", "gone")));
		Assertions.assertEquals("Unknown access 'gone'.", unknown.getMessage());
	}

	@Test
	@DisplayName("An unknown protocol is refused, for CLI, SNMP and HTTP accesses")
	void unknownProtocols() {
		String failure = failure(INFO + "var Config = {}; var Device = {}; var CLI = { x: { protocol: \"rlogin\" } };" + SNAPSHOT);
		Assertions.assertTrue(failure.contains("Invalid CLI object.") && failure.contains("Invalid protocol 'rlogin' for CLI access 'x'."), failure);
		failure = failure(INFO + "var Config = {}; var Device = {}; var SNMP = { x: { protocol: \"snmpv4\" } };" + SNAPSHOT);
		Assertions.assertTrue(failure.contains("Invalid SNMP object.") && failure.contains("Invalid protocol 'snmpv4' for SNMP access 'x'."), failure);
		failure = failure(INFO + "var Config = {}; var Device = {}; var HTTP = { x: { protocol: \"ftp\" } };" + SNAPSHOT);
		Assertions.assertTrue(failure.contains("Invalid HTTP object.") && failure.contains("Invalid protocol 'ftp' for HTTP access 'x'."), failure);
	}

	@Test
	@DisplayName("A driver needs an access and a snapshot function")
	void accessAndSnapshotRequired() {
		String failure = failure(INFO + "var Config = {}; var Device = {};" + SNAPSHOT);
		Assertions.assertTrue(failure.contains("it supports neither Telnet, SSH, SNMP nor HTTP/HTTPS"), failure);
		failure = failure(INFO + "var Config = {}; var Device = {}; var CLI = { ssh: {} };");
		Assertions.assertTrue(failure.contains("the 'snapshot' function cannot be found"), failure);
		failure = failure(INFO + "var Config = {}; var Device = {}; var CLI = { ssh: {} }; var snapshot = 3;");
		Assertions.assertTrue(failure.contains("the 'snapshot' function cannot be found"), failure);
	}

	@Test
	@DisplayName("Info: the priority is optional; drivers compare by priority (highest first), then by name")
	void info() throws Exception {
		String body = "var Config = {}; var Device = {}; var CLI = { ssh: {} };" + SNAPSHOT;
		DeviceDriver plain = load(INFO + body);
		Assertions.assertEquals(65536, plain.getPriority());
		Assertions.assertEquals("DeviceDriver [name=LabOS, author=test, description=Lab OS, version=1.0, priority=65536]", plain.toString());
		DeviceDriver preferred = load("var Info = { name: \"LabOS2\", author: \"test\", description: \"Lab OS 2\", version: \"2\", priority: 70000 };"
			+ body);
		DeviceDriver other = load("var Info = { name: \"AnotherOS\", author: \"test\", description: \"Another\", version: \"1\" };" + body);
		Assertions.assertTrue(preferred.compareTo(plain) < 0, "Highest priority first");
		Assertions.assertTrue(other.compareTo(plain) < 0, "Same priority: by name");
		Assertions.assertEquals(0, plain.compareTo(plain));
		Assertions.assertTrue(plain.compareTo(null) < 0);
		Assertions.assertEquals(plain, load(INFO + body), "Equal by name");
		Assertions.assertNotEquals(plain, other);
		Assertions.assertEquals(plain.hashCode(), load(INFO + body).hashCode());
	}

	@Test
	@DisplayName("Config and Device items: names, titles, types, flags and dump settings")
	void attributes() throws Exception {
		DeviceDriver driver = load(INFO + """
			var Config = {
				runningConfig: { type: "LongText", title: "Running configuration", comparable: true, searchable: true, checkable: true,
					dump: { pre: "! Start", preLine: "> ", post: "! End", postLine: " <" } },
				image: { type: "BinaryFile", title: "Image file" },
				count: { type: "Numeric", title: "Route count", dump: false },
				flag: { type: "Binary", title: "Flag (secure)", dump: true },
			};
			var Device = {
				contract: { type: "Text", title: "Support contract", searchable: true },
			};
			var CLI = { ssh: {} };
			""" + SNAPSHOT);

		AttributeDefinition running = driver.getAttributeDefinition(AttributeLevel.CONFIG, "runningConfig");
		Assertions.assertEquals(AttributeType.LONGTEXT, running.getType());
		Assertions.assertTrue(running.isComparable() && running.isSearchable() && running.isCheckable() && running.isDump());
		Assertions.assertEquals(List.of("! Start", "> ", "! End", " <"),
			List.of(running.getPreDump(), running.getPreLineDump(), running.getPostDump(), running.getPostLineDump()));
		Assertions.assertEquals(AttributeType.BINARYFILE, driver.getAttributeDefinition(AttributeLevel.CONFIG, "image").getType());
		AttributeDefinition count = driver.getAttributeDefinition(AttributeLevel.CONFIG, "count");
		Assertions.assertEquals(AttributeType.NUMERIC, count.getType());
		Assertions.assertFalse(count.isDump() || count.isComparable() || count.isSearchable() || count.isCheckable());
		AttributeDefinition flag = driver.getAttributeDefinition(AttributeLevel.CONFIG, "flag");
		Assertions.assertEquals(AttributeType.BINARY, flag.getType());
		Assertions.assertTrue(flag.isDump());
		Assertions.assertNull(flag.getPreDump());
		AttributeDefinition contract = driver.getAttributeDefinition(AttributeLevel.DEVICE, "contract");
		Assertions.assertEquals(AttributeType.TEXT, contract.getType());
		Assertions.assertNull(driver.getAttributeDefinition(AttributeLevel.CONFIG, "contract"), "Device and Config items are apart");
		Assertions.assertEquals(
			"AttributeDefinition [type=TEXT, level=DEVICE, name=contract, title=Support contract, comparable=false, searchable=true]",
			contract.toString());

		Assertions.assertSame(contract, driver.getAttributeDefinitionByTitle("support CONTRACT"), "By title, ignoring the case");
		Assertions.assertNull(driver.getAttributeDefinitionByTitle("Nothing"));
	}

	@Test
	@DisplayName("Invalid Config and Device items are refused, naming the item")
	void invalidAttributes() {
		String failure = failure(INFO + "var Config = { 'Bad-Name': { type: \"Text\", title: \"Bad\" } }; var Device = {}; var CLI = { ssh: {} };"
			+ SNAPSHOT);
		Assertions.assertTrue(failure.contains("Invalid Config object.") && failure.contains("Invalid config item Bad-Name."), failure);
		failure = failure(INFO + "var Config = {}; var Device = { x: { type: \"Text\", title: \"X\" } }; var CLI = { ssh: {} };" + SNAPSHOT);
		Assertions.assertTrue(failure.contains("Invalid Device object.") && failure.contains("Invalid device item x."), failure);
		failure = failure(INFO + "var Config = { item: { type: \"Text\", title: \"Bad/title\" } }; var Device = {}; var CLI = { ssh: {} };"
			+ SNAPSHOT);
		Assertions.assertTrue(failure.contains("Invalid item item in Config.") && failure.contains("Invalid title for item item."), failure);
		failure = failure(INFO + "var Config = {}; var Device = { item: { type: \"Date\", title: \"Some date\" } }; var CLI = { ssh: {} };"
			+ SNAPSHOT);
		Assertions.assertTrue(failure.contains("Invalid item item in Device.") && failure.contains("Invalid type for item item."), failure);
	}
}
