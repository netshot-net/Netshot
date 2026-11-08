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

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.compliance.JavaScriptRule;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.inventory.compliance.CheckResult.ResultOption;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;
import net.netshot.netshot.task.type.GroupBasedTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestCompliance;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestWiring;

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

	private StaticDeviceGroup policyGroup;

	private Policy policy;

	private TextRule rule;

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
		Domain domain = TestDevices.addDomain("Lab", "Test domain");
		this.first = TestDevices.addFakeCiscoIos(domain, 1);
		this.second = TestDevices.addFakeCiscoIos(domain, 2);
		this.third = TestDevices.addFakeCiscoIos(domain, 3);
		this.policyGroup = TestDevices.addStaticGroup("Policy target");
		TestDevices.addMembers(this.policyGroup, this.first);
		TestDevices.addMembers(this.policyGroup, this.third);
		this.group = TestDevices.addStaticGroup("Task target");
		TestDevices.addMembers(this.group, this.first);
		TestDevices.addMembers(this.group, this.second);
		this.policy = TestCompliance.policy("Policy", this.policyGroup.id());
		this.rule = TestCompliance.rule(TextRule.builder("pad", this.policy.id())
			.enabled(true)
			.deviceDriver("CiscoIOS12")
			.field("runningConfig")
			.text("no service pad")
			.build());
		TestCompliance.checkResults(
			CheckResult.of(this.rule.id(), this.second.id(), ResultOption.NONCONFORMING, "stale"),
			CheckResult.of(this.rule.id(), this.third.id(), ResultOption.NONCONFORMING, "stale"));
	}

	@AfterEach
	void cleanUp() {
		TaskRunner.cleanDatabase();
	}

	private static Map<String, ResultOption> results() {
		return TestWiring.get().transactions().read(dsl -> dsl.select(Tables.DEVICE.NAME, Tables.CHECK_RESULT.RESULT).from(Tables.CHECK_RESULT)
			.join(Tables.DEVICE).on(Tables.DEVICE.ID.eq(Tables.CHECK_RESULT.DEVICE))
			.fetch()
			.stream()
			.collect(Collectors.toMap(r -> r.value1(), r -> ResultOption.valueOf(r.value2()))));
	}

	@Test
	@DisplayName("Checks the devices of the group which a policy applies to, replacing their results")
	void checksTheGroup() throws Exception {
		long id = this.runner.run(GroupBasedTask.of(new CheckGroupComplianceTask(this.group.id(), null), this.group.name(), "Test", "tester"));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(id).status());
		Assertions.assertEquals(Map.of(
			this.first.name(), ResultOption.CONFORMING,
			this.third.name(), ResultOption.NONCONFORMING), results(),
			"First: checked; second (no policy): stale result deleted; third (out of the group): stale result kept");
	}

	@Test
	@DisplayName("Checks the devices of a list")
	void checksAList() throws Exception {
		this.runner.run(TaskRunner.forDevices(new CheckGroupComplianceTask(null, TaskRunner.ids(this.third)), "Test", "tester"));

		Assertions.assertEquals(Map.of(
			this.second.name(), ResultOption.NONCONFORMING,
			this.third.name(), ResultOption.CONFORMING), results());
	}

	@Test
	@DisplayName("Refreshes all the dynamic groups afterwards")
	void refreshesAllGroups() throws Exception {
		DynamicDeviceGroup dynamic = TestDevices.addDynamicGroup("All routers", "[Name] startswith \"router\"");

		this.runner.run(GroupBasedTask.of(new CheckGroupComplianceTask(this.group.id(), null), this.group.name(), "Test", "tester"));

		long members = TestDevices.memberIds(dynamic.id()).size();
		Assertions.assertEquals(3, members, "Every group is refreshed, not only those of the checked devices");
	}

	private static Map<String, String> ruleResults(long ruleId) {
		return TestWiring.get().transactions().read(dsl -> dsl.select(Tables.DEVICE.NAME, Tables.CHECK_RESULT.RESULT).from(Tables.CHECK_RESULT)
			.join(Tables.DEVICE).on(Tables.DEVICE.ID.eq(Tables.CHECK_RESULT.DEVICE))
			.where(Tables.CHECK_RESULT.RULE.eq(ruleId))
			.fetchMap(Tables.DEVICE.NAME, Tables.CHECK_RESULT.RESULT));
	}

	private void addCommentsRule(String comments) {
		TestCompliance.rule(new JavaScriptRule(0, 0, null, "Comments", true, this.policy.id(),
			"function check(device) {\n  device.set(\"comments\", " + comments + ");\n  return CONFORMING;\n}"));
	}

	@Test
	@DisplayName("Checks each device in its own transaction: an error on a device keeps the results of the others")
	void deviceErrorKeepsOtherResults() throws Exception {
		// Both devices of the task group in the policy, a rule changing them; the update of the first one fails
		TestDevices.addMembers(this.policyGroup, this.second);
		this.addCommentsRule("\"Checked\"");
		Transactions transactions = TestWiring.get().transactions();
		transactions.run(dsl -> {
			dsl.execute("create function test_fail_device_update() returns trigger language plpgsql as "
				+ "$$ begin raise exception 'Test failure of the update of device %', old.name; end $$");
			dsl.execute("create trigger test_fail_device_update before update on device for each row when (old.name = '"
				+ this.first.name() + "') execute function test_fail_device_update()");
		});
		try {
			long id = this.runner.run(GroupBasedTask.of(new CheckGroupComplianceTask(this.group.id(), null), this.group.name(), "Test", "tester"));

			Task task = TaskRunner.load(id);
			Assertions.assertEquals(Status.FAILURE, task.status());
			Assertions.assertTrue(task.log().contains("failed for 1 of 2 device(s): [" + this.first.id() + "]"), task.log());
			Assertions.assertEquals(Map.of(
				this.second.name(), ResultOption.CONFORMING.name(),
				this.third.name(), ResultOption.NONCONFORMING.name()), ruleResults(this.rule.id()),
				"First: its check rolled back; second: checked after the error; third (out of the group): untouched");
		}
		finally {
			transactions.run(dsl -> {
				dsl.execute("drop trigger test_fail_device_update on device");
				dsl.execute("drop function test_fail_device_update()");
			});
		}
	}

	@Test
	@DisplayName("Saves the device comments set by a rule, up to 4096 characters")
	void longComments() throws Exception {
		this.addCommentsRule("\"x\".repeat(4096)");

		long id = this.runner.run(GroupBasedTask.of(new CheckGroupComplianceTask(this.group.id(), null), this.group.name(), "Test", "tester"));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(id).status());
		String comments = TestWiring.get().transactions().read(dsl -> dsl.select(Tables.DEVICE.COMMENTS).from(Tables.DEVICE)
			.where(Tables.DEVICE.ID.eq(this.first.id())).fetchOne(Tables.DEVICE.COMMENTS));
		Assertions.assertEquals(4096, comments.length());
	}

	@Test
	@DisplayName("Is cancelled without group nor device")
	void cancelledWithoutTarget() throws Exception {
		long id = this.runner.run(TaskRunner.forDevices(new CheckGroupComplianceTask(null, TaskRunner.ids()), "Test", "tester"));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.CANCELLED, task.status());
		Assertions.assertTrue(task.log().contains("Neither a device group nor a device list is set"), task.log());
	}
}
