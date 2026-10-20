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

import java.util.Calendar;
import java.util.Date;
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
import net.netshot.netshot.compliance.HardwareRule;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.compliance.rules.TextRule;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

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
		this.domain = new Domain("Lab", "Test domain", null, null);
		this.device = FakeDeviceFactory.getFakeCiscoIosDevice(this.domain, null, 1);
		this.group = new StaticDeviceGroup("Routers");
		this.group.addDevice(this.device);
		TaskRunner.persist(this.domain, this.device, this.group);
	}

	@AfterEach
	void cleanUp() {
		TaskRunner.cleanDatabase();
	}

	private static TextRule textRule(String name, Policy policy, String text) {
		TextRule rule = new TextRule(name, policy);
		rule.setEnabled(true);
		rule.setDeviceDriver("CiscoIOS12");
		rule.setField("runningConfig");
		rule.setText(text);
		return rule;
	}

	private Map<String, ResultOption> results() {
		return TaskRunner.inSession(session -> session
			.createQuery("select c from CheckResult c join fetch c.key.rule where c.key.device.id = :id", CheckResult.class)
			.setParameter("id", this.device.getId())
			.list()
			.stream()
			.collect(Collectors.toMap(c -> c.getRule().getName(), CheckResult::getResult)));
	}

	private Device reloadDevice() {
		return TaskRunner.inSession(session -> session.get(Device.class, this.device.getId()));
	}

	private static Date daysFromNow(int days) {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DATE, days);
		return calendar.getTime();
	}

	@Test
	@DisplayName("Checks the policies applied to the groups of the device, replacing the previous results")
	void checksThePoliciesOfTheDevice() throws Exception {
		Policy policy = new Policy("Applied policy", Set.of(this.group));
		TextRule conforming = textRule("conforming", policy, "no service pad");
		TextRule nonConforming = textRule("non-conforming", policy, "no service mlqksd");
		StaticDeviceGroup otherGroup = new StaticDeviceGroup("Other group");
		Policy otherPolicy = new Policy("Other policy", Set.of(otherGroup));
		TextRule otherRule = textRule("other", otherPolicy, "no service pad");
		TaskRunner.persist(policy, conforming, nonConforming, otherGroup, otherPolicy, otherRule,
			new CheckResult(otherRule, this.device, ResultOption.NONCONFORMING, "stale"));

		long id = this.runner.run(new CheckComplianceTask(this.device, "Test", "tester"));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(CheckComplianceTask.class, id).getStatus());
		Assertions.assertEquals(Map.of("conforming", ResultOption.CONFORMING, "non-conforming", ResultOption.NONCONFORMING),
			this.results(), "Only the rules of the applied policies have results, the previous ones are deleted");
	}

	@Test
	@DisplayName("Sets the software level from the first matching software rule, by priority")
	void setsTheSoftwareLevel() throws Exception {
		TaskRunner.persist(
			new SoftwareRule(20, null, "CiscoIOS12", "", false, "16.1.6", false, "", false, ConformanceLevel.SILVER),
			new SoftwareRule(10, null, "CiscoIOS12", "", false, "16.1.6", false, "", false, ConformanceLevel.BRONZE),
			new SoftwareRule(5, null, "CiscoIOS12", "", false, "15.0", false, "", false, ConformanceLevel.GOLD));

		this.runner.run(new CheckComplianceTask(this.device, "Test", "tester"));

		Assertions.assertEquals(ConformanceLevel.BRONZE, this.reloadDevice().getSoftwareLevel());
	}

	@Test
	@DisplayName("Without matching software rule, the software level is unknown")
	void unknownSoftwareLevel() throws Exception {
		TaskRunner.persist(new SoftwareRule(10, null, "CiscoIOS12", "", false, "15.0", false, "", false, ConformanceLevel.GOLD));

		this.runner.run(new CheckComplianceTask(this.device, "Test", "tester"));

		Assertions.assertEquals(ConformanceLevel.UNKNOWN, this.reloadDevice().getSoftwareLevel());
	}

	@Test
	@DisplayName("Sets the end of sale and end of life from the hardware rules")
	void setsTheHardwareStatus() throws Exception {
		TaskRunner.persist(new HardwareRule("CiscoIOS12", null, "", false, "TESTCHASSIS", false, daysFromNow(-10), daysFromNow(100)));

		this.runner.run(new CheckComplianceTask(this.device, "Test", "tester"));

		Device checked = this.reloadDevice();
		Assertions.assertTrue(checked.isEndOfSale(), "End of sale (date in the past)");
		Assertions.assertFalse(checked.isEndOfLife(), "Not end of life (date in the future)");
	}

	@Test
	@DisplayName("Refreshes the dynamic groups of the device afterwards")
	void refreshesTheDynamicGroups() throws Exception {
		DynamicDeviceGroup bronze = new DynamicDeviceGroup("Bronze", null, "[Software Level] is BRONZE");
		TaskRunner.persist(bronze,
			new SoftwareRule(10, null, "CiscoIOS12", "", false, "16.1.6", false, "", false, ConformanceLevel.BRONZE));

		this.runner.run(new CheckComplianceTask(this.device, "Test", "tester"));

		List<Long> members = TaskRunner.inSession(session -> session
			.createQuery("select m.key.device.id from DeviceGroupMembership m where m.key.group.id = :id", Long.class)
			.setParameter("id", bronze.getId())
			.list());
		Assertions.assertEquals(List.of(this.device.getId()), members);
	}

	@Test
	@DisplayName("Fails for a device without configuration")
	void failsWithoutConfiguration() throws Exception {
		Device bare = new Device("CiscoIOS12", "10.9.9.9", this.domain, "tester");
		TaskRunner.persist(bare);

		long id = this.runner.run(new CheckComplianceTask(bare, "Test", "tester"));

		CheckComplianceTask task = TaskRunner.load(CheckComplianceTask.class, id);
		Assertions.assertEquals(Status.FAILURE, task.getStatus());
		Assertions.assertTrue(task.getLog().contains("No last config for this device"), task.getLog());
	}

	@Test
	@DisplayName("Is cancelled when the device was deleted")
	void cancelledWithoutDevice() throws Exception {
		Device bare = new Device("CiscoIOS12", "10.9.9.9", this.domain, "tester");
		TaskRunner.persist(bare);
		long id = TaskRunner.save(new CheckComplianceTask(bare, "Test", "tester"));
		TaskRunner.inSession(session -> {
			session.beginTransaction();
			session.remove(session.get(Device.class, bare.getId()));
			session.getTransaction().commit();
			return null;
		});

		this.runner.run(id);

		CheckComplianceTask task = TaskRunner.load(CheckComplianceTask.class, id);
		Assertions.assertEquals(Status.CANCELLED, task.getStatus());
		Assertions.assertTrue(task.getLog().contains("The device doesn't exist"), task.getLog());
	}
}
