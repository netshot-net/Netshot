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

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.hibernate.Session;

import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandlers;
import net.netshot.netshot.task.TaskSubmitter;
import net.netshot.netshot.task.engine.ChildTaskOrchestrator;
import net.netshot.netshot.task.engine.TaskJob;
import net.netshot.netshot.testing.TestDrivers;

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
		Wiring wiring = Wiring.get();
		TaskSubmitter submitter = this.submittedTasks::add;
		ChildTaskOrchestrator orchestrator = new ChildTaskOrchestrator(child -> {
			this.scheduledChildIds.add(child.getId());
			setStatus(child.getId(), this.childStatus);
		});
		TaskHandlers handlers = new TaskHandlers(List.of(
			new CheckComplianceHandler(wiring.complianceChecker()),
			new CheckGroupComplianceHandler(wiring.complianceChecker()),
			new CheckGroupSoftwareHandler(),
			new DiscoverDeviceTypeHandler(TestDrivers.registry(), submitter),
			new PurgeDatabaseHandler(),
			new RunDeviceGroupScriptHandler(orchestrator, TestDrivers.registry()),
			new RunDeviceScriptHandler(TestDrivers.registry(), submitter),
			new RunDiagnosticsHandler(TestDrivers.registry(), wiring.diagnosticAdapters(), submitter),
			new RunGroupDiagnosticsHandler(orchestrator),
			new ScanSubnetsHandler(orchestrator),
			new TakeGroupSnapshotHandler(orchestrator),
			new TakeSnapshotHandler(TestDrivers.registry(), submitter)));
		this.taskJob = new TaskJob(handlers, wiring.domainEvents());
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
		task.setStatus(Status.SCHEDULED);
		persist(task);
		return task.getId();
	}

	/**
	 * Saves new objects, in one transaction.
	 *
	 * @param objects the objects, in order
	 */
	static void persist(Object... objects) {
		inSession(session -> {
			session.beginTransaction();
			for (Object object : objects) {
				session.persist(object);
			}
			session.getTransaction().commit();
			return null;
		});
	}

	/**
	 * Empties every table of the schema of the test class (except the bookkeeping tables of Liquibase).
	 */
	static void cleanDatabase() {
		inSession(session -> {
			session.beginTransaction();
			session.doWork(connection -> {
				List<String> tables = new ArrayList<>();
				try (Statement statement = connection.createStatement();
						ResultSet rows = statement.executeQuery("select tablename from pg_tables where schemaname = current_schema() "
							+ "and tablename not like 'databasechangelog%'")) {
					while (rows.next()) {
						tables.add('"' + rows.getString(1) + '"');
					}
				}
				try (Statement statement = connection.createStatement()) {
					statement.execute("truncate table " + String.join(", ", tables) + " cascade");
				}
			});
			session.getTransaction().commit();
			return null;
		});
	}

	/**
	 * Runs a function in a new session.
	 *
	 * @param <T> the type of the result
	 * @param function the function
	 * @return the result of the function
	 */
	static <T> T inSession(Function<Session, T> function) {
		try (Session session = Database.getSession()) {
			return function.apply(session);
		}
	}

	/**
	 * Loads a task.
	 *
	 * @param <T> the type of task
	 * @param type the type of task
	 * @param id the ID of the task
	 * @return the task (detached, lazy associations not loaded)
	 */
	static <T extends Task> T load(Class<T> type, long id) {
		return inSession(session -> session.get(type, id));
	}

	/**
	 * Lists the child tasks of a task.
	 *
	 * @param parentId the ID of the parent task
	 * @return the children, by order
	 */
	static List<Task> children(long parentId) {
		return inSession(session -> session
			.createQuery("from Task t where t.parentTaskId = :id order by t.childOrder asc", Task.class)
			.setParameter("id", parentId)
			.list());
	}

	/**
	 * Sets the status of a task in the database.
	 *
	 * @param id the ID of the task
	 * @param status the status
	 */
	private static void setStatus(long id, Status status) {
		inSession(session -> {
			session.beginTransaction();
			session.createMutationQuery("update Task t set t.status = :status where t.id = :id")
				.setParameter("status", status)
				.setParameter("id", id)
				.executeUpdate();
			session.getTransaction().commit();
			return null;
		});
	}
}
