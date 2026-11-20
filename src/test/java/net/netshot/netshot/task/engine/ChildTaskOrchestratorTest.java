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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.quartz.SchedulerException;

import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.ScheduleMode;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.type.GroupBasedTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestProperty;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;

/**
 * Runs the child tasks of a group task through the orchestrator, in both schedule modes, with a fake scheduler
 * which runs each child on its own (the child is RUNNING once scheduled, and gets its outcome shortly after).
 */
@DatabaseTest
@TestProperty(name = "netshot.tasks.sequentialpollintervalms", value = "500")
@DisplayName("Child task orchestrator (DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
class ChildTaskOrchestratorTest {

	/** Time a fake child task runs, in milliseconds. */
	private static final long CHILD_RUN_MS = 300;

	Domain domain;
	List<Device> devices;
	FakeScheduler scheduler;

	@BeforeEach
	void createData() {
		this.domain = TestDevices.addDomain("Test domain", "Fake domain for tests");
		this.devices = new ArrayList<>();
		for (int i = 1; i <= 3; i++) {
			this.devices.add(TestDevices.addFakeCiscoIos(this.domain, i));
		}
		this.scheduler = new FakeScheduler();
	}

	@AfterEach
	void cleanUpData() throws Exception {
		this.scheduler.awaitRunningChildren();
		TestTasks.deleteAll();
		TestDevices.deleteAll();
	}

	/**
	 * Fake scheduler: marks the child as RUNNING, then gives it its outcome (by position among the children)
	 * from another thread, after {@link #CHILD_RUN_MS}.
	 */
	static final class FakeScheduler implements ChildTaskOrchestrator.ChildScheduler {

		/** Outcome of each child, by position (SUCCESS if absent, null to leave the child running). */
		final Map<Integer, Status> outcomes = Collections.synchronizedMap(new HashMap<>());

		/** Positions of the children which fail to be scheduled. */
		final Set<Integer> unschedulable = Collections.synchronizedSet(new HashSet<>());

		/** Action run when a child is scheduled, by position. */
		final Map<Integer, Runnable> onSchedule = Collections.synchronizedMap(new HashMap<>());

		/** Positions of the scheduled children, in order. */
		final List<Integer> scheduled = Collections.synchronizedList(new ArrayList<>());

		/** Highest number of children running at once. */
		final AtomicInteger maxRunning = new AtomicInteger();

		private final AtomicInteger running = new AtomicInteger();

		private final List<Thread> threads = Collections.synchronizedList(new ArrayList<>());

		@Override
		public Task schedule(Task child) throws SchedulerException {
			int position = child.childOrder();
			if (this.unschedulable.contains(position)) {
				throw new SchedulerException("Test scheduling error");
			}
			this.scheduled.add(position);
			TestTasks.setStatus(child.id(), Status.RUNNING);
			this.maxRunning.accumulateAndGet(this.running.incrementAndGet(), Math::max);
			Runnable action = this.onSchedule.get(position);
			if (action != null) {
				action.run();
			}
			Status outcome = this.outcomes.getOrDefault(position, Status.SUCCESS);
			if (outcome != null) {
				this.threads.add(Thread.ofVirtual().start(() -> {
					try {
						Thread.sleep(CHILD_RUN_MS);
					}
					catch (InterruptedException _) {
						return;
					}
					this.running.decrementAndGet();
					TestTasks.setStatus(child.id(), outcome);
				}));
			}
			return child.withStatus(Status.RUNNING);
		}

		/**
		 * Waits for the fake child tasks to finish.
		 *
		 * @throws InterruptedException if interrupted
		 */
		void awaitRunningChildren() throws InterruptedException {
			for (Thread thread : List.copyOf(this.threads)) {
				thread.join();
			}
		}
	}

	private ChildTaskOrchestrator orchestrator() {
		return new ChildTaskOrchestrator(TestWiring.get().core().transactions(), this.scheduler,
			TestWiring.get().core().repositories().task(), TestConfig.store());
	}

	/**
	 * Stores a group snapshot task on all the devices, with its pre-created children.
	 *
	 * @param mode the schedule mode
	 * @param stopOnFailure whether to stop on the first failure (sequential mode)
	 * @return the run of the parent task
	 */
	private TaskRun parent(ScheduleMode mode, boolean stopOnFailure) {
		List<Long> ids = this.devices.stream().map(Device::id).toList();
		Task parent = TestTasks.add(GroupBasedTask.of(new TakeGroupSnapshotTask(null, ids, -1, false, false), null, "Test", "tester")
			.toBuilder().scheduleMode(mode).stopOnFailure(stopOnFailure).status(Status.RUNNING).build());
		return new TaskRun(parent);
	}

	private List<Task> preCreate(TaskRun parent) {
		List<Task> children = new ArrayList<>();
		for (Device device : this.devices) {
			children.add(TakeSnapshotTask.of(device, "Child", "tester", false, false, false));
		}
		return this.orchestrator().preCreateChildren(parent, children);
	}

	private static void requestCancel(long taskId) {
		TestWiring.get().core().transactions().run(dsl -> TestWiring.get().core().repositories().task().requestCancel(dsl, taskId));
	}

	private static List<Status> statuses(TaskRun parent) {
		return TestTasks.children(parent.getTask().id()).stream().map(Task::status).toList();
	}

	@Nested
	@DisplayName("Parallel mode")
	class Parallel {

		@Test
		@DisplayName("Schedules all the children at once and waits for them to finish")
		void waitsForChildren() {
			TaskRun parent = parent(ScheduleMode.PARALLEL, false);
			List<Task> children = preCreate(parent);

			Status status = orchestrator().orchestrateChildren(parent, children);

			Assertions.assertEquals(Status.SUCCESS, status);
			Assertions.assertEquals(3, scheduler.maxRunning.get(), "All the children run at once");
			Assertions.assertEquals(List.of(Status.SUCCESS, Status.SUCCESS, Status.SUCCESS), statuses(parent),
				"The parent only finishes once the children have");
			Assertions.assertTrue(parent.getLog().contains("3 succeeded, 0 failed, out of 3 device(s)"), parent.getLog());
		}

		@Test
		@DisplayName("Succeeds even when child tasks fail")
		void succeedsWhenChildrenFail() {
			scheduler.outcomes.put(1, Status.FAILURE);
			TaskRun parent = parent(ScheduleMode.PARALLEL, true);
			List<Task> children = preCreate(parent);

			Status status = orchestrator().orchestrateChildren(parent, children);

			Assertions.assertEquals(Status.SUCCESS, status);
			Assertions.assertEquals(List.of(Status.SUCCESS, Status.FAILURE, Status.SUCCESS), statuses(parent));
			Assertions.assertTrue(parent.getLog().contains("2 succeeded, 1 failed"), parent.getLog());
		}

		@Test
		@DisplayName("Fails on a scheduling error, the unschedulable child failed, the others run")
		void failsOnSchedulingError() {
			scheduler.unschedulable.add(1);
			TaskRun parent = parent(ScheduleMode.PARALLEL, false);
			List<Task> children = preCreate(parent);

			Status status = orchestrator().orchestrateChildren(parent, children);

			Assertions.assertEquals(Status.FAILURE, status);
			Assertions.assertEquals(List.of(0, 2), scheduler.scheduled);
			Assertions.assertEquals(List.of(Status.SUCCESS, Status.FAILURE, Status.SUCCESS), statuses(parent));
		}

		@Test
		@DisplayName("Cancelled before scheduling: no child is scheduled, all are cancelled")
		void cancelledBeforeScheduling() {
			TaskRun parent = parent(ScheduleMode.PARALLEL, false);
			List<Task> children = preCreate(parent);
			requestCancel(parent.getTask().id());

			Status status = orchestrator().orchestrateChildren(parent, children);

			Assertions.assertEquals(Status.CANCELLED, status);
			Assertions.assertEquals(List.of(), scheduler.scheduled);
			Assertions.assertEquals(List.of(Status.CANCELLED, Status.CANCELLED, Status.CANCELLED), statuses(parent));
		}

		@Test
		@DisplayName("Cancelled while waiting: stops waiting, the running children keep running")
		void cancelledWhileWaiting() {
			for (int i = 0; i < 3; i++) {
				scheduler.outcomes.put(i, null);
			}
			TaskRun parent = parent(ScheduleMode.PARALLEL, false);
			List<Task> children = preCreate(parent);
			scheduler.onSchedule.put(2, () -> requestCancel(parent.getTask().id()));

			Status status = orchestrator().orchestrateChildren(parent, children);

			Assertions.assertEquals(Status.CANCELLED, status);
			Assertions.assertEquals(List.of(0, 1, 2), scheduler.scheduled);
			Assertions.assertEquals(List.of(Status.RUNNING, Status.RUNNING, Status.RUNNING), statuses(parent));
		}

		@Test
		@DisplayName("Resumes after a restart: the finished children are counted, the running ones waited for")
		void resumes() {
			TaskRun parent = parent(ScheduleMode.PARALLEL, false);
			List<Task> children = preCreate(parent);
			TestTasks.setStatus(children.get(0).id(), Status.FAILURE);
			TestTasks.setStatus(children.get(1).id(), Status.RUNNING);
			Thread.ofVirtual().start(() -> {
				try {
					Thread.sleep(CHILD_RUN_MS);
				}
				catch (InterruptedException _) {
					return;
				}
				TestTasks.setStatus(children.get(1).id(), Status.SUCCESS);
			});
			List<Task> reloaded = orchestrator().reloadExistingChildren(new TaskRun(TestTasks.find(parent.getTask().id())));

			Status status = orchestrator().orchestrateChildren(parent, reloaded);

			Assertions.assertEquals(Status.SUCCESS, status);
			Assertions.assertEquals(List.of(2), scheduler.scheduled, "Only the DELAYED child is scheduled");
			Assertions.assertEquals(List.of(Status.FAILURE, Status.SUCCESS, Status.SUCCESS), statuses(parent));
		}
	}

	@Nested
	@DisplayName("Sequential mode")
	class Sequential {

		@Test
		@DisplayName("Runs the children one at a time, in order")
		void runsInOrder() {
			TaskRun parent = parent(ScheduleMode.SEQUENTIAL, false);
			List<Task> children = preCreate(parent);

			Status status = orchestrator().orchestrateChildren(parent, children);

			Assertions.assertEquals(Status.SUCCESS, status);
			Assertions.assertEquals(List.of(0, 1, 2), scheduler.scheduled);
			Assertions.assertEquals(1, scheduler.maxRunning.get(), "A single child runs at a time");
			Assertions.assertEquals(List.of(Status.SUCCESS, Status.SUCCESS, Status.SUCCESS), statuses(parent));
		}

		@Test
		@DisplayName("Stops on the first failure with stop-on-failure: fails, the remaining children cancelled")
		void stopsOnFailure() {
			scheduler.outcomes.put(0, Status.FAILURE);
			TaskRun parent = parent(ScheduleMode.SEQUENTIAL, true);
			List<Task> children = preCreate(parent);

			Status status = orchestrator().orchestrateChildren(parent, children);

			Assertions.assertEquals(Status.FAILURE, status);
			Assertions.assertEquals(List.of(0), scheduler.scheduled);
			Assertions.assertEquals(List.of(Status.FAILURE, Status.CANCELLED, Status.CANCELLED), statuses(parent));
		}

		@Test
		@DisplayName("Goes on after a failure without stop-on-failure, and succeeds")
		void continuesAfterFailure() {
			scheduler.outcomes.put(0, Status.FAILURE);
			TaskRun parent = parent(ScheduleMode.SEQUENTIAL, false);
			List<Task> children = preCreate(parent);

			Status status = orchestrator().orchestrateChildren(parent, children);

			Assertions.assertEquals(Status.SUCCESS, status);
			Assertions.assertEquals(List.of(0, 1, 2), scheduler.scheduled);
			Assertions.assertEquals(List.of(Status.FAILURE, Status.SUCCESS, Status.SUCCESS), statuses(parent));
			Assertions.assertTrue(parent.getLog().contains("2 succeeded, 1 failed"), parent.getLog());
		}

		@Test
		@DisplayName("Cancelled while running: the current child finishes, the remaining ones are cancelled")
		void cancelledWhileRunning() {
			TaskRun parent = parent(ScheduleMode.SEQUENTIAL, false);
			List<Task> children = preCreate(parent);
			scheduler.onSchedule.put(0, () -> requestCancel(parent.getTask().id()));

			Status status = orchestrator().orchestrateChildren(parent, children);

			Assertions.assertEquals(Status.CANCELLED, status);
			Assertions.assertEquals(List.of(0), scheduler.scheduled);
			Assertions.assertEquals(List.of(Status.SUCCESS, Status.CANCELLED, Status.CANCELLED), statuses(parent));
		}

		@Test
		@DisplayName("Resumes after a restart: tracks the running child, then schedules the next ones")
		void resumes() {
			TaskRun parent = parent(ScheduleMode.SEQUENTIAL, true);
			List<Task> children = preCreate(parent);
			TestTasks.setStatus(children.get(0).id(), Status.SUCCESS);
			TestTasks.setStatus(children.get(1).id(), Status.RUNNING);
			Thread.ofVirtual().start(() -> {
				try {
					Thread.sleep(CHILD_RUN_MS);
				}
				catch (InterruptedException _) {
					return;
				}
				TestTasks.setStatus(children.get(1).id(), Status.SUCCESS);
			});
			List<Task> reloaded = orchestrator().reloadExistingChildren(new TaskRun(TestTasks.find(parent.getTask().id())));

			Status status = orchestrator().orchestrateChildren(parent, reloaded);

			Assertions.assertEquals(Status.SUCCESS, status);
			Assertions.assertEquals(List.of(2), scheduler.scheduled, "Only the DELAYED child is scheduled");
			Assertions.assertTrue(parent.getLog().contains("Resuming tracking of child task " + children.get(1).id()), parent.getLog());
			Assertions.assertEquals(List.of(Status.SUCCESS, Status.SUCCESS, Status.SUCCESS), statuses(parent));
		}

		@Test
		@DisplayName("Fails on a scheduling error, without scheduling the next children")
		void failsOnSchedulingError() {
			scheduler.unschedulable.add(1);
			TaskRun parent = parent(ScheduleMode.SEQUENTIAL, false);
			List<Task> children = preCreate(parent);

			Status status = orchestrator().orchestrateChildren(parent, children);

			Assertions.assertEquals(Status.FAILURE, status);
			Assertions.assertEquals(List.of(0), scheduler.scheduled);
			Assertions.assertEquals(Status.SUCCESS, statuses(parent).get(0));
		}
	}
}
