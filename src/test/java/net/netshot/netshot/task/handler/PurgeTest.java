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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

@DatabaseTest
public class PurgeTest {
	static final int DEVICES = 10;
	static final int CONFIGS_PER_DAY = 4;
	static final int DAYS = 30;
	static final String GROUP1_NAME = "group1";
	static final int DEVICES_IN_GROUP1 = 3;
	static final int DEVICES_WITH_BIG_CONFIG = 2;

	@BeforeAll
	protected static void initNetshot() throws Exception {
		Thread.sleep(1000);
	}

	private StaticDeviceGroup group1;

	@BeforeEach
	void createData() throws Exception {
		final Domain domain = TestDevices.addDomain("Test domain", "Fake domain for tests");
		group1 = TestDevices.addStaticGroup(GROUP1_NAME);
		// Let's create devices with configurations every n hours
		Calendar now = Calendar.getInstance();
		for (int i = 0; i < DEVICES; i++) {
			Device fake = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, i);
			// Add old modules
			List<Module> modules = new ArrayList<>(fake.modules());
			{
				Calendar firstSeenDate = (Calendar) now.clone();
				firstSeenDate.add(Calendar.DATE, -300);
				Calendar lastSeenDate = (Calendar) now.clone();
				lastSeenDate.add(Calendar.DATE, -25);
				modules.add(Module.builder().slot("chassis").partNumber("OLDPART1").serialNumber("9999999TEST99").removed(true)
					.firstSeenDate(firstSeenDate.toInstant()).lastSeenDate(lastSeenDate.toInstant()).build());
			}
			{
				Calendar firstSeenDate = (Calendar) now.clone();
				firstSeenDate.add(Calendar.DATE, -400);
				Calendar lastSeenDate = (Calendar) now.clone();
				lastSeenDate.add(Calendar.DATE, -5);
				modules.add(Module.builder().slot("chassis").partNumber("OLDPART2").serialNumber("9999999TEST09").removed(true)
					.firstSeenDate(firstSeenDate.toInstant()).lastSeenDate(lastSeenDate.toInstant()).build());
			}
			Device device = TestDevices.add(fake.toBuilder().modules(modules).build());
			Long lastConfigId = null;
			for (int h = 0; h < CONFIGS_PER_DAY * DAYS; h++) {
				Calendar configDate = (Calendar) now.clone();
				configDate.add(Calendar.HOUR_OF_DAY, -(24 / CONFIGS_PER_DAY) * h);
				// New configuration
				Config config = FakeDeviceFactory.getFakeCiscoIosConfig(device);
				if (i >= DEVICES - DEVICES_WITH_BIG_CONFIG) {
					// For last devices, increase the size of the config
					List<ConfigAttribute> attributes = new ArrayList<>(config.attributes());
					attributes.replaceAll(a -> a instanceof ConfigLongTextAttribute runningConfig
						? new ConfigLongTextAttribute(runningConfig.name(), runningConfig.text() + "\n" + runningConfig.text() + "\n"
							+ runningConfig.text() + "\n" + runningConfig.text())
						: a);
					config = config.toBuilder().attributes(attributes).build();
				}
				long configId = TestDevices.addOldConfig(device.id(), config.toBuilder().changeDate(configDate.toInstant()).build());
				if (h == 0) {
					lastConfigId = configId;
				}
				// Snapshot task
				TestTasks.add(TakeSnapshotTask.of(device, "Test", "faker", false, true, true).toBuilder()
					.creationDate(configDate.toInstant().minusSeconds(20))
					.executionDate(configDate.toInstant().minusSeconds(2))
					.changeDate(configDate.toInstant().minusSeconds(2))
					.status(Task.Status.SUCCESS)
					.build());
			}
			TestDevices.save(TestDevices.find(device.id()).toBuilder().lastConfigId(lastConfigId).build());
			if (i < DEVICES_IN_GROUP1) {
				// Add the two first devices only to the group
				TestDevices.addMembers(group1, device);
			}
		}
	}

	@AfterEach
	void cleanUpData() {
		TaskRunner.cleanDatabase();
	}

	private static long count(String sql) {
		return TestWiring.get().transactions().read(dsl -> ((Number) dsl.fetchValue(sql)).longValue());
	}

	private void assertDeviceCount(long expected) {
		Assertions.assertEquals(
			expected,
			count("select count(1) from device d"),
			"Unexpected number of device entries");
	}

	private void assertConfigCount(long expected) {
		Assertions.assertEquals(
			expected,
			count("select count(1) from config c"),
			"Unexpected number of config entries");
		Assertions.assertEquals(
			expected * 3, // three attributes per config are created by FakeDeviceFactory
			count("select count(1) from config_attribute ca"),
			"Unexpected number of config attribute entries");
		Assertions.assertEquals(
			expected * 1, // one attribute is long text configuration
			count("select count(1) from long_text_configuration ltc"),
			"Unexpected number of long text configuration entries");
	}

	private void assertTaskCount(long expected) {
		Assertions.assertEquals(
			expected,
			count("select count(1) from task t"),
			"Unexpected number of task entries");
		Assertions.assertEquals(
			expected,
			count("select count(1) from task t where t.dtype = 'TakeSnapshotTask'"),
			"Unexpected number of snapshot task entries");
	}

	private void assertModuleCount(long expected) {
		Assertions.assertEquals(
			expected,
			count("select count(1) from Module m"),
			"Unexpected number of module entries");
	}

	@Test
	@DisplayName("Initial count test")
	@ResourceLock("DB")
	public void initialCountTest() {
		// Check number of items before purge task
		this.assertDeviceCount(DEVICES);
		this.assertConfigCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertTaskCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertModuleCount(DEVICES * 4);
		Assertions.assertEquals(DEVICES_IN_GROUP1, TestDevices.memberIds(group1.id()).size(),
			"The number of devices in group1 is not correct");
	}

	@Test
	@DisplayName("Task purge test")
	@ResourceLock("DB")
	public void taskPurgeTest() {
		final int purgeDays = 18;
		this.purge(purgeDays, 0, 0, 0, 0, null, null);

		this.assertDeviceCount(DEVICES);
		this.assertConfigCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertTaskCount(DEVICES * CONFIGS_PER_DAY * purgeDays);
		this.assertModuleCount(DEVICES * 4);
	}

	@Test
	@DisplayName("Per-group task purge test")
	@ResourceLock("DB")
	public void perGroupTaskPurgeTest() {
		final int purgeDays = 18;
		this.purge(purgeDays, 0, 0, 0, 0, group1.id(), null);

		this.assertDeviceCount(DEVICES);
		this.assertConfigCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertTaskCount(
			(DEVICES - DEVICES_IN_GROUP1) * CONFIGS_PER_DAY * DAYS
				+ DEVICES_IN_GROUP1 * CONFIGS_PER_DAY * purgeDays);
		this.assertModuleCount(DEVICES * 4);
	}

	@Test
	@DisplayName("Config purge test")
	@ResourceLock("DB")
	public void configPurgeTest() {
		final int purgeDays = 18;
		this.purge(0, purgeDays, 0, 0, 0, null, null);

		this.assertDeviceCount(DEVICES);
		this.assertConfigCount(DEVICES * CONFIGS_PER_DAY * purgeDays);
		this.assertTaskCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertModuleCount(DEVICES * 4);
	}

	@Test
	@DisplayName("Per-group config purge test")
	@ResourceLock("DB")
	public void perGroupConfigPurgeTest() {
		final int purgeDays = 18;
		this.purge(0, purgeDays, 0, 0, 0, group1.id(), null);

		this.assertDeviceCount(DEVICES);
		this.assertConfigCount(
			(DEVICES - DEVICES_IN_GROUP1) * CONFIGS_PER_DAY * DAYS
				+ DEVICES_IN_GROUP1 * CONFIGS_PER_DAY * purgeDays);
		this.assertTaskCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertModuleCount(DEVICES * 4);
	}

	@Test
	@DisplayName("Oversized config purge test")
	@ResourceLock("DB")
	public void oversizedConfigPurgeTest() {
		final int purgeDays = 18;
		this.purge(0, purgeDays, 2, 0, 0, null, null);

		this.assertDeviceCount(DEVICES);
		// Only device #1 has configs bigger than 2KB
		this.assertConfigCount(
			(DEVICES - DEVICES_WITH_BIG_CONFIG) * CONFIGS_PER_DAY * DAYS
				+ DEVICES_WITH_BIG_CONFIG * CONFIGS_PER_DAY * purgeDays);
		this.assertTaskCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertModuleCount(DEVICES * 4);
	}

	@Test
	@DisplayName("Oversized config purge test, on the size of a binary file")
	@ResourceLock("DB")
	public void oversizedBinaryFileConfigPurgeTest() {
		// A device with small configurations gets an old one with a big binary file
		long deviceId = this.group1Devices().get(0);
		Config config = FakeDeviceFactory.getFakeCiscoIosConfig(TestDevices.find(deviceId));
		List<ConfigAttribute> attributes = new ArrayList<>(config.attributes());
		attributes.add(new ConfigBinaryFileAttribute("backupFile", "00000000-0000-0000-0000-000000000001", "backup.tar.gz",
			5 * 1024 * 1024, null));
		Calendar configDate = Calendar.getInstance();
		configDate.add(Calendar.DATE, -(DAYS + 1));
		long bigConfigId = TestDevices.addOldConfig(deviceId,
			config.toBuilder().attributes(attributes).changeDate(configDate.toInstant()).build());

		final int purgeDays = 18;
		this.purge(0, purgeDays, 2, 0, 0, null, null);

		Assertions.assertEquals(0, count("select count(1) from config c where c.id = " + bigConfigId),
			"The configuration with a binary file bigger than the size should be purged");
		Assertions.assertEquals(
			(DEVICES - DEVICES_WITH_BIG_CONFIG) * CONFIGS_PER_DAY * DAYS
				+ DEVICES_WITH_BIG_CONFIG * CONFIGS_PER_DAY * purgeDays,
			count("select count(1) from config c"),
			"Unexpected number of config entries");
	}

	@Test
	@DisplayName("Sparse config purge test")
	@ResourceLock("DB")
	public void sparseConfigPurgeTest() {
		final int purgeDays = 18;
		final int keepDays = 2; // Keep one config every two days
		this.purge(0, purgeDays, 0, keepDays, 0, null, null);

		this.assertDeviceCount(DEVICES);
		// Only device #1 has configs bigger than 2KB
		this.assertConfigCount(
			DEVICES * CONFIGS_PER_DAY * purgeDays     // All configs during purgeDays
				+ DEVICES * (DAYS - purgeDays) / keepDays);  // One config per device every two days for the rest of time
		this.assertTaskCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertModuleCount(DEVICES * 4);
	}

	@Test
	@DisplayName("Module purge test")
	@ResourceLock("DB")
	public void modulePurgeTest() {
		final int purgeDays = 18;
		this.purge(0, 0, 0, 0, purgeDays, null, null);

		this.assertDeviceCount(DEVICES);
		this.assertConfigCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertTaskCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertModuleCount(DEVICES * 3);
	}

	@Test
	@DisplayName("Per-group module purge test")
	@ResourceLock("DB")
	public void perGroupModulePurgeTest() {
		final int purgeDays = 18;
		this.purge(0, 0, 0, 0, purgeDays, group1.id(), null);

		this.assertDeviceCount(DEVICES);
		this.assertConfigCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertTaskCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertModuleCount(
			(DEVICES - DEVICES_IN_GROUP1) * 4
				+ DEVICES_IN_GROUP1 * 3);
	}

	/**
	 * Gets the devices of group1, as the device list of a task.
	 *
	 * @return the devices
	 */
	private List<Long> group1Devices() {
		return TestDevices.memberIds(group1.id());
	}

	// Runs a purge task
	private void purge(int days, int configDays, int configSize, int configKeepDays, int moduleDays, Long groupId, List<Long> deviceIds) {
		PurgeDatabaseTask spec = new PurgeDatabaseTask(groupId, deviceIds, days, configDays, configSize, configKeepDays, moduleDays, false);
		Task task = Task.of(spec, "Test", "Global", "tester");
		new PurgeDatabaseHandler(TestWiring.get().transactions()).run(task, spec, new TaskRun(task));
	}

	@Test
	@DisplayName("Per-device-list task purge test")
	@ResourceLock("DB")
	public void perDeviceListTaskPurgeTest() {
		final int purgeDays = 18;
		this.purge(purgeDays, 0, 0, 0, 0, null, this.group1Devices());

		this.assertConfigCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertTaskCount(
			(DEVICES - DEVICES_IN_GROUP1) * CONFIGS_PER_DAY * DAYS
				+ DEVICES_IN_GROUP1 * CONFIGS_PER_DAY * purgeDays);
		this.assertModuleCount(DEVICES * 4);
	}

	@Test
	@DisplayName("Per-device-list config purge test")
	@ResourceLock("DB")
	public void perDeviceListConfigPurgeTest() {
		final int purgeDays = 18;
		this.purge(0, purgeDays, 0, 0, 0, null, this.group1Devices());

		this.assertConfigCount(
			(DEVICES - DEVICES_IN_GROUP1) * CONFIGS_PER_DAY * DAYS
				+ DEVICES_IN_GROUP1 * CONFIGS_PER_DAY * purgeDays);
		this.assertTaskCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertModuleCount(DEVICES * 4);
	}

	@Test
	@DisplayName("Per-device-list oversized config purge test")
	@ResourceLock("DB")
	public void perDeviceListOversizedConfigPurgeTest() {
		final int purgeDays = 18;
		this.purge(0, purgeDays, 2, 0, 0, null, this.group1Devices());

		// The devices with big configs are the last ones, out of group1: nothing is purged
		this.assertConfigCount(DEVICES * CONFIGS_PER_DAY * DAYS);
	}

	@Test
	@DisplayName("Per-device-list module purge test")
	@ResourceLock("DB")
	public void perDeviceListModulePurgeTest() {
		final int purgeDays = 18;
		this.purge(0, 0, 0, 0, purgeDays, null, this.group1Devices());

		this.assertConfigCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertTaskCount(DEVICES * CONFIGS_PER_DAY * DAYS);
		this.assertModuleCount(
			(DEVICES - DEVICES_IN_GROUP1) * 4
				+ DEVICES_IN_GROUP1 * 3);
	}
}
