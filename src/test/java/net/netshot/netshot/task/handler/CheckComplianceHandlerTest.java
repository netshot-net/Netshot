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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
import net.netshot.netshot.compliance.HardwareRule;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestCompliance;
import net.netshot.netshot.testing.TestDevices;

/**
 * Characterization of {@link CheckComplianceHandler}, run through the task engine: what the compliance
 * check of a device computes and stores, as of the current implementation.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Check compliance task (CheckComplianceHandler)")
class CheckComplianceHandlerTest {

	private TaskRunner runner;

	private Domain domain;

	private Device device;

	private StaticDeviceGroup group;

	@BeforeEach
	void createRunner() {
		this.runner = new TaskRunner();
	}

	@BeforeEach
	void createDevice() {
		this.domain = TestDevices.addDomain("Lab", "Test domain");
		this.device = TestDevices.addFakeCiscoIos(this.domain, 1);
		this.group = TestDevices.addStaticGroup("Routers");
		TestDevices.addMembers(this.group, this.device);
	}

	@AfterEach
	void cleanUp() {
		TaskRunner.cleanDatabase();
	}

	private static TextRule textRule(String name, Policy policy, String text) {
		return TestCompliance.rule(TextRule.builder(name, policy.id())
			.enabled(true)
			.deviceDriver("CiscoIOS12")
			.field("runningConfig")
			.text(text)
			.build());
	}

	private Map<String, ResultOption> results() {
		return new Transactions().read(dsl -> dsl.select(Tables.RULE.NAME, Tables.CHECK_RESULT.RESULT).from(Tables.CHECK_RESULT)
			.join(Tables.RULE).on(Tables.RULE.ID.eq(Tables.CHECK_RESULT.RULE))
			.where(Tables.CHECK_RESULT.DEVICE.eq(this.device.id()))
			.fetch()
			.stream()
			.collect(Collectors.toMap(r -> r.value1(), r -> ResultOption.valueOf(r.value2()))));
	}

	private Device reloadDevice() {
		return TestDevices.find(this.device.id());
	}

	private static Instant daysFromNow(int days) {
		return Instant.now().plus(days, ChronoUnit.DAYS);
	}

	@Test
	@DisplayName("Checks the policies applied to the groups of the device, replacing the previous results")
	void checksThePoliciesOfTheDevice() throws Exception {
		Policy policy = TestCompliance.policy("Applied policy", this.group.id());
		textRule("conforming", policy, "no service pad");
		textRule("non-conforming", policy, "no service mlqksd");
		StaticDeviceGroup otherGroup = TestDevices.addStaticGroup("Other group");
		Policy otherPolicy = TestCompliance.policy("Other policy", otherGroup.id());
		TextRule otherRule = textRule("other", otherPolicy, "no service pad");
		TestCompliance.checkResults(CheckResult.of(otherRule.id(), this.device.id(), ResultOption.NONCONFORMING, "stale"));

		long id = this.runner.run(CheckComplianceTask.of(this.device, "Test", "tester"));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(id).status());
		Assertions.assertEquals(Map.of("conforming", ResultOption.CONFORMING, "non-conforming", ResultOption.NONCONFORMING),
			this.results(), "Only the rules of the applied policies have results, the previous ones are deleted");
	}

	@Test
	@DisplayName("Sets the software level from the first matching software rule, by priority")
	void setsTheSoftwareLevel() throws Exception {
		TestCompliance.softwareRules(
			new SoftwareRule(0, 20, null, "CiscoIOS12", "", false, "16.1.6", false, "", false, ConformanceLevel.SILVER),
			new SoftwareRule(0, 10, null, "CiscoIOS12", "", false, "16.1.6", false, "", false, ConformanceLevel.BRONZE),
			new SoftwareRule(0, 5, null, "CiscoIOS12", "", false, "15.0", false, "", false, ConformanceLevel.GOLD));

		this.runner.run(CheckComplianceTask.of(this.device, "Test", "tester"));

		Assertions.assertEquals(ConformanceLevel.BRONZE, this.reloadDevice().softwareLevel());
	}

	@Test
	@DisplayName("Without matching software rule, the software level is unknown")
	void unknownSoftwareLevel() throws Exception {
		TestCompliance.softwareRules(new SoftwareRule(0, 10, null, "CiscoIOS12", "", false, "15.0", false, "", false, ConformanceLevel.GOLD));

		this.runner.run(CheckComplianceTask.of(this.device, "Test", "tester"));

		Assertions.assertEquals(ConformanceLevel.UNKNOWN, this.reloadDevice().softwareLevel());
	}

	@Test
	@DisplayName("Sets the end of sale and end of life from the hardware rules")
	void setsTheHardwareStatus() throws Exception {
		TestCompliance.hardwareRules(new HardwareRule(0, "CiscoIOS12", null, "", false, "TESTCHASSIS", false, daysFromNow(-10), daysFromNow(100)));

		this.runner.run(CheckComplianceTask.of(this.device, "Test", "tester"));

		Device checked = this.reloadDevice();
		Assertions.assertTrue(checked.isEndOfSale(), "End of sale (date in the past)");
		Assertions.assertFalse(checked.isEndOfLife(), "Not end of life (date in the future)");
	}

	@Test
	@DisplayName("Refreshes the dynamic groups of the device afterwards")
	void refreshesTheDynamicGroups() throws Exception {
		DynamicDeviceGroup bronze = TestDevices.addDynamicGroup("Bronze", "[Software Level] is BRONZE");
		TestCompliance.softwareRules(new SoftwareRule(0, 10, null, "CiscoIOS12", "", false, "16.1.6", false, "", false, ConformanceLevel.BRONZE));

		this.runner.run(CheckComplianceTask.of(this.device, "Test", "tester"));

		List<Long> members = TestDevices.memberIds(bronze.id());
		Assertions.assertEquals(List.of(this.device.id()), members);
	}

	@Test
	@DisplayName("Fails for a device without configuration")
	void failsWithoutConfiguration() throws Exception {
		Device bare = TestDevices.add(Device.create("CiscoIOS12", "10.9.9.9", this.domain, "tester"));

		long id = this.runner.run(CheckComplianceTask.of(bare, "Test", "tester"));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.FAILURE, task.status());
		Assertions.assertTrue(task.log().contains("No last config for this device"), task.log());
	}

	@Test
	@DisplayName("Is cancelled when the device was deleted")
	void cancelledWithoutDevice() throws Exception {
		Device bare = TestDevices.add(Device.create("CiscoIOS12", "10.9.9.9", this.domain, "tester"));
		long id = TaskRunner.save(CheckComplianceTask.of(bare, "Test", "tester"));
		TestDevices.delete(bare.id());

		this.runner.run(id);

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.CANCELLED, task.status());
		Assertions.assertTrue(task.log().contains("The device doesn't exist"), task.log());
	}
}
