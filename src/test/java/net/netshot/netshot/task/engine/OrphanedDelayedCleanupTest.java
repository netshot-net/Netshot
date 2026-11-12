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

import java.util.List;

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
import net.netshot.netshot.task.type.GroupBasedTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;

@DatabaseTest
@DisplayName("Orphaned DELAYED task cleanup (TaskManager, DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
class OrphanedDelayedCleanupTest {

	Domain domain;
	Device device;

	@BeforeEach
	void createData() {
		domain = TestDevices.addDomain("Test domain", "Fake domain for tests");
		device = TestDevices.addFakeCiscoIos(domain, 1);
	}

	@AfterEach
	void cleanUpData() {
		TestTasks.deleteAll();
		TestDevices.deleteAll();
	}

	private long persistDelayed(Long parentTaskId) {
		return TestTasks.add(TakeSnapshotTask.of(device, "test", "tester", false, true, true).toBuilder()
			.status(Status.DELAYED)
			.parentTaskId(parentTaskId)
			.build()).id();
	}

	@Test
	@DisplayName("Cancels DELAYED tasks with no parent, leaves DELAYED children of a live parent alone")
	void cancelsOnlyParentlessDelayedTasks() {
		long orphanId = this.persistDelayed(null);
		Task parent = TestTasks.add(GroupBasedTask.of(new TakeGroupSnapshotTask(null, List.of(device.id()), -1, true, true),
			null, "test", "tester"));
		long childId = this.persistDelayed(parent.id());

		TestWiring.get().taskManager().cancelOrphanedDelayedTasks();

		Assertions.assertEquals(Status.CANCELLED, TestTasks.find(orphanId).status(),
			"Parentless DELAYED task should be cancelled");
		Assertions.assertEquals(Status.DELAYED, TestTasks.find(childId).status(),
			"DELAYED child of a still-existing parent should be left alone");
	}
}
