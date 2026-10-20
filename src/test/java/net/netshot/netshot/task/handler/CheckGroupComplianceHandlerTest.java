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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.CheckResult.ResultOption;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.rules.TextRule;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * Characterization of {@link CheckGroupComplianceHandler}, run through the task engine: the compliance
 * check of the devices of a group or list.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Check group compliance task (CheckGroupComplianceHandler)")
class CheckGroupComplianceHandlerTest {

	private TaskRunner runner;

	private Device first;

	private Device second;

	private Device third;

	private StaticDeviceGroup group;

	@BeforeEach
	void createRunner() {
		this.runner = new TaskRunner();
	}

	/**
	 * Three devices, all in the group the policy applies to; the first two are in the target group of
	 * the tasks; the third one has a stale result.
	 */
	@BeforeEach
	void createData() {
		Domain domain = new Domain("Lab", "Test domain", null, null);
		this.first = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1);
		this.second = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 2);
		this.third = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 3);
		StaticDeviceGroup policyGroup = new StaticDeviceGroup("Policy target");
		policyGroup.addDevice(this.first);
		policyGroup.addDevice(this.third);
		this.group = new StaticDeviceGroup("Task target");
		this.group.addDevice(this.first);
		this.group.addDevice(this.second);
		Policy policy = new Policy("Policy", Set.of(policyGroup));
		TextRule rule = new TextRule("pad", policy);
		rule.setEnabled(true);
		rule.setDeviceDriver("CiscoIOS12");
		rule.setField("runningConfig");
		rule.setText("no service pad");
		TaskRunner.persist(domain, this.first, this.second, this.third, policyGroup, this.group, policy, rule,
			new CheckResult(rule, this.second, ResultOption.NONCONFORMING, "stale"),
			new CheckResult(rule, this.third, ResultOption.NONCONFORMING, "stale"));
	}

	@AfterEach
	void cleanUp() {
		TaskRunner.cleanDatabase();
	}

	private static Map<String, ResultOption> results() {
		return TaskRunner.inSession(session -> session
			.createQuery("select c from CheckResult c join fetch c.key.device", CheckResult.class)
			.list()
			.stream()
			.collect(Collectors.toMap(c -> c.getDevice().getName(), CheckResult::getResult)));
	}

	@Test
	@DisplayName("Checks the devices of the group which a policy applies to, replacing their results")
	void checksTheGroup() throws Exception {
		long id = this.runner.run(new CheckGroupComplianceTask(this.group, "Test", "tester"));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(CheckGroupComplianceTask.class, id).getStatus());
		Assertions.assertEquals(Map.of(
			this.first.getName(), ResultOption.CONFORMING,
			this.third.getName(), ResultOption.NONCONFORMING), results(),
			"First: checked; second (no policy): stale result deleted; third (out of the group): stale result kept");
	}

	@Test
	@DisplayName("Checks the devices of a list")
	void checksAList() throws Exception {
		this.runner.run(new CheckGroupComplianceTask(List.of(this.third), "Test", "tester"));

		Assertions.assertEquals(Map.of(
			this.second.getName(), ResultOption.NONCONFORMING,
			this.third.getName(), ResultOption.CONFORMING), results());
	}

	@Test
	@DisplayName("Refreshes all the dynamic groups afterwards")
	void refreshesAllGroups() throws Exception {
		DynamicDeviceGroup dynamic = new DynamicDeviceGroup("All routers", null, "[Name] startswith \"router\"");
		TaskRunner.persist(dynamic);

		this.runner.run(new CheckGroupComplianceTask(this.group, "Test", "tester"));

		long members = TaskRunner.inSession(session -> session
			.createQuery("select count(m) from DeviceGroupMembership m where m.key.group.id = :id", Long.class)
			.setParameter("id", dynamic.getId())
			.uniqueResult());
		Assertions.assertEquals(3, members, "Every group is refreshed, not only those of the checked devices");
	}

	@Test
	@DisplayName("Is cancelled without group nor device")
	void cancelledWithoutTarget() throws Exception {
		long id = this.runner.run(new CheckGroupComplianceTask(List.of(), "Test", "tester"));

		CheckGroupComplianceTask task = TaskRunner.load(CheckGroupComplianceTask.class, id);
		Assertions.assertEquals(Status.CANCELLED, task.getStatus());
		Assertions.assertTrue(task.getLog().contains("Neither a device group nor a device list is set"), task.getLog());
	}
}
