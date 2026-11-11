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
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.type.GroupBasedTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;

@DatabaseTest
@DisplayName("Group task device list / parent-child tracking (DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
class DeviceListAndParentChildTest {

	Domain domain;
	Device device1;
	Device device2;

	@BeforeEach
	void createData() {
		domain = TestDevices.addDomain("Test domain", "Fake domain for tests");
		device1 = TestDevices.addFakeCiscoIos(domain, 1);
		device2 = TestDevices.addFakeCiscoIos(domain, 2);
	}

	@AfterEach
	void cleanUpData() {
		TestTasks.deleteAll();
		TestDevices.deleteAll();
	}

	// A group snapshot task on a list of devices (not stored)
	private static Task groupSnapshot(Device... devices) {
		List<Long> ids = new ArrayList<>();
		for (Device device : devices) {
			ids.add(device.id());
		}
		return GroupBasedTask.of(new TakeGroupSnapshotTask(null, ids, -1, true, true), null, "test", "tester");
	}

	@Test
	@DisplayName("Device list is persisted in order and reloads the correct concrete subtype")
	void deviceListPersistsInOrder() {
		Task task = TestTasks.add(groupSnapshot(device1, device2));

		List<long[]> members = TestTasks.deviceListMembers(task.id());
		Assertions.assertEquals(2, members.size(), "Expected one member per listed device");
		Assertions.assertEquals(0, members.get(0)[0]);
		Assertions.assertEquals(device1.id(), members.get(0)[1]);
		Assertions.assertEquals(1, members.get(1)[0]);
		Assertions.assertEquals(device2.id(), members.get(1)[1]);

		Task reloaded = TestTasks.find(task.id());
		Assertions.assertInstanceOf(TakeGroupSnapshotTask.class, reloaded.spec(),
			"Reloaded task should keep its concrete subtype (dtype discriminator)");
		Assertions.assertEquals(-1, ((TakeGroupSnapshotTask) reloaded.spec()).limitToOutofdateDeviceHours(),
			"Subclass-specific attribute should round-trip through the shared JSON attributes column");
		Assertions.assertEquals(List.of(device1.id(), device2.id()), ((TakeGroupSnapshotTask) reloaded.spec()).deviceIds());
	}

	@Test
	@DisplayName("Deleting a device cascades removal from its task's device list")
	void deviceDeletionCascadesFromDeviceList() {
		Task task = TestTasks.add(groupSnapshot(device2));

		TestDevices.delete(device2.id());

		Assertions.assertEquals(0, TestTasks.deviceListMembers(task.id()).size(),
			"Device-list membership should be cascade-deleted with the device");
	}

	@Test
	@DisplayName("Parent/child linkage: children are stamped, survive parent deletion with parentTaskId cleared")
	void parentChildLinkageAndCascade() {
		Task parent = TestTasks.add(groupSnapshot(device1));

		Task child = TestTasks.add(TakeSnapshotTask.of(device1, "child", "tester", false, true, true).toBuilder()
			.parentTaskId(parent.id())
			.childOrder(0)
			.build());

		Assertions.assertEquals(parent.id(), TestTasks.find(child.id()).parentTaskId());
		Assertions.assertEquals(1, TestTasks.children(parent.id()).size());

		TestTasks.delete(parent.id());

		Task reloadedChild = TestTasks.find(child.id());
		Assertions.assertNotNull(reloadedChild, "Child task should survive its parent's deletion");
		Assertions.assertNull(reloadedChild.parentTaskId(),
			"Child's parentTaskId should be cleared (SET NULL), not cascade-deleted");
	}

	/**
	 * Gets an orchestrator on the wiring of the class (built once the test instance exists).
	 *
	 * @return the orchestrator
	 */
	private ChildTaskOrchestrator orchestrator() {
		return new ChildTaskOrchestrator(TestWiring.get().transactions(), _ -> {
		});
	}

	@Test
	@DisplayName("preCreateChildren persists every child upfront, in order, all DELAYED")
	void preCreateChildrenPersistsAllAsDelayed() throws Exception {
		Task parent = TestTasks.add(groupSnapshot(device1, device2).toBuilder().priority(7).build());

		List<Task> children = this.orchestrator().preCreateChildren(new TaskRun(parent), List.of(
			TakeSnapshotTask.of(device1, "child", "tester", false, true, true),
			TakeSnapshotTask.of(device2, "child", "tester", false, true, true)));

		Assertions.assertTrue(children.get(0).id() != 0, "Pre-creation should assign a real id");
		Assertions.assertTrue(children.get(1).id() != 0, "Pre-creation should assign a real id");

		List<Task> reloaded = TestTasks.children(parent.id());
		Assertions.assertEquals(2, reloaded.size());
		Assertions.assertEquals(0, reloaded.get(0).childOrder());
		Assertions.assertEquals(Status.DELAYED, reloaded.get(0).status());
		Assertions.assertEquals(7, reloaded.get(0).priority());
		Assertions.assertEquals(1, reloaded.get(1).childOrder());
		Assertions.assertEquals(Status.DELAYED, reloaded.get(1).status());
	}

	@Test
	@DisplayName("reloadExistingChildren resumes from previously pre-created children (e.g. after a restart)")
	void reloadExistingChildrenResumesAfterRestart() throws Exception {
		Task parent = TestTasks.add(groupSnapshot(device1, device2));

		List<Task> created = this.orchestrator().preCreateChildren(new TaskRun(parent), List.of(
			TakeSnapshotTask.of(device1, "child", "tester", false, true, true),
			TakeSnapshotTask.of(device2, "child", "tester", false, true, true)));

		// Simulate a Netshot restart: a fresh run of the same task, no memory of `created`.
		List<Task> resumed = this.orchestrator().reloadExistingChildren(new TaskRun(TestTasks.find(parent.id())));
		Assertions.assertEquals(2, resumed.size());
		Assertions.assertEquals(created.get(0).id(), resumed.get(0).id());
		Assertions.assertEquals(created.get(1).id(), resumed.get(1).id());
		Assertions.assertEquals(Status.DELAYED, resumed.get(0).status());
	}

	@Test
	@DisplayName("cancelRemainingDelayedChildren only cancels still-DELAYED children from the given position")
	void cancelRemainingDelayedChildrenOnlyTouchesDelayedFromIndex() throws Exception {
		Task parent = TestTasks.add(groupSnapshot(device1, device2));
		TaskRun run = new TaskRun(parent);

		List<Task> children = this.orchestrator().preCreateChildren(run, List.of(
			TakeSnapshotTask.of(device1, "child", "tester", false, true, true),
			TakeSnapshotTask.of(device2, "child", "tester", false, true, true)));

		// Device 0 already completed (as orchestrateChildren would have observed by reaching
		// out to the DB) -- its in-memory Status must reflect that, since cancelRemainingDelayedChildren
		// only bulk-cancels ids whose in-memory Status is still DELAYED.
		TestTasks.setStatus(children.get(0).id(), Status.SUCCESS);
		children.set(0, children.get(0).withStatus(Status.SUCCESS));

		this.orchestrator().cancelRemainingDelayedChildren(run, children, 0);

		Assertions.assertEquals(Status.SUCCESS, TestTasks.find(children.get(0).id()).status(),
			"Already-completed child should be untouched");
		Assertions.assertEquals(Status.CANCELLED, TestTasks.find(children.get(1).id()).status(),
			"Still-DELAYED child should be cancelled");
	}
}
