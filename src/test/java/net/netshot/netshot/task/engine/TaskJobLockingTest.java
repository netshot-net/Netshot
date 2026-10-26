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
package net.netshot.netshot.task.engine;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.quartz.JobExecutionException;

import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.ScheduleType;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestProperty;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * Tests for the task execution flow of {@link TaskJob}: task claim, device exclusivity
 * (same-type cancellation, waiting for the device, cancellation while waiting) and the
 * repetition of recurring tasks in each case. The target device is disabled, so that the
 * snapshot task itself ends immediately (cancelled) without connecting to anything.
 */
@DatabaseTest
@TestProperty(name = "netshot.tasks.devicelock.pollintervalms", value = "500")
@DisplayName("Task execution with task/device locks (TaskJob, DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
public class TaskJobLockingTest {

	private static final long OTHER_TASK_ID = 999999L;

	Domain domain;
	Device device;

	@BeforeAll
	void initDb() throws Exception {
		TestWiring.get().taskManager().startSchedulers();
	}

	@BeforeEach
	void createData() {
		domain = TestDevices.addDomain("Test domain", "Fake domain for tests");
		Device fake = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1);
		device = TestDevices.add(fake.toBuilder().status(Device.Status.DISABLED).build(), FakeDeviceFactory.getFakeCiscoIosConfig(fake));
	}

	@AfterEach
	void cleanUpData() {
		TestTasks.deleteAll();
		TestDevices.deleteAll();
	}

	private long persistSnapshotTask(Status status, boolean recurring) {
		Task task = TakeSnapshotTask.of(device, "test", "tester", false, true, true);
		if (recurring) {
			task = task.scheduled(Instant.now(), ScheduleType.DAILY, 1);
		}
		return TestTasks.add(task, status).id();
	}

	private Task loadTask(long id) {
		return TestTasks.find(id);
	}

	private long countSnapshotTasks() {
		return TestTasks.all().stream().filter(t -> t.spec() instanceof TakeSnapshotTask).count();
	}

	private Thread runInBackground(long id) {
		Thread thread = new Thread(() -> {
			try {
				TestWiring.get().taskJobFactory().newTaskJob().runTask(id);
			}
			catch (JobExecutionException e) {
				throw new RuntimeException(e);
			}
		});
		thread.start();
		return thread;
	}

	@Test
	@DisplayName("A task which is not waiting to be run anymore is skipped, and not repeated")
	void alreadyRunTaskIsSkipped() throws Exception {
		long id = this.persistSnapshotTask(Status.SUCCESS, true);
		TestWiring.get().taskJobFactory().newTaskJob().runTask(id);
		Assertions.assertEquals(Status.SUCCESS, this.loadTask(id).status());
		Assertions.assertEquals(1, this.countSnapshotTasks(), "A skipped task must not be repeated");
	}

	@Test
	@DisplayName("A task already running (task lock held) is skipped")
	void lockedTaskIsSkipped() throws Exception {
		long id = this.persistSnapshotTask(Status.SCHEDULED, false);
		TaskLockManager lockManager = TestWiring.get().lockManager();
		Assertions.assertTrue(lockManager.tryLock(TaskLockManager.NS_TASK, id, OTHER_TASK_ID));
		try {
			TestWiring.get().taskJobFactory().newTaskJob().runTask(id);
		}
		finally {
			lockManager.unlock(TaskLockManager.NS_TASK, id, OTHER_TASK_ID);
		}
		Assertions.assertEquals(Status.SCHEDULED, this.loadTask(id).status(),
			"The task must be left untouched");
	}

	@Test
	@DisplayName("Snapshot cancelled when another snapshot runs on the device, next occurrence still scheduled")
	void sameTypeIsCancelledAndRepeated() throws Exception {
		long id = this.persistSnapshotTask(Status.SCHEDULED, true);
		TaskLockManager lockManager = TestWiring.get().lockManager();
		Assertions.assertTrue(lockManager.tryLock(TaskLockManager.NS_SNAPSHOT, device.id(), OTHER_TASK_ID));
		try {
			TestWiring.get().taskJobFactory().newTaskJob().runTask(id);
		}
		finally {
			lockManager.unlock(TaskLockManager.NS_SNAPSHOT, device.id(), OTHER_TASK_ID);
		}
		Task task = this.loadTask(id);
		Assertions.assertEquals(Status.CANCELLED, task.status());
		Assertions.assertTrue(task.log().contains("same type"), "Log should explain the cancellation");
		Assertions.assertEquals(2, this.countSnapshotTasks(), "The next occurrence must be scheduled");
		Assertions.assertFalse(lockManager.isHeld(TaskLockManager.NS_DEVICE, device.id()),
			"No lock must remain after the task");
	}

	@Test
	@DisplayName("A task waits for the device held by a task of another type, then runs")
	void waitsForDeviceThenRuns() throws Exception {
		long id = this.persistSnapshotTask(Status.SCHEDULED, false);
		TaskLockManager lockManager = TestWiring.get().lockManager();
		Assertions.assertTrue(lockManager.tryLock(TaskLockManager.NS_DEVICE, device.id(), OTHER_TASK_ID));
		Thread thread;
		try {
			thread = this.runInBackground(id);
			Thread.sleep(3000);
			Assertions.assertTrue(thread.isAlive(), "The task should be waiting for the device");
			Assertions.assertEquals(Status.RUNNING, this.loadTask(id).status());
		}
		finally {
			lockManager.unlock(TaskLockManager.NS_DEVICE, device.id(), OTHER_TASK_ID);
		}
		thread.join(10000);
		Assertions.assertFalse(thread.isAlive(), "The task should have completed");
		Task task = this.loadTask(id);
		String log = task.log();
		Assertions.assertTrue(log.contains("Waiting for the device to be released by task " + OTHER_TASK_ID), log);
		Assertions.assertTrue(log.contains("Device acquired after"), log);
		Assertions.assertTrue(log.contains("not enabled"), "The task should have actually run: " + log);
		Assertions.assertEquals(1, log.split("Waiting for the device").length - 1,
			"The wait should be logged only once");
	}

	@Test
	@DisplayName("A task cancelled while waiting for the device is not repeated")
	void cancelWhileWaiting() throws Exception {
		long id = this.persistSnapshotTask(Status.SCHEDULED, true);
		TaskLockManager lockManager = TestWiring.get().lockManager();
		Assertions.assertTrue(lockManager.tryLock(TaskLockManager.NS_DEVICE, device.id(), OTHER_TASK_ID));
		try {
			Thread thread = this.runInBackground(id);
			Thread.sleep(2000);
			TestWiring.get().taskManager().requestCancel(id);
			thread.join(10000);
			Assertions.assertFalse(thread.isAlive(), "The task should have stopped waiting");
		}
		finally {
			lockManager.unlock(TaskLockManager.NS_DEVICE, device.id(), OTHER_TASK_ID);
		}
		Task task = this.loadTask(id);
		Assertions.assertEquals(Status.CANCELLED, task.status());
		Assertions.assertTrue(task.log().contains("Cancelled while waiting for the device"), task.log());
		Assertions.assertEquals(1, this.countSnapshotTasks(), "A user-cancelled task must not be repeated");
		Assertions.assertFalse(lockManager.isHeld(TaskLockManager.NS_SNAPSHOT, device.id()),
			"The type lock must be released");
	}
}
