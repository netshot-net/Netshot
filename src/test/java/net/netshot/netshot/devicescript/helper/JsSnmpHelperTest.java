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
package net.netshot.netshot.devicescript.helper;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import net.netshot.netshot.access.AccessManager;
import net.netshot.netshot.access.AccessSettings;
import net.netshot.netshot.access.Connectors;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.DeviceDriver.AccessDefinition;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeTaskContext;
import net.netshot.netshot.vault.SecretResolver;

@SimulatorTest
@DisplayName("SNMP helper of the device scripts")
class JsSnmpHelperTest {

	/** sysName.0, the hostname of the simulator. */
	private static final String SYS_NAME = "1.3.6.1.2.1.1.5.0";

	/** The system group. */
	private static final String SYSTEM = "1.3.6.1.2.1.1";

	@Container
	private static final GenericContainer<?> CONTAINER = DeviceSimulators.snmp("snmphelper");

	private static final DeviceCredentialSet WRONG = DeviceSnmpv2cCommunity.builder().name("wrong").community(Secret.local("wrong")).build();

	private static final DeviceCredentialSet RIGHT = DeviceSnmpv2cCommunity.builder().name("right").community(Secret.local("public")).build();

	private FakeTaskContext taskContext;

	@BeforeEach
	void fastTimeouts() {
		// A wrong community gets no answer: don't wait long for it
		Properties properties = new Properties();
		properties.setProperty("netshot.snmp.timeout", "300");
		properties.setProperty("netshot.snmp.retries", "0");
		TestConfig.init(properties);
		this.taskContext = new FakeTaskContext();
	}

	@AfterEach
	void resetConfig() {
		TestConfig.init(new Properties());
	}

	/**
	 * Builds the helper on the SNMPv2c access of the simulator, with the given pool of credentials.
	 *
	 * @param autoTry whether the helper tries the credentials by itself
	 * @param pool the credential sets to try, in order
	 * @return the helper
	 */
	private JsSnmpHelper helper(boolean autoTry, List<DeviceCredentialSet> pool) {
		Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
		DeviceAccess access = DeviceAccess.of("snmpv2c").toBuilder()
			.address(CONTAINER.getHost()).port(DeviceSimulators.mappedUdpPort(CONTAINER, 161)).build();
		DeviceDraft device = new DeviceDraft(Device.create("GenericSNMP", null, domain, "test").toBuilder()
			.accesses(List.of(access)).build());
		Connectors connectors = new Connectors(SecretResolver.LOCAL, TestConnectors.sshClient(), null, new AccessSettings(TestConfig.store()));
		AccessManager accessManager = new AccessManager(TestDrivers.registry(), connectors, () -> pool, device, null, this.taskContext, null);
		List<AccessDefinition> accesses = List.of(TestDrivers.registry().getDriverByName("GenericSNMP").getAccessDefinition("snmpv2c"));
		return new JsSnmpHelper(accessManager, accesses, autoTry, this.taskContext);
	}

	@Test
	@DisplayName("The helper tries the credential sets until one answers, then gets and walks")
	void autoTry() throws IOException {
		JsSnmpHelper snmp = this.helper(true, List.of(WRONG, RIGHT));

		Assertions.assertEquals("snmphelper", snmp.getAsString(SYS_NAME, false));
		ProxyObject system = snmp.walkAsString(SYSTEM, false);
		Assertions.assertEquals("snmphelper", system.getMember(SYS_NAME));
		Assertions.assertFalse(snmp.isErrored());
		String log = this.taskContext.getLog();
		Assertions.assertTrue(log.contains("using credentials 'wrong'"), log);
		Assertions.assertTrue(log.contains("using credentials 'right'"), log);
		Assertions.assertTrue(log.contains("Received SNMP response: '" + SYS_NAME + "' = 'snmphelper'."), log);
		Assertions.assertTrue(log.contains("SNMP response(s) for the walk:"), log);
	}

	@Test
	@DisplayName("Without auto-try, the script moves to the next credential set itself, until there is none left")
	void manualTry() throws IOException {
		JsSnmpHelper snmp = this.helper(false, List.of(WRONG, RIGHT));

		Assertions.assertThrows(IOException.class, () -> snmp.getAsString(SYS_NAME, false), "Wrong community");
		Assertions.assertTrue(snmp.tryNextCredentials(), "No next credential set");
		Assertions.assertEquals("snmphelper", snmp.getAsString(SYS_NAME, false));
		Assertions.assertFalse(snmp.tryNextCredentials(), "Credential sets left");
	}

	@Test
	@DisplayName("Without any working credential set, the request fails")
	void noWorkingCredentials() {
		JsSnmpHelper snmp = this.helper(true, List.of(WRONG));

		Assertions.assertThrows(IOException.class, () -> snmp.getAsString(SYS_NAME, false));
		Assertions.assertThrows(IOException.class, () -> snmp.walkAsString(SYSTEM, false));
	}
}
