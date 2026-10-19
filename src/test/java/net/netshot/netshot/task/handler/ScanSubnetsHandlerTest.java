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

import net.netshot.netshot.credentials.DeviceCredentialSet;
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
		this.domain = new Domain("Lab", "Test domain", null, null);
		TaskRunner.persist(this.domain);
	}

	@AfterEach
	void cleanUp() {
		TaskRunner.cleanDatabase();
	}

	private ScanSubnetsTask scan(String address, int prefixLength) throws Exception {
		return new ScanSubnetsTask(Set.of(new Network4Address(address, prefixLength)), this.domain, "Scan", address, "tester");
	}

	private DeviceSnmpv2cCommunity community(String name, Domain mgmtDomain) {
		DeviceSnmpv2cCommunity community = new DeviceSnmpv2cCommunity("public", name);
		community.setMgmtDomain(mgmtDomain);
		return community;
	}

	private static Set<String> addresses(List<Task> children) {
		return children.stream().map(child -> ((DiscoverDeviceTypeTask) child).getDeviceAddress()).collect(Collectors.toSet());
	}

	@Test
	@DisplayName("Creates a discovery task per address of the subnet without device, with the usable SNMP communities")
	void discoversTheSubnet() throws Exception {
		Domain otherDomain = new Domain("Other", "Other domain", null, null);
		TaskRunner.persist(otherDomain,
			new Device("CiscoIOS12", "10.20.0.3", this.domain, "tester"),
			this.community("global", null), this.community("same domain", this.domain), this.community("other domain", otherDomain),
			new DeviceSshAccount("admin", "admin", "admin", "ssh"));

		long id = this.runner.run(this.scan("10.20.0.0", 29));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(ScanSubnetsTask.class, id).getStatus());
		List<Task> children = TaskRunner.children(id);
		Assertions.assertEquals(Set.of("10.20.0.1", "10.20.0.2", "10.20.0.4", "10.20.0.5", "10.20.0.6"), addresses(children),
			"Neither the network and broadcast addresses, nor the address of an existing device");
		Assertions.assertEquals(children.size(), this.runner.scheduledChildIds().size());
		TaskRunner.inSession(session -> {
			for (Task child : children) {
				DiscoverDeviceTypeTask discovery = session.get(DiscoverDeviceTypeTask.class, child.getId());
				Assertions.assertEquals(Set.of("global", "same domain"),
					discovery.getCredentialSets().stream().map(DeviceCredentialSet::getName).collect(Collectors.toSet()));
				Assertions.assertEquals(this.domain.getId(), discovery.getDomain().getId());
				Assertions.assertEquals("Scan", discovery.getComments());
				Assertions.assertEquals("tester", discovery.getAuthor());
			}
			return null;
		});
	}

	@Test
	@DisplayName("Scans the single address of a /32")
	void scansASingleAddress() throws Exception {
		TaskRunner.persist(this.community("global", null));

		long id = this.runner.run(this.scan("10.20.0.9", 32));

		Assertions.assertEquals(Set.of("10.20.0.9"), addresses(TaskRunner.children(id)));
	}

	@Test
	@DisplayName("Fails without SNMP community")
	void failsWithoutCommunity() throws Exception {
		long id = this.runner.run(this.scan("10.20.0.0", 30));

		ScanSubnetsTask task = TaskRunner.load(ScanSubnetsTask.class, id);
		Assertions.assertEquals(Status.FAILURE, task.getStatus());
		Assertions.assertTrue(task.getLog().contains("No available SNMP community to scan devices."), task.getLog());
		Assertions.assertEquals(List.of(), TaskRunner.children(id));
	}
}
