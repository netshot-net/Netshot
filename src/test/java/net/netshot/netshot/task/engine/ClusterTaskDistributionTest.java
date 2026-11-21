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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.quartz.Scheduler;
import org.quartz.impl.SchedulerRepository;

import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.ScheduleType;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.engine.TaskManager.Mode;
import net.netshot.netshot.task.engine.TaskManager.Runner;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * The distribution of the tasks in a cluster: the master schedules the new tasks, assigns them to the runners
 * (master job), reassigns the tasks of a lost runner, and reschedules its tasks at startup; a member only stores
 * the tasks it creates, for the master.
 */
@DatabaseTest
@DisplayName("Task distribution in a cluster (DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
class ClusterTaskDistributionTest {

	/** A runner of another cluster member, so that the assigned tasks aren't run here. */
	private static final String REMOTE_RUNNER = "remoterunner00000001";

	private Device device;

	private Scheduler runnerScheduler;

	private Scheduler masterScheduler;

	private TaskManager taskManager;

	@BeforeEach
	void init() throws Exception {
		this.taskManager = TestWiring.get().task().taskManager();
		this.taskManager.startSchedulers();
		this.runnerScheduler = SchedulerRepository.getInstance().lookup("NetshotRunnerScheduler");
		this.runnerScheduler.standby();
		this.masterScheduler = SchedulerRepository.getInstance().lookup("NetshotMasterScheduler");
		this.masterScheduler.standby();
		Domain domain = TestDevices.addDomain("Test domain", "Fake domain for tests");
		Device fake = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1);
		this.device = TestDevices.add(fake, FakeDeviceFactory.getFakeCiscoIosConfig(fake));
	}

	@AfterEach
	void cleanUp() throws Exception {
		this.taskManager.setMode(Mode.SINGLE);
		this.taskManager.setRunners(List.of());
		this.runnerScheduler.clear();
		this.runnerScheduler.start();
		this.masterScheduler.clear();
		this.masterScheduler.start();
		TestTasks.deleteAll();
		TestDevices.deleteAll();
	}

	private Task script() {
		return RunDeviceScriptTask.of(this.device, new RunDeviceScriptTask(null, "function run(cli, device) {}", "CiscoIOS12", Map.of(),
			false, false, false), "test", "tester");
	}

	private Task scriptAt(Instant when) {
		return this.script().toBuilder().scheduleType(ScheduleType.AT).scheduleReference(when).build();
	}

	private void master() {
		this.taskManager.setMode(Mode.CLUSTER_MASTER);
		this.taskManager.setRunners(List.of(new Runner(REMOTE_RUNNER, 100, 1)));
	}

	/**
	 * Waits for a task to reach a status.
	 *
	 * @param id the task ID
	 * @param status the expected status
	 * @return the task
	 */
	private static Task awaitStatus(long id, Status status) throws InterruptedException {
		long end = System.currentTimeMillis() + 10000;
		Task task = TestTasks.find(id);
		while (task.status() != status && System.currentTimeMillis() < end) {
			Thread.sleep(100);
			task = TestTasks.find(id);
		}
		return task;
	}

	@Test
	@DisplayName("A member only stores the task it creates (NEW), for the master to schedule it")
	void memberAddsTask() throws Exception {
		this.taskManager.setMode(Mode.CLUSTER_MEMBER);

		Task task = this.taskManager.addTask(this.script());

		Assertions.assertEquals(Status.NEW, TestTasks.find(task.id()).status());
		Assertions.assertFalse(this.masterScheduler.checkExists(task.identity()), "In the master scheduler");
		Assertions.assertFalse(this.runnerScheduler.checkExists(task.identity()), "In the runner scheduler");
	}

	@Test
	@DisplayName("The master schedules the NEW tasks of the members")
	void masterSchedulesNewTasks() throws Exception {
		this.master();
		Task task = TestTasks.add(this.script(), Status.NEW);

		this.taskManager.scheduleNewTasks();

		Assertions.assertEquals(Status.SCHEDULED, TestTasks.find(task.id()).status());
		Assertions.assertTrue(this.masterScheduler.checkExists(task.identity()), "Not in the master scheduler");
		Assertions.assertFalse(this.runnerScheduler.checkExists(task.identity()), "In the runner scheduler");
	}

	@Test
	@DisplayName("When due, the master job assigns the task to a runner, unless it was cancelled meanwhile")
	void masterJobAssignsTask() throws Exception {
		this.master();
		Task due = this.taskManager.addTask(this.script());
		Task cancelled = this.taskManager.addTask(this.scriptAt(Instant.now().plusSeconds(1)));
		Assertions.assertEquals(Status.SCHEDULED, TestTasks.find(due.id()).status());
		TestTasks.setStatus(cancelled.id(), Status.CANCELLED);

		this.masterScheduler.start();

		Task assigned = awaitStatus(due.id(), Status.WAITING);
		Assertions.assertEquals(Status.WAITING, assigned.status());
		Assertions.assertEquals(REMOTE_RUNNER, assigned.runnerId());
		Thread.sleep(2500);
		Task skipped = TestTasks.find(cancelled.id());
		Assertions.assertEquals(Status.CANCELLED, skipped.status());
		Assertions.assertNull(skipped.runnerId(), "A cancelled task got a runner");
	}

	@Test
	@DisplayName("A task of a lost runner is not reassigned while its lock is held (the runner may still be running it)")
	void orphanTaskStillLocked() throws Exception {
		this.master();
		Task waiting = TestTasks.add(this.script().toBuilder().runnerId("lostrunner0000000001").build(), Status.WAITING);
		TaskLockManager locks = this.taskManager.lockManager();
		Assertions.assertTrue(locks.tryLock(TaskLockManager.NS_TASK, waiting.id(), waiting.id()));
		try {
			this.taskManager.reassignOrphanTasks();
			Assertions.assertEquals("lostrunner0000000001", TestTasks.find(waiting.id()).runnerId(), "Reassigned while locked");
		}
		finally {
			locks.unlock(TaskLockManager.NS_TASK, waiting.id(), waiting.id());
		}

		this.taskManager.reassignOrphanTasks();
		Assertions.assertEquals(REMOTE_RUNNER, TestTasks.find(waiting.id()).runnerId());
	}

	@Test
	@DisplayName("Only the master reassigns orphan tasks, and only when it knows of a runner")
	void orphanTasksMasterOnly() {
		Task waiting = TestTasks.add(this.script().toBuilder().runnerId("lostrunner0000000001").build(), Status.WAITING);
		this.taskManager.setMode(Mode.CLUSTER_MEMBER);
		this.taskManager.setRunners(List.of(new Runner(REMOTE_RUNNER, 100, 1)));
		this.taskManager.reassignOrphanTasks();
		Assertions.assertEquals("lostrunner0000000001", TestTasks.find(waiting.id()).runnerId(), "Reassigned by a member");

		this.taskManager.setMode(Mode.CLUSTER_MASTER);
		this.taskManager.setRunners(List.of());
		this.taskManager.reassignOrphanTasks();
		Assertions.assertEquals("lostrunner0000000001", TestTasks.find(waiting.id()).runnerId(), "Reassigned without runner");
	}

	@Test
	@DisplayName("At startup, the master schedules the tasks still to come, and cancels the ones whose time has passed")
	void masterReschedulesAtStartup() throws Exception {
		this.master();
		Task future = TestTasks.add(this.scriptAt(Instant.now().plus(10, ChronoUnit.MINUTES)), Status.SCHEDULED);
		Task past = TestTasks.add(this.scriptAt(Instant.now().minus(10, ChronoUnit.MINUTES)), Status.SCHEDULED);
		Task created = TestTasks.add(this.script(), Status.NEW);

		this.taskManager.rescheduleAll();

		Assertions.assertTrue(this.masterScheduler.checkExists(future.identity()),
			"The future task is not in the master scheduler");
		Task cancelled = TestTasks.find(past.id());
		Assertions.assertEquals(Status.CANCELLED, cancelled.status());
		Assertions.assertTrue(cancelled.log().contains("Task skipped, too late to run."), cancelled.log());
		Assertions.assertEquals(Status.SCHEDULED, TestTasks.find(created.id()).status(), "The NEW task is scheduled");
	}

	@Test
	@DisplayName("At startup, a member only takes back the tasks assigned to itself")
	void memberReschedulesOwnTasks() throws Exception {
		this.taskManager.setMode(Mode.CLUSTER_MEMBER);
		Task other = TestTasks.add(this.script().toBuilder().runnerId(REMOTE_RUNNER).build(), Status.WAITING);
		Task scheduled = TestTasks.add(this.scriptAt(Instant.now().plus(10, ChronoUnit.MINUTES)), Status.SCHEDULED);

		this.taskManager.rescheduleAll();

		Assertions.assertFalse(this.runnerScheduler.checkExists(other.identity()), "Task of another runner taken");
		Assertions.assertFalse(this.masterScheduler.checkExists(scheduled.identity()), "Master task taken by a member");
		Assertions.assertEquals(Status.WAITING, TestTasks.find(other.id()).status());
		Assertions.assertEquals(Status.SCHEDULED, TestTasks.find(scheduled.id()).status());
	}
}
