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
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.driver.DeviceDriver.AccessDefinition;
import net.netshot.netshot.driver.DeviceDriver.DriverProtocol;
import net.netshot.netshot.driver.DeviceDriver.Location;
import net.netshot.netshot.driver.DeviceDriver.LocationType;
import net.netshot.netshot.driver.protocol.AuthScheme;
import net.netshot.netshot.testing.ScriptEngineTest;

/**
 * Tests for the access "group"/"priority" defaulting and the HTTP/HTTPS
 * protocol split (Phase 4 of the generic multi-protocol client work).
 */
@ScriptEngineTest
@DisplayName("Device driver access test")
public class DriverAccessTest {

	private static final String TEST_DRIVER_JS = """
		var Info = {
			name: "TestAccessGroupsDriver",
			author: "test",
			description: "Test driver for access groups/priority",
			version: "1.0"
		};

		var Config = {};
		var Device = {};

		var CLI = {
			telnet: {},
			ssh: {},
		};

		var SNMP = {
			snmpv1: {},
			snmpv2c: {},
			snmpv3: {},
		};

		var HTTP = {
			http: {},
			https: {},
			custom: { protocol: "https", group: "custom", priority: 5 },
		};

		function snapshot(client, device, config) {
		}
		""";

	private static final String COOKIE_AUTH_DRIVER_JS = """
		var Info = {
			name: "CookieAuthDriver",
			author: "test",
			description: "Test driver for cookie-based HTTP auth",
			version: "1.0"
		};

		var Config = {};
		var Device = {};
		var CLI = { ssh: {} };

		var HTTP = {
			https: {
				auth: {
					type: "cookie",
					method: "post",
					path: "/login",
					data: {
						domain: "local",
						userName: "$$NetshotUsername$$",
						userPasswd: "$$NetshotPassword$$",
					},
					contentType: "json",
				}
			}
		};

		function snapshot(client, device, config) {
		}
		""";

	private static final String BAD_CASE_AUTH_TYPE_DRIVER_JS = """
		var Info = {
			name: "BadCaseAuthTypeDriver",
			author: "test",
			description: "test",
			version: "1.0"
		};

		var Config = {};
		var Device = {};
		var CLI = {};

		var HTTP = {
			https: {
				auth: {
					type: "APIKEY",
					in: "header",
					name: "X-API-Key",
				}
			}
		};

		function snapshot(client, device, config) {
		}
		""";

	private static final String BAD_SNMP_KEY_DRIVER_JS = """
		var Info = {
			name: "BadSnmpKeyDriver",
			author: "test",
			description: "test",
			version: "1.0"
		};

		var Config = {};
		var Device = {};

		var CLI = { ssh: {} };
		var SNMP = { snmp1: {} };

		function snapshot(client, device, config) {
		}
		""";

	private DeviceDriver buildTestDriver() throws Exception {
		return new DeviceDriver(new StringReader(TEST_DRIVER_JS), "TestAccessGroupsDriver.js",
			new Location(LocationType.EMBEDDED, "TestAccessGroupsDriver.js"));
	}

	@Test
	void strictSnmpKeyMatchingRejectsAmbiguousNames() {
		// Previously "snmp1" would have matched SNMPv1 via a loose `.contains("1")`
		// check; the key must now be one of the exact "snmpv1"/"snmpv2c"/"snmpv3" names.
		Assertions.assertThrows(IllegalArgumentException.class, () -> new DeviceDriver(
			new StringReader(BAD_SNMP_KEY_DRIVER_JS), "BadSnmpKeyDriver.js",
			new Location(LocationType.EMBEDDED, "BadSnmpKeyDriver.js")));
	}

	@Test
	void defaultGroupsAndPriorities() throws Exception {
		DeviceDriver driver = this.buildTestDriver();

		AccessDefinition ssh = driver.getAccessDefinition("ssh");
		Assertions.assertEquals("cli", ssh.getGroup());
		Assertions.assertEquals(100, ssh.getPriority());
		Assertions.assertEquals(22, ssh.getDefaultPort());

		AccessDefinition telnet = driver.getAccessDefinition("telnet");
		Assertions.assertEquals("cli", telnet.getGroup());
		Assertions.assertEquals(10, telnet.getPriority());
		Assertions.assertEquals(23, telnet.getDefaultPort());

		AccessDefinition snmpv1 = driver.getAccessDefinition("snmpv1");
		Assertions.assertEquals("snmp", snmpv1.getGroup());
		Assertions.assertEquals(20, snmpv1.getPriority());
		Assertions.assertEquals(161, snmpv1.getDefaultPort());

		AccessDefinition snmpv2c = driver.getAccessDefinition("snmpv2c");
		Assertions.assertEquals("snmp", snmpv2c.getGroup());
		Assertions.assertEquals(22, snmpv2c.getPriority());

		AccessDefinition snmpv3 = driver.getAccessDefinition("snmpv3");
		Assertions.assertEquals("snmp", snmpv3.getGroup());
		Assertions.assertEquals(80, snmpv3.getPriority());

		AccessDefinition http = driver.getAccessDefinition("http");
		Assertions.assertEquals(DriverProtocol.HTTP, http.getProtocol());
		Assertions.assertEquals("http", http.getGroup());
		Assertions.assertEquals(30, http.getPriority());
		Assertions.assertEquals(80, http.getDefaultPort());
		Assertions.assertEquals(DeviceHttpAccount.class, http.getCredentialClass());

		AccessDefinition https = driver.getAccessDefinition("https");
		Assertions.assertEquals(DriverProtocol.HTTPS, https.getProtocol());
		Assertions.assertEquals("http", https.getGroup());
		Assertions.assertEquals(90, https.getPriority());
		Assertions.assertEquals(443, https.getDefaultPort());
		Assertions.assertEquals(DeviceHttpAccount.class, https.getCredentialClass());
	}

	@Test
	void explicitGroupAndPriorityOverride() throws Exception {
		DeviceDriver driver = this.buildTestDriver();

		AccessDefinition custom = driver.getAccessDefinition("custom");
		Assertions.assertEquals(DriverProtocol.HTTPS, custom.getProtocol());
		Assertions.assertEquals("custom", custom.getGroup());
		Assertions.assertEquals(5, custom.getPriority());
	}

	@Test
	void defaultCliAccessesSortedByPriority() throws Exception {
		DeviceDriver driver = this.buildTestDriver();
		List<AccessDefinition> cliAccesses = driver.getDefaultCliAccessDefinitions();
		Assertions.assertEquals(2, cliAccesses.size());
		Assertions.assertEquals("ssh", cliAccesses.get(0).getName());
		Assertions.assertEquals("telnet", cliAccesses.get(1).getName());
	}

	@Test
	void defaultSnmpAccessesSortedByPriority() throws Exception {
		DeviceDriver driver = this.buildTestDriver();
		List<AccessDefinition> snmpAccesses = driver.getDefaultSnmpAccessDefinitions();
		Assertions.assertEquals(3, snmpAccesses.size());
		Assertions.assertEquals("snmpv3", snmpAccesses.get(0).getName());
		Assertions.assertEquals("snmpv2c", snmpAccesses.get(1).getName());
		Assertions.assertEquals("snmpv1", snmpAccesses.get(2).getName());
	}

	@Test
	void accessDefinitionsByGroup() throws Exception {
		DeviceDriver driver = this.buildTestDriver();

		List<AccessDefinition> httpGroup = driver.getAccessDefinitionsByGroup("http");
		Assertions.assertEquals(2, httpGroup.size());
		Assertions.assertEquals("https", httpGroup.get(0).getName());
		Assertions.assertEquals("http", httpGroup.get(1).getName());

		List<AccessDefinition> customGroup = driver.getAccessDefinitionsByGroup("custom");
		Assertions.assertEquals(1, customGroup.size());
		Assertions.assertEquals("custom", customGroup.get(0).getName());
	}

	@Test
	void cookieAuthSchemeIsParsed() throws Exception {
		DeviceDriver driver = new DeviceDriver(new StringReader(COOKIE_AUTH_DRIVER_JS), "CookieAuthDriver.js",
			new Location(LocationType.EMBEDDED, "CookieAuthDriver.js"));

		AuthScheme auth = driver.getAccessDefinition("https").getHttpConfig().getAuth();
		Assertions.assertEquals("cookie", auth.getType());
		Assertions.assertEquals("POST", auth.getMethod());
		Assertions.assertEquals("/login", auth.getPath());
		Assertions.assertEquals("json", auth.getContentType());
		Assertions.assertEquals(Map.of(
			"domain", "local",
			"userName", "$$NetshotUsername$$",
			"userPasswd", "$$NetshotPassword$$"
		), auth.getData());
	}

	@Test
	void authSchemeTypeIsCaseSensitive() {
		// "APIKEY" must be rejected: driver-declared auth constants like "apiKey"
		// are matched with exact case, not loosely/case-insensitively.
		Assertions.assertThrows(IllegalArgumentException.class, () -> new DeviceDriver(
			new StringReader(BAD_CASE_AUTH_TYPE_DRIVER_JS), "BadCaseAuthTypeDriver.js",
			new Location(LocationType.EMBEDDED, "BadCaseAuthTypeDriver.js")));
	}

}
