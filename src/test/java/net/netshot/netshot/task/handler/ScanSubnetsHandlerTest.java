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
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.ScanSubnetsTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestProperty;

/**
 * Characterization of {@link ScanSubnetsHandler}, run through the task engine (the discovery tasks are
 * not run, see {@link TaskRunner}): the discovery tasks it creates for the addresses of the subnets.
 */
@DatabaseTest
@TestProperty(name = "netshot.tasks.sequentialpollintervalms", value = "500")
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Scan subnets task (ScanSubnetsHandler)")
class ScanSubnetsHandlerTest {

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

	private Task scan(String address, int prefixLength) throws Exception {
		return ScanSubnetsTask.of(Set.of(new Network4Address(address, prefixLength)), this.domain.id(), "Scan", address, "tester");
	}

	private void community(String name, Domain mgmtDomain) {
		TestDevices.addCredentialSet(DeviceSnmpv2cCommunity.builder().community("public").name(name).mgmtDomain(mgmtDomain).build());
	}

	private static Set<String> addresses(List<Task> children) {
		return children.stream().map(child -> ((DiscoverDeviceTypeTask) child.spec()).deviceAddress()).collect(Collectors.toSet());
	}

	@Test
	@DisplayName("Creates a discovery task per address of the subnet without device, with the usable SNMP communities")
	void discoversTheSubnet() throws Exception {
		Domain otherDomain = TestDevices.addDomain("Other", "Other domain");
		TestDevices.add(Device.create("CiscoIOS12", "10.20.0.3", this.domain, "tester"));
		this.community("global", null);
		this.community("same domain", this.domain);
		this.community("other domain", otherDomain);
		TestDevices.addCredentialSet(DeviceSshAccount.builder().username("admin").password("admin").superPassword("admin").name("ssh").build());

		long id = this.runner.run(this.scan("10.20.0.0", 29));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(id).status());
		List<Task> children = TaskRunner.children(id);
		Assertions.assertEquals(Set.of("10.20.0.1", "10.20.0.2", "10.20.0.4", "10.20.0.5", "10.20.0.6"), addresses(children),
			"Neither the network and broadcast addresses, nor the address of an existing device");
		Assertions.assertEquals(children.size(), this.runner.scheduledChildIds().size());
		for (Task child : children) {
			DiscoverDeviceTypeTask discovery = (DiscoverDeviceTypeTask) child.spec();
			Assertions.assertEquals(Set.of("global", "same domain"), discovery.credentialSetIds().stream()
				.map(credentialSetId -> TestDevices.findCredentialSet(credentialSetId).name()).collect(Collectors.toSet()));
			Assertions.assertEquals(this.domain.id(), discovery.domainId());
			Assertions.assertEquals("Scan", child.comments());
			Assertions.assertEquals("tester", child.author());
		}
	}

	@Test
	@DisplayName("Scans the single address of a /32")
	void scansASingleAddress() throws Exception {
		this.community("global", null);
		long id = this.runner.run(this.scan("10.20.0.9", 32));

		Assertions.assertEquals(Set.of("10.20.0.9"), addresses(TaskRunner.children(id)));
	}

	@Test
	@DisplayName("Fails without SNMP community")
	void failsWithoutCommunity() throws Exception {
		long id = this.runner.run(this.scan("10.20.0.0", 30));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.FAILURE, task.status());
		Assertions.assertTrue(task.log().contains("No available SNMP community to scan devices."), task.log());
		Assertions.assertEquals(List.of(), TaskRunner.children(id));
	}
}
