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
package net.netshot.netshot.task.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestProperty;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * Stop of the task manager ({@link TaskManager#shutdown()}): the running tasks are given
 * {@code netshot.shutdown.timeout} to end, then the ones still running are marked as failed.
 * A task is kept running by holding the lock of its device (it waits for the device).
 */
@DatabaseTest
@TestProperty(name = "netshot.tasks.devicelock.pollintervalms", value = "500")
@TestProperty(name = "netshot.shutdown.timeout", value = "1")
@DisplayName("Task manager shutdown (DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
public class TaskManagerShutdownTest {

	private static final long OTHER_TASK_ID = 999998L;

	private Device device;

	@BeforeEach
	void init() throws Exception {
		TestWiring.get().taskManager().startSchedulers();
		Domain domain = TestDevices.addDomain("Test domain", "Fake domain for tests");
		Device fake = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1);
		this.device = TestDevices.add(fake.toBuilder().status(Device.Status.DISABLED).build(),
			FakeDeviceFactory.getFakeCiscoIosConfig(fake));
	}

	@AfterEach
	void cleanUp() {
		TestTasks.deleteAll();
		TestDevices.deleteAll();
	}

	private static void await(BooleanSupplier condition, String message) throws InterruptedException {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
		while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
			Thread.sleep(100);
		}
		Assertions.assertTrue(condition.getAsBoolean(), message);
	}

	@Test
	@DisplayName("A task still running after the timeout is marked as failed")
	void runningTaskIsFailed() throws Exception {
		TaskLockManager lockManager = TestWiring.get().lockManager();
		Assertions.assertTrue(lockManager.tryLock(TaskLockManager.NS_DEVICE, this.device.id(), OTHER_TASK_ID));
		try {
			Task task = TestWiring.get().taskManager().addTask(TakeSnapshotTask.of(this.device, "test", "tester", false, true, true));
			// Claimed by its job (RUNNING), and waiting for the device
			await(() -> TestTasks.find(task.id()).status() == Status.RUNNING, "The task isn't running");
			long start = System.currentTimeMillis();
			TestWiring.get().taskManager().shutdown();
			Assertions.assertTrue(System.currentTimeMillis() - start >= 1000, "The running task wasn't waited for");
			Task stopped = TestTasks.find(task.id());
			Assertions.assertEquals(Status.FAILURE, stopped.status());
			Assertions.assertTrue(stopped.log().contains("Netshot was stopped while the task was running."), stopped.log());
			Assertions.assertEquals(1L, TestWiring.get().taskManager().stats().get("interruptedOnStop"));
			Assertions.assertEquals(1L, TestWiring.get().taskManager().stats().get("started"));
			// Let the job thread end
			TestWiring.get().taskManager().requestCancel(task.id());
		}
		finally {
			lockManager.unlock(TaskLockManager.NS_DEVICE, this.device.id(), OTHER_TASK_ID);
		}
	}

	@Test
	@DisplayName("Without running task, the stop doesn't wait, and the scheduled tasks are kept")
	void scheduledTaskIsKept() throws Exception {
		Task task = TestWiring.get().taskManager().addTask(TakeSnapshotTask.of(this.device, "test", "tester", false, true, true)
			.scheduledIn(60));
		long start = System.currentTimeMillis();
		TestWiring.get().taskManager().shutdown();
		Assertions.assertTrue(System.currentTimeMillis() - start < 1000, "The stop waited without running task");
		Assertions.assertEquals(Status.SCHEDULED, TestTasks.find(task.id()).status());
	}
}
