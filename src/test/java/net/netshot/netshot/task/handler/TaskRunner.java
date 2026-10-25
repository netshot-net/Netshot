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
import java.util.Collections;
import java.util.List;

import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.AutoSnapshotQueue;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandlers;
import net.netshot.netshot.task.TaskSubmitter;
import net.netshot.netshot.task.engine.ChildTaskOrchestrator;
import net.netshot.netshot.task.engine.TaskJob;
import net.netshot.netshot.task.type.GroupBasedTask;
import net.netshot.netshot.task.type.TaskSpec;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;

/**
 * Runs tasks the way the task engine does ({@link TaskJob#runTask(long)}: claim, preparation, device
 * locks, handler, saving of the result, post-task events), with the handlers built as by the wiring
 * except for their access to the scheduler: the follow-up tasks a handler submits are only recorded
 * ({@link #submitted()}), and the child tasks of the group tasks are not run but immediately given the
 * status {@link #setChildStatus(Status)} says, as if they had run.
 *
 * <p>For {@code @DatabaseTest} classes (the wiring must be initialized). The group tasks poll their
 * children every {@code netshot.tasks.sequentialpollintervalms} (500 ms at least).</p>
 */
final class TaskRunner {

	/** The follow-up tasks submitted by the handlers. */
	private final List<Task> submittedTasks = Collections.synchronizedList(new ArrayList<>());

	/** The IDs of the child tasks scheduled by the group tasks, in order. */
	private final List<Long> scheduledChildIds = Collections.synchronizedList(new ArrayList<>());

	/** The status given to the scheduled child tasks. */
	private volatile Status childStatus = Status.SUCCESS;

	private final TaskJob taskJob;

	/**
	 * Builds the runner on the current wiring.
	 */
	TaskRunner() {
		Wiring wiring = TestWiring.get();
		TaskSubmitter submitter = task -> {
			this.submittedTasks.add(task);
			return task;
		};
		ChildTaskOrchestrator orchestrator = new ChildTaskOrchestrator(child -> {
			this.scheduledChildIds.add(child.id());
			TestTasks.setStatus(child.id(), this.childStatus);
		});
		TaskHandlers handlers = new TaskHandlers(List.of(
			new CheckComplianceHandler(wiring.complianceChecker(), wiring.dynamicGroups()),
			new CheckGroupComplianceHandler(wiring.complianceChecker(), wiring.dynamicGroups()),
			new CheckGroupSoftwareHandler(),
			new DiscoverDeviceTypeHandler(TestDrivers.registry(), submitter, wiring.dynamicGroups(), wiring.vaultManager()),
			new PurgeDatabaseHandler(),
			new RunDeviceGroupScriptHandler(orchestrator, TestDrivers.registry()),
			new RunDeviceScriptHandler(TestDrivers.registry(), submitter, wiring.dynamicGroups(), wiring.vaultManager()),
			new RunDiagnosticsHandler(TestDrivers.registry(), wiring.diagnosticAdapters(), submitter, wiring.dynamicGroups(), wiring.vaultManager()),
			new RunGroupDiagnosticsHandler(orchestrator),
			new ScanSubnetsHandler(orchestrator),
			new TakeGroupSnapshotHandler(orchestrator),
			new TakeSnapshotHandler(TestDrivers.registry(), submitter, new AutoSnapshotQueue(), null, wiring.dynamicGroups(),
				wiring.vaultManager())));
		this.taskJob = new TaskJob(handlers, wiring.domainEvents(), wiring.taskManager());
	}

	/**
	 * Sets the status the next scheduled child tasks get.
	 *
	 * @param status the status (a terminal one)
	 */
	void setChildStatus(Status status) {
		this.childStatus = status;
	}

	/**
	 * Gets the follow-up tasks submitted by the handlers so far.
	 *
	 * @return the tasks, in submission order
	 */
	List<Task> submitted() {
		return List.copyOf(this.submittedTasks);
	}

	/**
	 * Gets the IDs of the child tasks scheduled so far.
	 *
	 * @return the IDs, in scheduling order
	 */
	List<Long> scheduledChildIds() {
		return List.copyOf(this.scheduledChildIds);
	}

	/**
	 * Saves a new task as scheduled, and runs it.
	 *
	 * @param task the task, not saved yet
	 * @return the ID of the task
	 * @throws Exception if the task engine fails
	 */
	long run(Task task) throws Exception {
		long id = save(task);
		this.run(id);
		return id;
	}

	/**
	 * Runs a saved task.
	 *
	 * @param id the ID of the task
	 * @throws Exception if the task engine fails
	 */
	void run(long id) throws Exception {
		this.taskJob.runTask(id);
	}

	/**
	 * Saves a new task as scheduled.
	 *
	 * @param task the task, not saved yet
	 * @return the ID of the task
	 */
	static long save(Task task) {
		return TestTasks.add(task, Status.SCHEDULED).id();
	}

	/**
	 * Empties every table of the schema of the test class (except the bookkeeping tables of Liquibase).
	 */
	static void cleanDatabase() {
		new Transactions().run(dsl -> {
			List<String> tables = new ArrayList<>();
			for (org.jooq.Record row : dsl.fetch("select tablename from pg_tables where schemaname = current_schema() "
					+ "and tablename not like 'databasechangelog%'")) {
				tables.add('"' + row.get(0, String.class) + '"');
			}
			dsl.execute("truncate table " + String.join(", ", tables) + " cascade");
		});
	}

	/**
	 * Loads a task.
	 *
	 * @param id the ID of the task
	 * @return the task
	 */
	static Task load(long id) {
		return TestTasks.find(id);
	}

	/**
	 * Lists the child tasks of a task.
	 *
	 * @param parentId the ID of the parent task
	 * @return the children, by order
	 */
	static List<Task> children(long parentId) {
		return TestTasks.children(parentId);
	}

	/**
	 * Builds a new task working on a list of devices.
	 *
	 * @param <S> the type of specification
	 * @param spec the specification
	 * @param comments the comments
	 * @param author the author
	 * @return the task
	 */
	static <S extends TaskSpec & GroupBasedTask> Task forDevices(S spec, String comments, String author) {
		return GroupBasedTask.of(spec, null, comments, author);
	}

	/**
	 * Gets the IDs of devices.
	 *
	 * @param devices the devices
	 * @return the IDs, in order
	 */
	static List<Long> ids(Device... devices) {
		List<Long> ids = new ArrayList<>();
		for (Device device : devices) {
			ids.add(device.id());
		}
		return ids;
	}
}
