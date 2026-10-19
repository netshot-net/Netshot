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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.hibernate.Session;

import net.netshot.netshot.FakeDeviceFactory;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.TestDrivers;
import net.netshot.netshot.WithDatabaseTest;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskDeviceListMember;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@DisplayName("Group task device list / parent-child tracking (DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
public class DeviceListAndParentChildTest extends WithDatabaseTest {

	Domain domain;
	Device device1;
	Device device2;

	@BeforeAll
	void initDb() throws Exception {
		Netshot.readConfig();
		Properties config = getDatabaseConfig("tasktest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "WARN");
		TestConfig.init(config);
		Database.update();
		Wiring.init(TestDrivers.registry());
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
	 * @param task the task to persist
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

	private final ChildTaskOrchestrator orchestrator = new ChildTaskOrchestrator(child -> {
	});

	@Test
	@DisplayName("preCreateChildren persists every child upfront, in order, all DELAYED")
	void preCreateChildrenPersistsAllAsDelayed() throws Exception {
		TakeGroupSnapshotTask parent = new TakeGroupSnapshotTask(
			List.of(device1, device2), "test", "tester", -1, true, true);
		parent.setPriority(7);
		this.persist(parent);

		List<Task> children = new ArrayList<>(List.of(
			new TakeSnapshotTask(device1, "child", "tester", false, true, true),
			new TakeSnapshotTask(device2, "child", "tester", false, true, true)));
		this.orchestrator.preCreateChildren(parent, children);

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
	void reloadExistingChildrenResumesAfterRestart() throws Exception {
		TakeGroupSnapshotTask parent = new TakeGroupSnapshotTask(
			List.of(device1, device2), "test", "tester", -1, true, true);
		this.persist(parent);

		List<Task> created = new ArrayList<>(List.of(
			new TakeSnapshotTask(device1, "child", "tester", false, true, true),
			new TakeSnapshotTask(device2, "child", "tester", false, true, true)));
		this.orchestrator.preCreateChildren(parent, created);

		// Simulate a Netshot restart: a fresh Task instance for the same id, no memory of `created`.
		try (Session session = Database.getSession()) {
			Task reloadedParent = session.get(Task.class, parent.getId());
			List<Task> resumed = this.orchestrator.reloadExistingChildren(reloadedParent);
			Assertions.assertEquals(2, resumed.size());
			Assertions.assertEquals(created.get(0).getId(), resumed.get(0).getId());
			Assertions.assertEquals(created.get(1).getId(), resumed.get(1).getId());
			Assertions.assertEquals(Status.DELAYED, resumed.get(0).getStatus());
		}
	}

	@Test
	@DisplayName("cancelRemainingDelayedChildren only cancels still-DELAYED children from the given position")
	void cancelRemainingDelayedChildrenOnlyTouchesDelayedFromIndex() throws Exception {
		TakeGroupSnapshotTask parent = new TakeGroupSnapshotTask(
			List.of(device1, device2), "test", "tester", -1, true, true);
		this.persist(parent);

		List<Task> children = new ArrayList<>(List.of(
			new TakeSnapshotTask(device1, "child", "tester", false, true, true),
			new TakeSnapshotTask(device2, "child", "tester", false, true, true)));
		this.orchestrator.preCreateChildren(parent, children);

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

		this.orchestrator.cancelRemainingDelayedChildren(parent, children, 0);

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
