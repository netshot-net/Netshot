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
package net.netshot.netshot.task;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.task.Task.ScheduleType;
import net.netshot.netshot.task.engine.ClusterCoordinator;
import net.netshot.netshot.task.engine.TaskManager;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestProperty;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * Schedules the automatic snapshots as the syslog server, the SNMP trap receiver and the cluster do, with a
 * submitter recording the tasks.
 */
@DatabaseTest
@TestProperty(name = "netshot.snapshots.auto.interval", value = "15")
@DisplayName("Automatic snapshot scheduler (DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
class AutoSnapshotSchedulerTest {

	/** The driver of the fake devices. */
	private static final List<String> IOS = List.of("CiscoIOS12");

	Domain domain;
	Device device;
	List<Task> submitted;
	AutoSnapshotQueue queue;

	@BeforeEach
	void createData() {
		this.domain = TestDevices.addDomain("Test domain", "Fake domain for tests");
		// Management address 172.16.0.1, interfaces 10.0.1.1 and 10.0.1.129
		this.device = TestDevices.addFakeCiscoIos(this.domain, 1);
		this.submitted = new ArrayList<>();
		this.queue = new AutoSnapshotQueue();
	}

	@AfterEach
	void cleanUpData() {
		this.setAnyIp(false);
		TestDevices.deleteAll();
	}

	private void setAnyIp(boolean anyIp) {
		Properties properties = TestConfig.properties();
		properties.setProperty("netshot.snapshots.auto.anyip", Boolean.toString(anyIp));
		TestConfig.init(properties);
	}

	private AutoSnapshotScheduler scheduler(TaskManager taskManager) {
		return new AutoSnapshotScheduler(task -> {
			this.submitted.add(task);
			return task;
		}, TestConfig.store(), taskManager, this.queue, TestWiring.get().core().repositories().device());
	}

	private AutoSnapshotScheduler scheduler() {
		return this.scheduler(TestWiring.get().task().taskManager());
	}

	private static Network4Address ip(String address) throws Exception {
		return new Network4Address(address);
	}

	@Test
	@DisplayName("Schedules a snapshot of the device with the management address, in the configured interval")
	void schedulesByManagementAddress() throws Exception {
		Instant before = Instant.now();

		Assertions.assertTrue(this.scheduler().scheduleSnapshotIfNeeded(IOS, ip("172.16.0.1")));

		Assertions.assertEquals(1, this.submitted.size());
		Task task = this.submitted.get(0);
		TakeSnapshotTask spec = Assertions.assertInstanceOf(TakeSnapshotTask.class, task.spec());
		Assertions.assertEquals(this.device.id(), spec.deviceId());
		Assertions.assertTrue(spec.automatic());
		Assertions.assertEquals("Auto", task.author());
		Assertions.assertEquals(ScheduleType.AT, task.scheduleType());
		Duration delay = Duration.between(before, task.scheduleReference());
		Assertions.assertTrue(delay.compareTo(Duration.ofMinutes(15)) >= 0 && delay.compareTo(Duration.ofMinutes(16)) < 0,
			"Scheduled in the configured interval: " + delay);
	}

	@Test
	@DisplayName("Schedules a single snapshot while one is queued")
	void schedulesOnceWhileQueued() throws Exception {
		AutoSnapshotScheduler scheduler = this.scheduler();

		Assertions.assertTrue(scheduler.scheduleSnapshotIfNeeded(IOS, ip("172.16.0.1")));
		Assertions.assertTrue(scheduler.scheduleSnapshotIfNeeded(IOS, ip("172.16.0.1")));
		Assertions.assertTrue(scheduler.scheduleSnapshotIfNeeded(this.device.id()));
		Assertions.assertEquals(1, this.submitted.size());

		this.queue.clear(this.device.id());
		Assertions.assertTrue(scheduler.scheduleSnapshotIfNeeded(this.device.id()));
		Assertions.assertEquals(2, this.submitted.size(), "Scheduled again once the queued one is done");
	}

	@Test
	@DisplayName("Ignores an address which isn't the management address of a device")
	void ignoresUnknownAddress() throws Exception {
		Assertions.assertFalse(this.scheduler().scheduleSnapshotIfNeeded(IOS, ip("172.16.0.99")));
		Assertions.assertFalse(this.scheduler().scheduleSnapshotIfNeeded(IOS, ip("10.0.1.1")),
			"An interface address only counts with netshot.snapshots.auto.anyip");
		Assertions.assertEquals(List.of(), this.submitted);
	}

	@Test
	@DisplayName("Finds the device by an interface address with netshot.snapshots.auto.anyip")
	void findsByInterfaceAddressWithAnyIp() throws Exception {
		this.setAnyIp(true);

		Assertions.assertTrue(this.scheduler().scheduleSnapshotIfNeeded(IOS, ip("10.0.1.129")));

		Assertions.assertEquals(1, this.submitted.size());
		Assertions.assertEquals(this.device.id(), ((TakeSnapshotTask) this.submitted.get(0).spec()).deviceId());
	}

	@Test
	@DisplayName("Ignores a device of another driver than the ones of the message")
	void ignoresOtherDriver() throws Exception {
		Assertions.assertFalse(this.scheduler().scheduleSnapshotIfNeeded(List.of("JuniperJunos"), ip("172.16.0.1")));
		Assertions.assertEquals(List.of(), this.submitted);
	}

	@Test
	@DisplayName("Ignores a device which isn't in production")
	void ignoresDisabledDevice() throws Exception {
		TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(this.domain, null, 2).toBuilder()
			.status(Device.Status.DISABLED).build());

		Assertions.assertFalse(this.scheduler().scheduleSnapshotIfNeeded(IOS, ip("172.16.0.2")));
		Assertions.assertEquals(List.of(), this.submitted);
	}

	@Test
	@DisplayName("Schedules a snapshot by device ID (cluster request), ignores an unknown ID")
	void schedulesByDeviceId() {
		Assertions.assertFalse(this.scheduler().scheduleSnapshotIfNeeded(this.device.id() + 1000));
		Assertions.assertEquals(List.of(), this.submitted);

		Assertions.assertTrue(this.scheduler().scheduleSnapshotIfNeeded(this.device.id()));
		Assertions.assertEquals(1, this.submitted.size());
	}

	@Test
	@DisplayName("On a cluster member, asks the master for the snapshot instead of scheduling it")
	void clusterMemberAsksMaster() throws Exception {
		Wiring wiring = TestWiring.get();
		List<Long> requested = new ArrayList<>();
		TaskManager member = new TaskManager(wiring.core().transactions(), TestConfig.store(), wiring.core().database(), this.queue,
			wiring.task().lockManager(), wiring.core().repositories().task());
		member.setMode(TaskManager.Mode.CLUSTER_MEMBER);
		member.setCluster(new ClusterCoordinator() {
			@Override
			public String localInstanceId() {
				return "member";
			}

			@Override
			public void requestTasksAssignment() {
			}

			@Override
			public void requestTasksLoad() {
			}

			@Override
			public void requestAutoSnapshot(long deviceId) {
				requested.add(deviceId);
			}
		});

		Assertions.assertTrue(this.scheduler(member).scheduleSnapshotIfNeeded(IOS, ip("172.16.0.1")));

		Assertions.assertEquals(List.of(this.device.id()), requested);
		Assertions.assertEquals(List.of(), this.submitted);
	}
}
