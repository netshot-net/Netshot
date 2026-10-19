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

import java.util.List;

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
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.testing.DatabaseTest;

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
		this.domain = new Domain("Lab", "Test domain", null, null);
		TaskRunner.persist(this.domain);
	}

	@AfterEach
	void cleanUp() {
		TaskRunner.cleanDatabase();
	}

	private DiscoverDeviceTypeTask discovery(String address, DeviceCredentialSet... credentialSets) {
		DiscoverDeviceTypeTask task = new DiscoverDeviceTypeTask(address, this.domain, "Discovery", "tester");
		for (DeviceCredentialSet credentialSet : credentialSets) {
			TaskRunner.persist(credentialSet);
			task.addCredentialSet(credentialSet);
		}
		return task;
	}

	private static long deviceCount() {
		return TaskRunner.inSession(session -> session.createQuery("select count(d) from Device d", Long.class).uniqueResult());
	}

	@Test
	@DisplayName("Fails when the address can't be resolved")
	void failsOnUnresolvableAddress() throws Exception {
		long id = this.runner.run(this.discovery("no-such-device.invalid", new DeviceSnmpv2cCommunity("public", "community")));

		DiscoverDeviceTypeTask task = TaskRunner.load(DiscoverDeviceTypeTask.class, id);
		Assertions.assertEquals(Status.FAILURE, task.getStatus());
		Assertions.assertTrue(task.getLog().contains("Unable to resolve device address 'no-such-device.invalid'"), task.getLog());
	}

	@Test
	@DisplayName("Fails without SNMP credential set")
	void failsWithoutSnmpCredentials() throws Exception {
		long id = this.runner.run(this.discovery("127.0.0.1", new DeviceSshAccount("admin", "admin", "admin", "ssh")));

		DiscoverDeviceTypeTask task = TaskRunner.load(DiscoverDeviceTypeTask.class, id);
		Assertions.assertEquals(Status.FAILURE, task.getStatus());
		Assertions.assertTrue(task.getLog().contains("No available SNMP credential set... cannot start autodiscovery."), task.getLog());
		Assertions.assertEquals(0, deviceCount());
	}

	@Test
	@DisplayName("Fails when the device doesn't answer SNMP, without creating a device nor a snapshot task")
	void failsWithoutAnswer() throws Exception {
		long id = this.runner.run(this.discovery("127.0.0.1", new DeviceSnmpv2cCommunity("netshot-test-no-answer", "community")));

		DiscoverDeviceTypeTask task = TaskRunner.load(DiscoverDeviceTypeTask.class, id);
		Assertions.assertEquals(Status.FAILURE, task.getStatus());
		Assertions.assertTrue(task.getLog().contains("Error while polling the device via SNMP."), task.getLog());
		Assertions.assertEquals(0, deviceCount());
		Assertions.assertEquals(List.of(), this.runner.submitted());
		Assertions.assertEquals(List.<Device>of(), TaskRunner.inSession(session -> session
			.createQuery("select t.discoverResultDevice from DiscoverDeviceTypeTask t where t.id = :id", Device.class)
			.setParameter("id", id).list()));
	}
}
