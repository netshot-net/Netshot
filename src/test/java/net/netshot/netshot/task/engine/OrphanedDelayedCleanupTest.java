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

import java.util.List;

import org.hibernate.Session;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.Task;
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
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

@DatabaseTest
@DisplayName("Orphaned DELAYED task cleanup (TaskManager, DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
public class OrphanedDelayedCleanupTest {

	Domain domain;
	Device device;

	@BeforeAll
	void initDb() throws Exception {
		Netshot.readConfig();
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
