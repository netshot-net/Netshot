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
package net.netshot.netshot.task.handler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import net.netshot.netshot.access.AccessSettings;
import net.netshot.netshot.access.Snmp;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.AuthProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.PrivProtocol;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.platform.net.NetworkAddress;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.vault.SecretResolver;

/**
 * Discovery of the type of simulated devices by SNMP, through the task engine: the driver matching the
 * sysObjectID and sysDescr is found, the device is created and its first snapshot submitted.
 *
 * <p>The handler polls the standard SNMP port, which the simulator containers don't publish on the host: the
 * devices are reached at their address on the Docker network instead. This works on Linux and in the CI (the
 * tests run in a container of the same network); elsewhere (e.g. Docker Desktop), the tests are skipped.</p>
 */
@DatabaseTest
@SimulatorTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Discover device type task on simulated devices (DiscoverDeviceTypeHandler)")
class DiscoverDeviceTypeSimulatorTest {

	/** A Cisco small business switch (CiscoSx driver), which answers SNMP v1, v2c and v3. */
	@Container
	private static final GenericContainer<?> SWITCH = DeviceSimulators.ssh("Cisco_Sx");

	/** An SNMP-only device, of no specific type (GenericSNMP driver). */
	@Container
	private static final GenericContainer<?> GENERIC = DeviceSimulators.snmp("generic1");

	private TaskRunner runner;

	private Domain domain;

	private static String networkAddress(GenericContainer<?> container) {
		return container.getContainerInfo().getNetworkSettings().getNetworks().values().iterator().next().getIpAddress();
	}

	private static DeviceSnmpv2cCommunity v2c(String community) {
		return DeviceSnmpv2cCommunity.builder().community(Secret.local(community)).name("v2c " + community).build();
	}

	@BeforeAll
	void checkReachable() {
		try {
			Snmp poller = new Snmp(NetworkAddress.getNetworkAddress(networkAddress(SWITCH)), v2c("public"),
				AccessSettings.defaults().snmp(), SecretResolver.LOCAL);
			try {
				poller.getAsString("1.3.6.1.2.1.1.2.0");
			}
			finally {
				poller.stop();
			}
		}
		catch (Exception e) {
			Assumptions.abort("The simulated devices can't be reached at their Docker network address ("
				+ e.getMessage() + "), as with Docker Desktop");
		}
	}

	@BeforeEach
	void createRunner() {
		this.runner = new TaskRunner();
		this.domain = TestDevices.addDomain("Lab", "Test domain");
	}

	@AfterEach
	void cleanUp() {
		TaskRunner.cleanDatabase();
	}

	private Task discovery(String address, DeviceCredentialSet... credentialSets) {
		Set<Long> credentialSetIds = new HashSet<>();
		for (DeviceCredentialSet credentialSet : credentialSets) {
			credentialSetIds.add(TestDevices.addCredentialSet(credentialSet).id());
		}
		return Task.of(DiscoverDeviceTypeTask.builder().deviceAddress(address).domainId(this.domain.id())
			.credentialSetIds(credentialSetIds).deviceComments("Found by discovery").deviceTags(List.of("lab", " new "))
			.build(), "Discovery", address, "tester");
	}

	/**
	 * Runs a discovery, which has to succeed.
	 *
	 * @param expectedDriver the driver which has to be found
	 * @param address the address of the device
	 * @param credentialSets the credential sets to try
	 * @return the new device
	 */
	private Device discovers(String expectedDriver, String address, DeviceCredentialSet... credentialSets) throws Exception {
		long id = this.runner.run(this.discovery(address, credentialSets));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.SUCCESS, task.status(), task.log());
		DiscoverDeviceTypeTask spec = (DiscoverDeviceTypeTask) task.spec();
		Assertions.assertEquals(expectedDriver, spec.discoveredDeviceType());
		Assertions.assertNotNull(spec.discoverResultDeviceId());

		Device device = TestDevices.find(spec.discoverResultDeviceId());
		Assertions.assertEquals(expectedDriver, device.driver());
		Assertions.assertEquals(address, device.mgmtAddress());
		Assertions.assertEquals(this.domain.id(), device.mgmtDomain().id());
		Assertions.assertEquals("Found by discovery", device.comments());
		Assertions.assertEquals(Set.of("lab", "new"), Set.copyOf(device.tags()));
		Assertions.assertEquals("tester", device.creator());

		// The first snapshot of the device, child of the discovery
		Assertions.assertEquals(1, this.runner.submitted().size());
		Task snapshot = this.runner.submitted().get(0);
		Assertions.assertEquals(spec.snapshotTaskId(), snapshot.id());
		Assertions.assertEquals(id, snapshot.parentTaskId());
		Assertions.assertEquals(device.id(), ((TakeSnapshotTask) snapshot.spec()).deviceId());
		return device;
	}

	@Test
	@DisplayName("Discovers the switch by SNMPv2c, creates it with an access row per driver access")
	void discoversByV2c() throws Exception {
		Device device = this.discovers("CiscoSx", networkAddress(SWITCH), v2c("public"));

		Assertions.assertFalse(device.accesses().isEmpty(), "The driver accesses are created");
		Assertions.assertTrue(device.accesses().stream().map(DeviceAccess::accessName).anyMatch("ssh"::equals),
			device.accesses().toString());
	}

	@Test
	@DisplayName("Discovers the switch by SNMPv1")
	void discoversByV1() throws Exception {
		this.discovers("CiscoSx", networkAddress(SWITCH),
			DeviceSnmpv1Community.builder().community(Secret.local("public")).name("v1 public").build());
	}

	@Test
	@DisplayName("Discovers the switch by SNMPv3 (SHA, AES)")
	void discoversByV3() throws Exception {
		this.discovers("CiscoSx", networkAddress(SWITCH), DeviceSnmpv3Community.builder().name("v3 user")
			.username(Secret.local("netshotv3")).authType(AuthProtocol.SHA).authKey(Secret.local("admin1234"))
			.privType(PrivProtocol.AES128).privKey(Secret.local("admin1234")).build());
	}

	@Test
	@DisplayName("Goes on with the next credential set when one gets no answer")
	void triesTheNextCredentialSet() throws Exception {
		this.discovers("CiscoSx", networkAddress(SWITCH), v2c("wrong-community"), v2c("public"));
	}

	@Test
	@DisplayName("Falls back to the generic SNMP driver for a device of no known type")
	void fallsBackToGenericSnmp() throws Exception {
		this.discovers("GenericSNMP", networkAddress(GENERIC), v2c("public"));
	}

	@Test
	@DisplayName("Fails with wrong credentials, without creating a device")
	void failsWithWrongCredentials() throws Exception {
		long id = this.runner.run(this.discovery(networkAddress(SWITCH), v2c("wrong-community")));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.FAILURE, task.status());
		Assertions.assertEquals(0, TestDevices.count());
		Assertions.assertEquals(List.of(), this.runner.submitted());
	}
}
