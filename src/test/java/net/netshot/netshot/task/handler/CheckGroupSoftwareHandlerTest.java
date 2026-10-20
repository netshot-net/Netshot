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
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

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
		Domain domain = new Domain("Lab", "Test domain", null, null);
		this.inGroup = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1);
		this.outOfGroup = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 2);
		this.group = new StaticDeviceGroup("Routers");
		this.group.addDevice(this.inGroup);
		TaskRunner.persist(domain, this.inGroup, this.outOfGroup, this.group,
			new SoftwareRule(20, null, "CiscoIOS12", "", false, "16.1.6", false, "", false, ConformanceLevel.SILVER),
			new SoftwareRule(10, null, "CiscoIOS12", "", false, "16.1.6", false, "", false, ConformanceLevel.BRONZE),
			new HardwareRule("CiscoIOS12", null, "", false, "TESTCHASSIS", false, daysFromNow(-10), daysFromNow(-5)));
	}

	@AfterEach
	void cleanUp() {
		TaskRunner.cleanDatabase();
	}

	private static Date daysFromNow(int days) {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DATE, days);
		return calendar.getTime();
	}

	private static Device reload(Device device) {
		return TaskRunner.inSession(session -> session.get(Device.class, device.getId()));
	}

	@Test
	@DisplayName("Checks the devices of the group only")
	void checksTheDevicesOfTheGroup() throws Exception {
		long id = this.runner.run(new CheckGroupSoftwareTask(this.group, "Test", "tester"));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(CheckGroupSoftwareTask.class, id).getStatus());
		Device checked = reload(this.inGroup);
		Assertions.assertEquals(ConformanceLevel.BRONZE, checked.getSoftwareLevel(), "First matching rule by priority");
		Assertions.assertTrue(checked.isEndOfSale());
		Assertions.assertTrue(checked.isEndOfLife());
		Device unchecked = reload(this.outOfGroup);
		Assertions.assertEquals(ConformanceLevel.GOLD, unchecked.getSoftwareLevel(), "Device out of the group left as is");
		Assertions.assertFalse(unchecked.isEndOfSale());
	}

	@Test
	@DisplayName("Checks the devices of a list")
	void checksTheDevicesOfAList() throws Exception {
		this.runner.run(new CheckGroupSoftwareTask(List.of(this.outOfGroup), "Test", "tester"));

		Assertions.assertEquals(ConformanceLevel.BRONZE, reload(this.outOfGroup).getSoftwareLevel());
		Assertions.assertEquals(ConformanceLevel.GOLD, reload(this.inGroup).getSoftwareLevel(), "Device out of the list left as is");
	}

	@Test
	@DisplayName("Is cancelled without group nor device")
	void cancelledWithoutTarget() throws Exception {
		long id = this.runner.run(new CheckGroupSoftwareTask(List.of(), "Test", "tester"));

		CheckGroupSoftwareTask task = TaskRunner.load(CheckGroupSoftwareTask.class, id);
		Assertions.assertEquals(Status.CANCELLED, task.getStatus());
		Assertions.assertTrue(task.getLog().contains("Neither a device group nor a device list is set"), task.getLog());
	}
}
