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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.compliance.HardwareRule;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;
import net.netshot.netshot.task.type.GroupBasedTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestCompliance;
import net.netshot.netshot.testing.TestDevices;

/**
 * Characterization of {@link CheckGroupSoftwareHandler}, run through the task engine: the software
 * level and hardware support status of the devices of a group or list.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Check group software task (CheckGroupSoftwareHandler)")
class CheckGroupSoftwareHandlerTest {

	private TaskRunner runner;

	private Device inGroup;

	private Device outOfGroup;

	private StaticDeviceGroup group;

	@BeforeEach
	void createRunner() {
		this.runner = new TaskRunner();
	}

	@BeforeEach
	void createDevices() {
		Domain domain = TestDevices.addDomain("Lab", "Test domain");
		this.inGroup = TestDevices.addFakeCiscoIos(domain, 1);
		this.outOfGroup = TestDevices.addFakeCiscoIos(domain, 2);
		this.group = TestDevices.addStaticGroup("Routers");
		TestDevices.addMembers(this.group, this.inGroup);
		TestCompliance.softwareRules(
			new SoftwareRule(0, 20, null, "CiscoIOS12", "", false, "16.1.6", false, "", false, ConformanceLevel.SILVER),
			new SoftwareRule(0, 10, null, "CiscoIOS12", "", false, "16.1.6", false, "", false, ConformanceLevel.BRONZE));
		TestCompliance.hardwareRules(new HardwareRule(0, "CiscoIOS12", null, "", false, "TESTCHASSIS", false, daysFromNow(-10), daysFromNow(-5)));
	}

	@AfterEach
	void cleanUp() {
		TaskRunner.cleanDatabase();
	}

	private static Instant daysFromNow(int days) {
		return Instant.now().plus(days, ChronoUnit.DAYS);
	}

	private static Device reload(Device device) {
		return TestDevices.find(device.id());
	}

	@Test
	@DisplayName("Checks the devices of the group only")
	void checksTheDevicesOfTheGroup() throws Exception {
		long id = this.runner.run(GroupBasedTask.of(new CheckGroupSoftwareTask(this.group.id(), null), this.group.name(), "Test", "tester"));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(id).status());
		Device checked = reload(this.inGroup);
		Assertions.assertEquals(ConformanceLevel.BRONZE, checked.softwareLevel(), "First matching rule by priority");
		Assertions.assertTrue(checked.isEndOfSale());
		Assertions.assertTrue(checked.isEndOfLife());
		Device unchecked = reload(this.outOfGroup);
		Assertions.assertEquals(ConformanceLevel.GOLD, unchecked.softwareLevel(), "Device out of the group left as is");
		Assertions.assertFalse(unchecked.isEndOfSale());
	}

	@Test
	@DisplayName("Checks the devices of a list")
	void checksTheDevicesOfAList() throws Exception {
		this.runner.run(TaskRunner.forDevices(new CheckGroupSoftwareTask(null, TaskRunner.ids(this.outOfGroup)), "Test", "tester"));

		Assertions.assertEquals(ConformanceLevel.BRONZE, reload(this.outOfGroup).softwareLevel());
		Assertions.assertEquals(ConformanceLevel.GOLD, reload(this.inGroup).softwareLevel(), "Device out of the list left as is");
	}

	@Test
	@DisplayName("Is cancelled without group nor device")
	void cancelledWithoutTarget() throws Exception {
		long id = this.runner.run(TaskRunner.forDevices(new CheckGroupSoftwareTask(null, TaskRunner.ids()), "Test", "tester"));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.CANCELLED, task.status());
		Assertions.assertTrue(task.log().contains("Neither a device group nor a device list is set"), task.log());
	}
}
