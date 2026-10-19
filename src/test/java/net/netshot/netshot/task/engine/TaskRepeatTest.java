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

import java.util.Date;

import org.hibernate.Session;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task.ScheduleType;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.testing.DatabaseTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * Regression tests for {@link TaskManager#repeatTask(Task)} not being able to
 * reschedule a recurring task once it has finished running.
 *
 * <p>At the tail of {@code TaskJob.execute()}, the task is reloaded in its own
 * (short-lived) session for the post-task hooks, that session is closed, and the
 * resulting detached task is then handed to {@code TaskManager.repeatTask()},
 * which calls {@code task.clone()} to produce the next occurrence. For every
 * group-based task that also supports a one-time device list (e.g.
 * {@link PurgeDatabaseTask}), {@code clone()} used to call {@code getDeviceList()}
 * unconditionally, which touches the lazy {@code deviceListMembers} collection. If
 * that collection was never initialized while a session was open, this throws
 * {@code LazyInitializationException} -- silently, since the caller in
 * {@code TaskJob} only logged the task ID without the exception itself -- so the
 * recurring task was simply never rescheduled again.
 *
 * <p>Two fixes were needed:
 * <ul>
 * <li>{@code TaskJob} re-runs {@code task.prepare(session)} on the reloaded task
 * while its session is still open, before handing it off to {@code repeatTask()}.
 * <li>Every affected {@code clone()} only calls {@code getDeviceList()} when
 * {@code deviceGroup} is null -- mirroring {@code prepare()}'s own guard -- since
 * a device group and a device list are mutually exclusive, so the first fix alone
 * does not initialize {@code deviceListMembers} for a group-restricted task, and
 * {@code clone()} would still blow up on one of those.
 * </ul>
 */
@DatabaseTest
@DisplayName("Recurring task rescheduling after execution (TaskManager.repeatTask, DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
public class TaskRepeatTest {

	@BeforeAll
	void initDb() throws Exception {
		Netshot.readConfig();
		TaskManager.init(Wiring.get().taskJobFactory());
	}

	@AfterEach
	void cleanUpData() {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.createMutationQuery("delete from Task").executeUpdate();
			session.createMutationQuery("delete from DeviceGroup").executeUpdate();
			session.getTransaction().commit();
		}
	}

	/**
	 * Persists a recurring, unrestricted (no device group, no device list)
	 * purge task and returns its ID.
	 * @return the ID of the task
	 */
	private long persistRecurringPurgeTask() {
		PurgeDatabaseTask task = new PurgeDatabaseTask("Test", "tester", 1, 0, 0, 0, 0, (DeviceGroup) null);
		task.schedule(new Date(), ScheduleType.DAILY, 1);
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.persist(task);
			session.getTransaction().commit();
		}
		return task.getId();
	}

	/**
	 * Persists a recurring purge task restricted to a (real, persisted) device
	 * group and returns its ID.
	 * @return the ID of the task
	 */
	private long persistRecurringPerGroupPurgeTask() {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			StaticDeviceGroup group = new StaticDeviceGroup("Group for repeat test");
			session.persist(group);
			PurgeDatabaseTask task = new PurgeDatabaseTask("Test", "tester", 1, 0, 0, 0, 0, group);
			task.schedule(new Date(), ScheduleType.DAILY, 1);
			session.persist(task);
			session.getTransaction().commit();
			return task.getId();
		}
	}

	@Test
	@DisplayName("Fix: an unrestricted recurring purge task is repeated after execution once prepare() has run")
	void recurringPurgeTaskIsRepeatedAfterExecution() {
		long taskId = this.persistRecurringPurgeTask();

		// Mirrors the fixed tail of TaskJob.execute(): prepare() is called on the
		// reloaded task while its session is still open (initializing the lazy
		// device group/list), and only then is the session closed before the task
		// is handed to repeatTask().
		Task reloaded;
		try (Session session = Database.getSession()) {
			reloaded = session.get(Task.class, taskId);
			reloaded.prepare(session);
		}

		Assertions.assertDoesNotThrow(() -> TaskManager.repeatTask(reloaded),
			"Repeating the task after prepare() was called should not throw");

		try (Session session = Database.getSession()) {
			Long count = session
				.createQuery("select count(t) from Task t where t.id != :id", Long.class)
				.setParameter("id", taskId)
				.uniqueResult();
			Assertions.assertEquals(1L, count,
				"A new occurrence of the recurring task should have been persisted");
		}
	}

	@Test
	@DisplayName("Fix: a per-group recurring purge task is repeated after execution (clone() no longer "
		+ "touches the uninitialized device list when a device group is set)")
	void recurringPerGroupPurgeTaskIsRepeatedAfterExecution() {
		long taskId = this.persistRecurringPerGroupPurgeTask();

		// Same as the unrestricted case, except prepare() does NOT initialize
		// deviceListMembers here (deviceGroup is set, so it's irrelevant and left
		// alone) -- this is exactly the production scenario that still broke after
		// the TaskJob-only fix, since PurgeDatabaseTask.clone() used to call
		// getDeviceList() unconditionally regardless of deviceGroup.
		Task reloaded;
		try (Session session = Database.getSession()) {
			reloaded = session.get(Task.class, taskId);
			reloaded.prepare(session);
		}

		Assertions.assertDoesNotThrow(() -> TaskManager.repeatTask(reloaded),
			"Repeating a per-group task after prepare() was called should not throw, "
				+ "even though deviceListMembers was never initialized");

		try (Session session = Database.getSession()) {
			Long count = session
				.createQuery("select count(t) from Task t where t.id != :id", Long.class)
				.setParameter("id", taskId)
				.uniqueResult();
			Assertions.assertEquals(1L, count,
				"A new occurrence of the recurring task should have been persisted");
		}
	}
}
