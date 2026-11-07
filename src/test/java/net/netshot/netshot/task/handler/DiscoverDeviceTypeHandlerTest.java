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
package net.netshot.netshot.task.handler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;

/**
 * Characterization of the failure paths of {@link DiscoverDeviceTypeHandler}, run through the task engine.
 * The discovery itself (SNMP answer, driver match, device creation) is not covered: the handler polls
 * the standard SNMP port only, which a simulator container doesn't offer on the host.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Discover device type task (DiscoverDeviceTypeHandler)")
class DiscoverDeviceTypeHandlerTest {

	private TaskRunner runner;

	private Domain domain;

	@BeforeEach
	void createRunner() {
		this.runner = new TaskRunner();
	}

	@BeforeEach
	void createDomain() {
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
		return DiscoverDeviceTypeTask.of(address, this.domain.id(), credentialSetIds, "Discovery", "tester");
	}

	private static long deviceCount() {
		return TestDevices.count();
	}

	@Test
	@DisplayName("Fails when the address can't be resolved")
	void failsOnUnresolvableAddress() throws Exception {
		long id = this.runner.run(this.discovery("no-such-device.invalid", DeviceSnmpv2cCommunity.builder().community(Secret.local("public")).name("community").build()));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.FAILURE, task.status());
		Assertions.assertTrue(task.log().contains("Unable to resolve device address 'no-such-device.invalid'"), task.log());
	}

	@Test
	@DisplayName("Fails without SNMP credential set")
	void failsWithoutSnmpCredentials() throws Exception {
		long id = this.runner.run(this.discovery("127.0.0.1", DeviceSshAccount.builder().username(Secret.local("admin")).password(Secret.local("admin"))
			.superPassword(Secret.local("admin")).name("ssh").build()));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.FAILURE, task.status());
		Assertions.assertTrue(task.log().contains("No available SNMP credential set... cannot start autodiscovery."), task.log());
		Assertions.assertEquals(0, deviceCount());
	}

	@Test
	@DisplayName("Fails when the device doesn't answer SNMP, without creating a device nor a snapshot task")
	void failsWithoutAnswer() throws Exception {
		long id = this.runner.run(this.discovery("127.0.0.1", DeviceSnmpv2cCommunity.builder().community(Secret.local("netshot-test-no-answer")).name("community").build()));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.FAILURE, task.status());
		Assertions.assertTrue(task.log().contains("Error while polling the device via SNMP."), task.log());
		Assertions.assertEquals(0, deviceCount());
		Assertions.assertEquals(List.of(), this.runner.submitted());
		Assertions.assertNull(((DiscoverDeviceTypeTask) task.spec()).discoverResultDeviceId());
	}
}
