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
package net.netshot.netshot.inventory.device;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DeviceDriver.Location;
import net.netshot.netshot.driver.DeviceDriver.LocationType;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestTls;

@ScriptEngineTest
@DisplayName("Validation of the device inputs (options, accesses)")
class DeviceValidationTest {

	/** A driver with a Boolean option, a free text option and a text option with choices, and an SSH access. */
	private static DeviceDriver driver;

	/** A global credential set, as given by a client (only its ID matters). */
	private static final DeviceCredentialSet GLOBAL = DeviceSshAccount.builder().id(42).name("global")
		.username(Secret.local("admin")).password(Secret.local("admin")).build();

	@BeforeAll
	static void loadDriver() throws Exception {
		driver = new DeviceDriver(new StringReader("""
			var Info = { name: "LabOS", author: "test", description: "Lab OS", version: "1.0" };
			var Config = {};
			var Device = {};
			var Options = {
				fullBackup: { type: "Boolean", title: "Full backup" },
				comment: { type: "Text", title: "Comment" },
				source: { type: "Text", title: "Source", choices: ["running", "startup"] },
			};
			var CLI = { ssh: { macros: { enable: {}, configure: {} } } };
			function snapshot(cli, device, config) {}
			"""), "LabOS.js", new Location(LocationType.EMBEDDED, "LabOS.js"));
	}

	private static void assertRefused(String message, Executable executable) {
		NetshotException error = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(message, error.getMessage());
	}

	@Test
	@DisplayName("The options are checked against the declarations of the driver; a Boolean may be given as text")
	void options() {
		Map<String, Object> options = new HashMap<>();
		options.put("fullBackup", "true");
		options.put("comment", "free text");
		options.put("source", "startup");
		options.put("unset", null);
		assertRefused("Unknown option 'unset' for this device type.", () -> DeviceValidation.validateOptions(driver, options));
		options.remove("unset");
		options.put("comment", null);

		Map<String, Object> validated = DeviceValidation.validateOptions(driver, options);
		Assertions.assertEquals(Boolean.TRUE, validated.get("fullBackup"), "Text converted to a boolean");
		Assertions.assertEquals("startup", validated.get("source"));
		Assertions.assertTrue(validated.containsKey("comment") && validated.get("comment") == null, "A null value is kept");

		Assertions.assertNull(DeviceValidation.validateOptions(driver, null));
		Assertions.assertEquals(Map.of(), DeviceValidation.validateOptions(null, Map.of()));
		assertRefused("Cannot set options without a known device type.",
			() -> DeviceValidation.validateOptions(null, Map.of("fullBackup", true)));
		assertRefused("Option 'fullBackup' should be a boolean.", () -> DeviceValidation.validateOptions(driver, Map.of("fullBackup", "yes")));
		assertRefused("Option 'fullBackup' should be a boolean.", () -> DeviceValidation.validateOptions(driver, Map.of("fullBackup", 1)));
		assertRefused("Option 'comment' should be a string.", () -> DeviceValidation.validateOptions(driver, Map.of("comment", 12)));
		assertRefused("Option 'source' is not one of the allowed choices.",
			() -> DeviceValidation.validateOptions(driver, Map.of("source", "candidate")));
	}

	private static List<DeviceAccess> validate(DeviceDriver deviceDriver, DeviceAccess access, List<AccessSpecificCredential> specifics) {
		return DeviceValidation.validateAccesses(null, null, deviceDriver, List.of(access), specifics);
	}

	@Test
	@DisplayName("An access gets the default trust modes; its name, address and port are checked")
	void accesses() {
		DeviceAccess validated = validate(driver, DeviceAccess.of("ssh").toBuilder().address("10.1.1.1").port(2222).build(), null).get(0);
		Assertions.assertEquals(DeviceAccess.SshHostKeyVerification.TRUST_ANY, validated.sshHostKeyVerification());
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, validated.httpsCaTrustMode());
		Assertions.assertNull(DeviceValidation.validateAccesses(null, null, driver, null, null));

		assertRefused("Missing access name in one of the device accesses.", () -> validate(driver, DeviceAccess.of(""), null));
		assertRefused("Invalid port for access 'ssh'.", () -> validate(driver, DeviceAccess.of("ssh").toBuilder().port(0).build(), null));
		assertRefused("Invalid port for access 'ssh'.", () -> validate(driver, DeviceAccess.of("ssh").toBuilder().port(65536).build(), null));
		Assertions.assertThrows(NetshotException.class,
			() -> validate(driver, DeviceAccess.of("ssh").toBuilder().address("224.0.0.1").build(), null), "Multicast address");
		Assertions.assertThrows(IllegalArgumentException.class,
			() -> validate(driver, DeviceAccess.of("ssh").toBuilder().specificCredentialSet(GLOBAL).build(), null),
			"Specific credential set inline");
	}

	@Test
	@DisplayName("The custom CA trust mode needs a valid certificate")
	void customCa() {
		DeviceAccess customCa = DeviceAccess.of("ssh").toBuilder().httpsCaTrustMode(HttpsCaTrustMode.CUSTOM_CA).build();
		assertRefused("Access 'ssh' requires a custom CA certificate when the HTTPS trust mode is CUSTOM_CA.",
			() -> validate(driver, customCa, null));
		assertRefused("Access 'ssh' requires a custom CA certificate when the HTTPS trust mode is CUSTOM_CA.",
			() -> validate(driver, customCa.toBuilder().httpsCustomCaCertificate("  ").build(), null));
		NetshotException invalid = Assertions.assertThrows(NetshotException.class,
			() -> validate(driver, customCa.toBuilder().httpsCustomCaCertificate("-----BEGIN CERTIFICATE-----\nnot base64\n").build(), null));
		Assertions.assertTrue(invalid.getMessage().startsWith("Invalid custom CA certificate for access 'ssh': "), invalid.getMessage());

		String pem = TestTls.authority("Lab CA").pem();
		DeviceAccess validated = validate(driver, customCa.toBuilder().httpsCustomCaCertificate(pem).build(), null).get(0);
		Assertions.assertEquals(pem, validated.httpsCustomCaCertificate());
	}

	@Test
	@DisplayName("A global credential set is pinned only on a known access of a known device type, and not with a specific one")
	void globalCredentials() {
		DeviceAccess pinned = DeviceAccess.of("ssh").toBuilder().globalCredentialSet(GLOBAL).build();
		assertRefused("Cannot set both a global and a specific credential set for access 'ssh'.",
			() -> validate(driver, pinned, List.of(new AccessSpecificCredential("ssh", null))));
		assertRefused("Cannot pin credentials for access 'ssh' without a known device type.", () -> validate(null, pinned, null));
		assertRefused("Unknown access 'console' for this device type.",
			() -> validate(driver, pinned.toBuilder().accessName("console").build(), null));
	}

	@Test
	@DisplayName("A specific credential set needs a known access without global credential set, on a known device type")
	void specificCredentials() {
		List<DeviceAccess> accesses = List.of(DeviceAccess.of("ssh"), DeviceAccess.of("console"),
			DeviceAccess.of("pinned").toBuilder().globalCredentialSet(GLOBAL).build());
		Assertions.assertSame(accesses, DeviceValidation.applySpecificCredentials(null, null, null, driver, List.of(), accesses, null));
		assertRefused("Unknown access 'telnet' for the specific credential set.", () -> DeviceValidation.applySpecificCredentials(
			null, null, null, driver, List.of(), accesses, List.of(new AccessSpecificCredential("telnet", null))));
		assertRefused("Cannot set both a global and a specific credential set for access 'pinned'.", () -> DeviceValidation.applySpecificCredentials(
			null, null, null, driver, List.of(), accesses, List.of(new AccessSpecificCredential("pinned", null))));
		assertRefused("Cannot pin credentials for access 'ssh' without a known device type.", () -> DeviceValidation.applySpecificCredentials(
			null, null, null, null, List.of(), accesses, List.of(new AccessSpecificCredential("ssh", null))));
		assertRefused("Unknown access 'console' for this device type.", () -> DeviceValidation.applySpecificCredentials(
			null, null, null, driver, List.of(), accesses, List.of(new AccessSpecificCredential("console", null))));
	}
}
