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
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.ScheduleType;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.engine.TaskManager.Mode;
import net.netshot.netshot.task.engine.TaskManager.Runner;
import net.netshot.netshot.task.type.GroupBasedTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * The tasks found RUNNING when Netshot starts (it stopped or crashed during their run), or when the cluster member
 * running them is lost: they are marked as failed and not run again (a script must not be applied twice), the next
 * occurrence of a repeating one is scheduled, and a group task resumes.
 */
@DatabaseTest
@DisplayName("Tasks interrupted while running (DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
class InterruptedTaskTest {

	private Device device;

	private StaticDeviceGroup group;

	private Scheduler runnerScheduler;

	private Scheduler masterScheduler;

	@BeforeEach
	void init() throws Exception {
		TaskManager taskManager = TestWiring.get().taskManager();
		taskManager.startSchedulers();
		// The tasks must stay in the schedulers to be checked, not run
		this.runnerScheduler = SchedulerRepository.getInstance().lookup("NetshotRunnerScheduler");
		this.runnerScheduler.standby();
		this.masterScheduler = SchedulerRepository.getInstance().lookup("NetshotMasterScheduler");
		this.masterScheduler.standby();
		Domain domain = TestDevices.addDomain("Test domain", "Fake domain for tests");
		Device fake = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1);
		this.device = TestDevices.add(fake, FakeDeviceFactory.getFakeCiscoIosConfig(fake));
		this.group = TestDevices.addStaticGroup("Group", this.device);
	}

	@AfterEach
	void cleanUp() throws Exception {
		TaskManager taskManager = TestWiring.get().taskManager();
		taskManager.setMode(Mode.SINGLE);
		taskManager.setRunners(List.of());
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

	@Test
	@DisplayName("At startup, an interrupted task is marked as failed, and not run again")
	void interruptedTaskFails() throws Exception {
		Task script = TestTasks.add(this.script(), Status.RUNNING);

		TestWiring.get().taskManager().rescheduleAll();

		Task task = TestTasks.find(script.id());
		Assertions.assertEquals(Status.FAILURE, task.status());
		Assertions.assertTrue(task.log().contains("Netshot was stopped while the task was running"), task.log());
		Assertions.assertFalse(this.runnerScheduler.checkExists(task.identity()), "The task is scheduled to run again");
	}

	@Test
	@DisplayName("At startup, an interrupted repeating task is marked as failed, and its next occurrence is scheduled")
	void interruptedRepeatingTask() throws Exception {
		Instant reference = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
		Task snapshot = TestTasks.add(TakeSnapshotTask.of(this.device, "test", "tester", false, true, true).toBuilder()
			.scheduleType(ScheduleType.DAILY).scheduleFactor(1).scheduleReference(reference).build(), Status.RUNNING);

		TestWiring.get().taskManager().rescheduleAll();

		Assertions.assertEquals(Status.FAILURE, TestTasks.find(snapshot.id()).status());
		List<Task> next = TestTasks.all().stream().filter(t -> t.id() != snapshot.id()).toList();
		Assertions.assertEquals(1, next.size(), "One next occurrence");
		Assertions.assertEquals(Status.SCHEDULED, next.get(0).status());
		Assertions.assertTrue(next.get(0).nextExecutionDate().toInstant().isAfter(Instant.now()));
		Assertions.assertTrue(this.runnerScheduler.checkExists(next.get(0).identity()));
	}

	@Test
	@DisplayName("At startup, an interrupted group task resumes")
	void interruptedGroupTaskResumes() throws Exception {
		Task parent = TestTasks.add(GroupBasedTask.of(new TakeGroupSnapshotTask(this.group.id(), null, 0, true, true),
			this.group.name(), "test", "tester"), Status.RUNNING);

		TestWiring.get().taskManager().rescheduleAll();

		Task task = TestTasks.find(parent.id());
		Assertions.assertEquals(Status.SCHEDULED, task.status());
		Assertions.assertTrue(this.runnerScheduler.checkExists(task.identity()), "The group task is scheduled to run (resume) again");
	}

	@Test
	@DisplayName("Cluster: a running task of a lost member is marked as failed, a waiting one is reassigned")
	void lostMemberTasks() throws Exception {
		TaskManager taskManager = TestWiring.get().taskManager();
		taskManager.setMode(Mode.CLUSTER_MASTER);
		taskManager.setRunners(List.of(new Runner("liverunner0000000001", 100, 1)));
		Task script = TestTasks.add(this.script().toBuilder().runnerId("lostrunner0000000001").build(), Status.RUNNING);
		Task waiting = TestTasks.add(TakeSnapshotTask.of(this.device, "test", "tester", false, true, true).toBuilder()
			.runnerId("lostrunner0000000001").build(), Status.WAITING);

		taskManager.reassignOrphanTasks();

		Task failed = TestTasks.find(script.id());
		Assertions.assertEquals(Status.FAILURE, failed.status());
		Assertions.assertTrue(failed.log().contains("The cluster member running the task was lost"), failed.log());
		Task reassigned = TestTasks.find(waiting.id());
		Assertions.assertEquals(Status.WAITING, reassigned.status());
		Assertions.assertEquals("liverunner0000000001", reassigned.runnerId());
	}
}
