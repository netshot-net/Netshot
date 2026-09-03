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
package net.netshot.netshot;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.hibernate.Session;
import org.quartz.JobKey;

import net.netshot.netshot.database.Database;
import net.netshot.netshot.device.Config;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.DeviceGroup;
import net.netshot.netshot.device.Domain;
import net.netshot.netshot.device.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.work.Task;
import net.netshot.netshot.work.Task.ScheduleType;
import net.netshot.netshot.work.Task.SequentialScheduling;
import net.netshot.netshot.work.Task.SequentialScheduling.NextAction;
import net.netshot.netshot.work.Task.Status;
import net.netshot.netshot.work.TaskDeviceListMember;
import net.netshot.netshot.work.tasks.PurgeDatabaseTask;
import net.netshot.netshot.work.tasks.TakeGroupSnapshotTask;
import net.netshot.netshot.work.tasks.TakeSnapshotTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.ResourceLock;

public class TaskTest extends WithDatabaseTest {

	@BeforeAll
	static void initNetshot() {
		Netshot.readConfig();
	}

	@Nested
	@DisplayName("Next execution time of tasks")
	class TaskExecutionTimeTest {

		Task task = new Task() {
			@Override
			public JobKey getIdentity() {
				return null;
			}

			@Override
			public String getTaskDescription() {
				return null;
			}

			@Override
			public void run() {
			}
		};

		@Test
		@DisplayName("Next execution time of ASAP task")
		void asapTask() {
			task.setScheduleType(ScheduleType.ASAP);
			Assertions.assertNull(task.getNextExecutionDate(),
				"Next execution date for ASAP task is not null");
		}

		@Test
		@DisplayName("Next execution time of AT task")
		void atTask() {
			Calendar inSixteenMinutes = Calendar.getInstance();
			inSixteenMinutes.add(Calendar.MINUTE, 16);
			task.setScheduleType(ScheduleType.AT);
			task.setScheduleReference(inSixteenMinutes.getTime());
			Assertions.assertEquals(task.getNextExecutionDate(), inSixteenMinutes.getTime(),
				"Next execution date for AT task is not the schedule reference");
		}

		@Test
		@DisplayName("Next execution time of EVERY 2 HOURS imminent task")
		void everyTwoHoursImminentTask() {
			Calendar inTenSeconds = Calendar.getInstance();
			inTenSeconds.add(Calendar.SECOND, 10);
			task.setScheduleReference(inTenSeconds.getTime());
			task.setScheduleType(ScheduleType.HOURLY);
			task.setScheduleFactor(2);
			Calendar inTwoHours = (Calendar) inTenSeconds.clone();
			inTwoHours.add(Calendar.HOUR, 2);
			Assertions.assertEquals(task.getNextExecutionDate(), inTwoHours.getTime(),
				"Next execution date for EVERY 2 HOURS task is not in two hours");
		}

		@Test
		@DisplayName("Next execution time of EVERY 2 HOURS task")
		void everyTwoHoursTask() {
			Calendar inEightySeconds = Calendar.getInstance();
			inEightySeconds.add(Calendar.SECOND, 80);
			task.setScheduleReference(inEightySeconds.getTime());
			task.setScheduleType(ScheduleType.HOURLY);
			task.setScheduleFactor(2);
			Assertions.assertEquals(task.getNextExecutionDate(), inEightySeconds.getTime(),
				"Next execution date for EVERY 2 HOURS task is not now");
		}

		@Test
		@DisplayName("Next execution time of EVERY WEEK task")
		void everyWeekTask() {
			Calendar inTenSeconds = Calendar.getInstance();
			inTenSeconds.add(Calendar.SECOND, 10);
			task.setScheduleReference(inTenSeconds.getTime());
			task.setScheduleType(ScheduleType.WEEKLY);
			task.setScheduleFactor(1);
			Calendar inOneWeek = (Calendar) inTenSeconds.clone();
			inOneWeek.add(Calendar.WEEK_OF_YEAR, 1);
			Assertions.assertEquals(task.getNextExecutionDate(), inOneWeek.getTime(),
				"Next execution date for EVERY WEEK task is not now");
		}

		@Test
		@DisplayName("Next execution time of EVERY SIX MONTHS task")
		void everySixMonths() {
			Calendar inTenSeconds = Calendar.getInstance();
			inTenSeconds.add(Calendar.SECOND, 10);
			task.setScheduleReference(inTenSeconds.getTime());
			task.setScheduleType(ScheduleType.MONTHLY);
			task.setScheduleFactor(6);
			Calendar inSixMonths = (Calendar) inTenSeconds.clone();
			inSixMonths.add(Calendar.MONTH, 6);
			Assertions.assertEquals(task.getNextExecutionDate(), inSixMonths.getTime(),
				"Next execution date for EVERY SIX MONTHS task is not now");
		}

		@Test
		@DisplayName("Next execution time of EVERY FIRST OF THE MONTH task")
		void everyFirstOfTheMonth() {
			Calendar reference = Calendar.getInstance();
			reference.set(2016, 01, 01, 16, 00, 00);
			reference.set(Calendar.MILLISECOND, 0);
			task.setScheduleReference(reference.getTime());
			task.setScheduleType(ScheduleType.MONTHLY);
			task.setScheduleFactor(1);
			Calendar nextFirst = Calendar.getInstance();
			nextFirst.set(Calendar.DAY_OF_MONTH, 1);
			nextFirst.set(Calendar.HOUR_OF_DAY, 16);
			nextFirst.set(Calendar.MINUTE, 0);
			nextFirst.set(Calendar.SECOND, 0);
			nextFirst.set(Calendar.MILLISECOND, 0);
			if (nextFirst.before(Calendar.getInstance())) {
				nextFirst.add(Calendar.MONTH, 1);
			}
			Assertions.assertEquals(task.getNextExecutionDate(), nextFirst.getTime(),
				"Next execution date for EVERY FIRST OF THE MONTH is not next first of the month");
		}

	}

	@Nested
	@DisplayName("Sequential group-task scheduling decisions")
	class SequentialDecisionTest {

		@Test
		@DisplayName("Continues while nothing failed and no cancel was requested")
		void continuesByDefault() {
			Assertions.assertEquals(NextAction.CONTINUE,
				SequentialScheduling.decideNextAction(false, false, false));
			Assertions.assertEquals(NextAction.CONTINUE,
				SequentialScheduling.decideNextAction(false, true, false));
		}

		@Test
		@DisplayName("Continues after a failure when stopOnFailure is false")
		void continuesOnFailureWhenNotStopping() {
			Assertions.assertEquals(NextAction.CONTINUE,
				SequentialScheduling.decideNextAction(true, false, false));
		}

		@Test
		@DisplayName("Stops after a failure when stopOnFailure is true")
		void stopsOnFailureWhenStopping() {
			Assertions.assertEquals(NextAction.STOP,
				SequentialScheduling.decideNextAction(true, true, false));
		}

		@Test
		@DisplayName("Stops when cancellation was requested, regardless of stopOnFailure")
		void stopsOnCancelRequested() {
			Assertions.assertEquals(NextAction.STOP,
				SequentialScheduling.decideNextAction(false, false, true));
			Assertions.assertEquals(NextAction.STOP,
				SequentialScheduling.decideNextAction(false, true, true));
		}

		@Test
		@DisplayName("Final status is SUCCESS when nothing failed and no cancel was honored")
		void finalStatusSuccess() {
			Assertions.assertEquals(Status.SUCCESS,
				SequentialScheduling.decideFinalStatus(false, false, false, false));
			Assertions.assertEquals(Status.SUCCESS,
				SequentialScheduling.decideFinalStatus(false, true, false, false));
		}

		@Test
		@DisplayName("Final status is CANCELLED when a cancel was honored and nothing failed")
		void finalStatusCancelled() {
			Assertions.assertEquals(Status.CANCELLED,
				SequentialScheduling.decideFinalStatus(false, false, true, false));
		}

		@Test
		@DisplayName("Final status is FAILURE if a child failed and stop-on-failure is enabled, even if a cancel was also honored")
		void finalStatusFailureWinsOverCancel() {
			Assertions.assertEquals(Status.FAILURE,
				SequentialScheduling.decideFinalStatus(true, true, true, false));
			Assertions.assertEquals(Status.FAILURE,
				SequentialScheduling.decideFinalStatus(true, true, false, false));
		}

		@Test
		@DisplayName("Final status is SUCCESS if a child failed but stop-on-failure is disabled and nothing else went wrong")
		void finalStatusSuccessDespiteFailureWhenNotStopping() {
			Assertions.assertEquals(Status.SUCCESS,
				SequentialScheduling.decideFinalStatus(true, false, false, false));
		}

		@Test
		@DisplayName("Final status is CANCELLED if a child failed but stop-on-failure is disabled and a cancel was honored")
		void finalStatusCancelledDespiteFailureWhenNotStopping() {
			Assertions.assertEquals(Status.CANCELLED,
				SequentialScheduling.decideFinalStatus(true, false, true, false));
		}

		@Test
		@DisplayName("Final status is FAILURE whenever a scheduling error occurred, regardless of other flags")
		void finalStatusFailureOnSchedulingError() {
			Assertions.assertEquals(Status.FAILURE,
				SequentialScheduling.decideFinalStatus(false, false, false, true));
			Assertions.assertEquals(Status.FAILURE,
				SequentialScheduling.decideFinalStatus(false, false, true, true));
		}
	}

	@Nested
	@DisplayName("Group task device list / parent-child tracking (DB-backed)")
	@TestInstance(Lifecycle.PER_CLASS)
	class DeviceListAndParentChildTest {

		Domain domain;
		Device device1;
		Device device2;

		@BeforeAll
		void initDb() throws Exception {
			Properties config = getDatabaseConfig("tasktest");
			config.setProperty("netshot.log.file", "CONSOLE");
			config.setProperty("netshot.log.level", "WARN");
			Netshot.initConfig(config);
			Database.update();
			Database.init();
		}

		@BeforeEach
		void createData() {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				domain = new Domain("Test domain", "Fake domain for tests", null, null);
				session.persist(domain);
				device1 = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1);
				device2 = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 2);
				session.persist(device1);
				session.persist(device2);
				session.getTransaction().commit();
			}
		}

		@AfterEach
		void cleanUpData() {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.createMutationQuery("delete from Task").executeUpdate();
				session.createMutationQuery("delete from Device").executeUpdate();
				session.createMutationQuery("delete from Domain").executeUpdate();
				session.getTransaction().commit();
			}
		}

		/**
		 * Mimics TaskManager's persistence of a new task, without needing the Quartz
		 * scheduler to be initialized. Task.deviceListMembers is cascade=ALL, so
		 * persisting the task alone is enough to persist its device list too.
		 */
		private void persist(Task task) {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(task);
				session.getTransaction().commit();
			}
		}

		@Test
		@DisplayName("Device list is persisted in order and reloads the correct concrete subtype")
		@ResourceLock("DB")
		void deviceListPersistsInOrder() {
			TakeGroupSnapshotTask task = new TakeGroupSnapshotTask(
				List.of(device1, device2), "test", "tester", -1, true, true);
			this.persist(task);

			try (Session session = Database.getSession()) {
				List<TaskDeviceListMember> members = session
					.createQuery(
						"from TaskDeviceListMember m where m.key.task.id = :id order by m.position asc",
						TaskDeviceListMember.class)
					.setParameter("id", task.getId())
					.list();
				Assertions.assertEquals(2, members.size(), "Expected one member per listed device");
				Assertions.assertEquals(0, members.get(0).getPosition());
				Assertions.assertEquals(device1.getId(), members.get(0).getDevice().getId());
				Assertions.assertEquals(1, members.get(1).getPosition());
				Assertions.assertEquals(device2.getId(), members.get(1).getDevice().getId());

				Task reloaded = session.get(Task.class, task.getId());
				Assertions.assertInstanceOf(TakeGroupSnapshotTask.class, reloaded,
					"Reloaded task should keep its concrete subtype (dtype discriminator)");
				Assertions.assertEquals(-1, ((TakeGroupSnapshotTask) reloaded).getLimitToOutofdateDeviceHours(),
					"Subclass-specific attribute should round-trip through the shared JSON attributes column");
			}
		}

		@Test
		@DisplayName("Deleting a device cascades removal from its task's device list")
		@ResourceLock("DB")
		void deviceDeletionCascadesFromDeviceList() {
			TakeGroupSnapshotTask task = new TakeGroupSnapshotTask(
				List.of(device2), "test", "tester", -1, true, true);
			this.persist(task);

			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.remove(session.get(Device.class, device2.getId()));
				session.getTransaction().commit();
			}

			try (Session session = Database.getSession()) {
				Long count = session
					.createQuery("select count(m) from TaskDeviceListMember m where m.key.task.id = :id", Long.class)
					.setParameter("id", task.getId())
					.uniqueResult();
				Assertions.assertEquals(0L, count, "Device-list membership should be cascade-deleted with the device");
			}
		}

		@Test
		@DisplayName("Parent/child linkage: children are stamped, survive parent deletion with parentTaskId cleared")
		@ResourceLock("DB")
		void parentChildLinkageAndCascade() {
			TakeGroupSnapshotTask parent = new TakeGroupSnapshotTask(
				List.of(device1), "test", "tester", -1, true, true);
			this.persist(parent);

			TakeSnapshotTask child = new TakeSnapshotTask(device1, "child", "tester", false, true, true);
			child.setParentTaskId(parent.getId());
			child.setChildOrder(0);
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(child);
				session.getTransaction().commit();
			}

			try (Session session = Database.getSession()) {
				Task reloadedChild = session.get(Task.class, child.getId());
				Assertions.assertEquals(parent.getId(), reloadedChild.getParentTaskId());
				List<Task> children = session
					.createQuery("from Task t where t.parentTaskId = :id order by t.childOrder asc", Task.class)
					.setParameter("id", parent.getId())
					.list();
				Assertions.assertEquals(1, children.size());
			}

			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.remove(session.get(Task.class, parent.getId()));
				session.getTransaction().commit();
			}

			try (Session session = Database.getSession()) {
				Task reloadedChild = session.get(Task.class, child.getId());
				Assertions.assertNotNull(reloadedChild, "Child task should survive its parent's deletion");
				Assertions.assertNull(reloadedChild.getParentTaskId(),
					"Child's parentTaskId should be cleared (SET NULL), not cascade-deleted");
			}
		}

		/**
		 * preCreateChildren/reloadExistingChildren/cancelRemainingDelayedChildren are protected --
		 * they're only meant to be called by a group task's own run() loop -- and the concrete task
		 * classes that use them are final, so they can't be exercised through an anonymous subclass
		 * the way {@link TaskExecutionTimeTest} does. Reflection is the pragmatic way to whitebox-test
		 * them directly against a real, persisted entity without loosening their visibility for
		 * production callers.
		 */
		@SuppressWarnings("unchecked")
		private <T> T invokeProtected(Task task, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
			Method method = Task.class.getDeclaredMethod(methodName, paramTypes);
			method.setAccessible(true);
			return (T) method.invoke(task, args);
		}

		@Test
		@DisplayName("preCreateChildren persists every child upfront, in order, all DELAYED")
		@ResourceLock("DB")
		void preCreateChildrenPersistsAllAsDelayed() throws Exception {
			TakeGroupSnapshotTask parent = new TakeGroupSnapshotTask(
				List.of(device1, device2), "test", "tester", -1, true, true);
			parent.setPriority(7);
			this.persist(parent);

			List<Task> children = new ArrayList<>(List.of(
				new TakeSnapshotTask(device1, "child", "tester", false, true, true),
				new TakeSnapshotTask(device2, "child", "tester", false, true, true)));
			this.invokeProtected(parent, "preCreateChildren", new Class<?>[] { List.class }, children);

			Assertions.assertTrue(children.get(0).getId() != 0, "Pre-creation should assign a real id");
			Assertions.assertTrue(children.get(1).getId() != 0, "Pre-creation should assign a real id");

			try (Session session = Database.getSession()) {
				List<Task> reloaded = session
					.createQuery("from Task t where t.parentTaskId = :id order by t.childOrder asc", Task.class)
					.setParameter("id", parent.getId())
					.list();
				Assertions.assertEquals(2, reloaded.size());
				Assertions.assertEquals(0, reloaded.get(0).getChildOrder());
				Assertions.assertEquals(Status.DELAYED, reloaded.get(0).getStatus());
				Assertions.assertEquals(7, reloaded.get(0).getPriority());
				Assertions.assertEquals(1, reloaded.get(1).getChildOrder());
				Assertions.assertEquals(Status.DELAYED, reloaded.get(1).getStatus());
			}
		}

		@Test
		@DisplayName("reloadExistingChildren resumes from previously pre-created children (e.g. after a restart)")
		@ResourceLock("DB")
		void reloadExistingChildrenResumesAfterRestart() throws Exception {
			TakeGroupSnapshotTask parent = new TakeGroupSnapshotTask(
				List.of(device1, device2), "test", "tester", -1, true, true);
			this.persist(parent);

			List<Task> created = new ArrayList<>(List.of(
				new TakeSnapshotTask(device1, "child", "tester", false, true, true),
				new TakeSnapshotTask(device2, "child", "tester", false, true, true)));
			this.invokeProtected(parent, "preCreateChildren", new Class<?>[] { List.class }, created);

			// Simulate a Netshot restart: a fresh Task instance for the same id, no memory of `created`.
			try (Session session = Database.getSession()) {
				Task reloadedParent = session.get(Task.class, parent.getId());
				List<Task> resumed = this.invokeProtected(
					reloadedParent, "reloadExistingChildren", new Class<?>[0]);
				Assertions.assertEquals(2, resumed.size());
				Assertions.assertEquals(created.get(0).getId(), resumed.get(0).getId());
				Assertions.assertEquals(created.get(1).getId(), resumed.get(1).getId());
				Assertions.assertEquals(Status.DELAYED, resumed.get(0).getStatus());
			}
		}

		@Test
		@DisplayName("cancelRemainingDelayedChildren only cancels still-DELAYED children from the given position")
		@ResourceLock("DB")
		void cancelRemainingDelayedChildrenOnlyTouchesDelayedFromIndex() throws Exception {
			TakeGroupSnapshotTask parent = new TakeGroupSnapshotTask(
				List.of(device1, device2), "test", "tester", -1, true, true);
			this.persist(parent);

			List<Task> children = new ArrayList<>(List.of(
				new TakeSnapshotTask(device1, "child", "tester", false, true, true),
				new TakeSnapshotTask(device2, "child", "tester", false, true, true)));
			this.invokeProtected(parent, "preCreateChildren", new Class<?>[] { List.class }, children);

			// Device 0 already completed (as orchestrateChildren would have observed by reaching
			// out to the DB) -- its in-memory Status must reflect that, since cancelRemainingDelayedChildren
			// only bulk-cancels ids whose in-memory Status is still DELAYED.
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.createMutationQuery("update Task t set t.status = :success where t.id = :id")
					.setParameter("success", Status.SUCCESS)
					.setParameter("id", children.get(0).getId())
					.executeUpdate();
				session.getTransaction().commit();
			}
			children.get(0).setStatus(Status.SUCCESS);

			this.invokeProtected(parent, "cancelRemainingDelayedChildren",
				new Class<?>[] { List.class, int.class }, children, 0);

			try (Session session = Database.getSession()) {
				Assertions.assertEquals(Status.SUCCESS,
					session.get(Task.class, children.get(0).getId()).getStatus(),
					"Already-completed child should be untouched");
				Assertions.assertEquals(Status.CANCELLED,
					session.get(Task.class, children.get(1).getId()).getStatus(),
					"Still-DELAYED child should be cancelled");
			}
		}
	}

	@Nested
	@DisplayName("Task.Status persisted value (StatusConverter)")
	class StatusConverterTest {

		private final Status.StatusConverter converter = new Status.StatusConverter();

		@Test
		@DisplayName("Every status converts to its fixed, explicit value (not declaration order)")
		void convertsToFixedValue() {
			Assertions.assertEquals(0, converter.convertToDatabaseColumn(Status.CANCELLED));
			Assertions.assertEquals(1, converter.convertToDatabaseColumn(Status.FAILURE));
			Assertions.assertEquals(2, converter.convertToDatabaseColumn(Status.NEW));
			Assertions.assertEquals(3, converter.convertToDatabaseColumn(Status.RUNNING));
			Assertions.assertEquals(4, converter.convertToDatabaseColumn(Status.SCHEDULED));
			Assertions.assertEquals(5, converter.convertToDatabaseColumn(Status.SUCCESS));
			Assertions.assertEquals(6, converter.convertToDatabaseColumn(Status.WAITING));
			Assertions.assertEquals(7, converter.convertToDatabaseColumn(Status.DELAYED));
		}

		@Test
		@DisplayName("Round-trips through the persisted value")
		void roundTrips() {
			for (Status status : Status.values()) {
				int value = converter.convertToDatabaseColumn(status);
				Assertions.assertEquals(status, converter.convertToEntityAttribute(value));
			}
		}

		@Test
		@DisplayName("null converts both ways to null")
		void handlesNull() {
			Assertions.assertNull(converter.convertToDatabaseColumn(null));
			Assertions.assertNull(converter.convertToEntityAttribute(null));
		}

		@Test
		@DisplayName("Unknown persisted value is rejected rather than silently mismatched")
		void rejectsUnknownValue() {
			Assertions.assertThrows(IllegalArgumentException.class, () -> Status.fromValue(99));
		}
	}

	@Nested
	@DisplayName("Orphaned DELAYED task cleanup (TaskManager, DB-backed)")
	@TestInstance(Lifecycle.PER_CLASS)
	class OrphanedDelayedCleanupTest {

		Domain domain;
		Device device;

		@BeforeAll
		void initDb() throws Exception {
			Properties config = getDatabaseConfig("tasktest_orphan");
			config.setProperty("netshot.log.file", "CONSOLE");
			config.setProperty("netshot.log.level", "WARN");
			Netshot.initConfig(config);
			Database.update();
			Database.init();
		}

		@BeforeEach
		void createData() {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				domain = new Domain("Test domain", "Fake domain for tests", null, null);
				session.persist(domain);
				device = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1);
				session.persist(device);
				session.getTransaction().commit();
			}
		}

		@AfterEach
		void cleanUpData() {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.createMutationQuery("delete from Task").executeUpdate();
				session.createMutationQuery("delete from Device").executeUpdate();
				session.createMutationQuery("delete from Domain").executeUpdate();
				session.getTransaction().commit();
			}
		}

		private long persistDelayed(Long parentTaskId) {
			TakeSnapshotTask task = new TakeSnapshotTask(device, "test", "tester", false, true, true);
			task.setStatus(Status.DELAYED);
			task.setParentTaskId(parentTaskId);
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(task);
				session.getTransaction().commit();
			}
			return task.getId();
		}

		@Test
		@DisplayName("Cancels DELAYED tasks with no parent, leaves DELAYED children of a live parent alone")
		@ResourceLock("DB")
		void cancelsOnlyParentlessDelayedTasks() {
			long orphanId = this.persistDelayed(null);
			TakeGroupSnapshotTask parent = new TakeGroupSnapshotTask(
				List.of(device), "test", "tester", -1, true, true);
			this.persist(parent);
			long childId = this.persistDelayed(parent.getId());

			TaskManager.cancelOrphanedDelayedTasks();

			try (Session session = Database.getSession()) {
				Assertions.assertEquals(Status.CANCELLED, session.get(Task.class, orphanId).getStatus(),
					"Parentless DELAYED task should be cancelled");
				Assertions.assertEquals(Status.DELAYED, session.get(Task.class, childId).getStatus(),
					"DELAYED child of a still-existing parent should be left alone");
			}
		}

		private void persist(Task task) {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(task);
				session.getTransaction().commit();
			}
		}
	}

	/**
	 * Tests for {@link PurgeDatabaseTask}'s "remove orphan binary files"
	 * option: a filesystem sweep of the binary attribute storage folder that
	 * deletes files no longer (or never) matching a database record, while
	 * never touching a file that does - even one still sitting at its
	 * pending (not-yet-finalized) name.
	 */
	@Nested
	@DisplayName("Remove orphan binary files (PurgeDatabaseTask, DB-backed)")
	@TestInstance(Lifecycle.PER_CLASS)
	class OrphanBinaryFilePurgeTest {

		Path storagePath;

		@BeforeAll
		void initDb() throws Exception {
			storagePath = Files.createTempDirectory("netshot-test-orphan-purge-");
			Properties config = getDatabaseConfig("tasktest_orphanpurge");
			config.setProperty("netshot.log.file", "CONSOLE");
			config.setProperty("netshot.log.level", "WARN");
			config.setProperty("netshot.snapshots.binary.path", storagePath.toString());
			Netshot.initConfig(config);
			Database.update();
			Database.init();
			ConfigBinaryFileAttribute.loadConfig();
		}

		@AfterEach
		void cleanUpData() {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.createMutationQuery("delete from Config").executeUpdate();
				session.createMutationQuery("delete from LongTextConfiguration").executeUpdate();
				session.createMutationQuery("delete from Device").executeUpdate();
				session.createMutationQuery("delete from Domain").executeUpdate();
				session.getTransaction().commit();
			}
		}

		private void setOldMtime(Path path) throws IOException {
			Files.setLastModifiedTime(path, FileTime.from(Instant.now().minus(48, ChronoUnit.HOURS)));
		}

		@Test
		@DisplayName("Orphan binary file sweep deletes only files with no matching database record, sparing recent or referenced ones")
		@ResourceLock("DB")
		void removeOrphanFilesDeletesOnlyTrueOrphans() throws Exception {
			Device device;
			ConfigBinaryFileAttribute attrA;
			ConfigBinaryFileAttribute attrB;
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
				session.persist(domain);
				device = new Device("CiscoIOS12", null, domain, "test");
				Config config = new Config(device);
				attrA = new ConfigBinaryFileAttribute(config, "attrA", "fileA.bin");
				attrB = new ConfigBinaryFileAttribute(config, "attrB", "fileB.bin");
				config.addAttribute(attrA);
				config.addAttribute(attrB);
				device.getConfigs().add(config);
				device.setLastConfig(config);
				session.persist(device);
				session.getTransaction().commit();
			}

			// attrA: fully finalized, real referenced file - must survive.
			Files.write(attrA.getPendingFilePath(), "real-data-a".getBytes());
			attrA.finalizeStorage();
			Path attrAFinalPath = attrA.getFilePath();

			// attrB: real, referenced, but stuck at its pending name and old -
			// simulates a crash between commit() and finalizeStorage(). Must
			// survive: its uid is known, so it's not an orphan.
			Path attrBPendingPath = attrB.getPendingFilePath();
			Files.write(attrBPendingPath, "real-data-b".getBytes());
			this.setOldMtime(attrBPendingPath);

			// True orphans: no database row references these uids at all.
			Path finalFormatOrphan = storagePath.resolve(
				"999_cfg1_orphanattr_%s.data".formatted(UUID.randomUUID()));
			Files.write(finalFormatOrphan, "orphan-final".getBytes());

			Path legacyFormatOrphan = storagePath.resolve("%s.data".formatted(UUID.randomUUID()));
			Files.write(legacyFormatOrphan, "orphan-legacy".getBytes());

			Path recentPendingOrphan = storagePath.resolve(
				".tmp.999_cfg0_orphanattr_%s.data".formatted(UUID.randomUUID()));
			Files.write(recentPendingOrphan, "orphan-recent-pending".getBytes());

			Path oldPendingOrphan = storagePath.resolve(
				".tmp.999_cfg0_orphanattr_%s.data".formatted(UUID.randomUUID()));
			Files.write(oldPendingOrphan, "orphan-old-pending".getBytes());
			this.setOldMtime(oldPendingOrphan);

			PurgeDatabaseTask task = new PurgeDatabaseTask("Test", "tester", 0, 0, 0, 0, 0, (DeviceGroup) null);
			task.setRemoveOrphanFiles(true);
			task.run();

			Assertions.assertEquals(Status.SUCCESS, task.getStatus());
			Assertions.assertTrue(Files.exists(attrAFinalPath), "The real, finalized attribute file must survive");
			Assertions.assertTrue(Files.exists(attrBPendingPath),
				"A real, referenced attribute file must survive even if still pending and old");
			Assertions.assertFalse(Files.exists(finalFormatOrphan), "A final-format orphan file must be deleted");
			Assertions.assertFalse(Files.exists(legacyFormatOrphan), "A legacy-format orphan file must be deleted");
			Assertions.assertTrue(Files.exists(recentPendingOrphan),
				"A recent pending file with no DB row could still be an in-progress snapshot and must survive");
			Assertions.assertFalse(Files.exists(oldPendingOrphan),
				"An old pending file with no DB row at all is a genuine orphan and must be deleted");
		}
	}

	private TaskTest() {
	}
}
