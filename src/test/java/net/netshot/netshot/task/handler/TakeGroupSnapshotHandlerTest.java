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
package net.netshot.netshot.task.handler;

import java.util.Calendar;
import java.util.Date;
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
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.GroupBasedTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestProperty;
import net.netshot.netshot.testing.TestTasks;

/**
 * Characterization of {@link TakeGroupSnapshotHandler}, run through the task engine (the child tasks are
 * not run, see {@link TaskRunner}): the snapshot tasks it creates for the devices of a group or list.
 */
@DatabaseTest
@TestProperty(name = "netshot.tasks.sequentialpollintervalms", value = "500")
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Take group snapshot task (TakeGroupSnapshotHandler)")
class TakeGroupSnapshotHandlerTest {

	private TaskRunner runner;

	private Device recent;

	private Device old;

	private StaticDeviceGroup group;

	@BeforeEach
	void createRunner() {
		this.runner = new TaskRunner();
	}

	@BeforeEach
	void createDevices() {
		Domain domain = TestDevices.addDomain("Lab", "Test domain");
		this.recent = TestDevices.addFakeCiscoIos(domain, 1);
		TestDevices.setChangeDate(this.recent.id(), new Date().toInstant());
		this.old = TestDevices.addFakeCiscoIos(domain, 2);
		TestDevices.setChangeDate(this.old.id(), hoursAgo(48).toInstant());
		this.group = TestDevices.addStaticGroup("Routers");
		TestDevices.addMembers(this.group, this.recent);
		TestDevices.addMembers(this.group, this.old);
	}

	@AfterEach
	void cleanUp() {
		TaskRunner.cleanDatabase();
	}

	private static Date hoursAgo(int hours) {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.HOUR, -hours);
		return calendar.getTime();
	}

	private static List<Long> deviceIds(List<Task> children) {
		return children.stream()
			.map(child -> ((TakeSnapshotTask) child.spec()).deviceId())
			.toList();
	}

	@Test
	@DisplayName("Creates and schedules one snapshot task per device of the group")
	void snapshotsTheGroup() throws Exception {
		long id = this.runner.run(GroupBasedTask.of(new TakeGroupSnapshotTask(this.group.id(), null, -1, true, false), this.group.name(), "Test", "tester"));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(id).status());
		List<Task> children = TaskRunner.children(id);
		Assertions.assertEquals(List.of(this.recent.id(), this.old.id()), deviceIds(children));
		Assertions.assertEquals(children.stream().map(Task::id).toList(), this.runner.scheduledChildIds());
		for (Task child : children) {
			TakeSnapshotTask snapshot = (TakeSnapshotTask) child.spec();
			Assertions.assertEquals("Started due to group Routers snapshot", child.comments());
			Assertions.assertEquals("tester", child.author());
			Assertions.assertTrue(snapshot.dontRunDiagnostics());
			Assertions.assertFalse(snapshot.dontCheckCompliance());
			Assertions.assertFalse(snapshot.automatic());
		}
	}

	@Test
	@DisplayName("Ignores the devices changed less than the given number of hours ago")
	void ignoresRecentlyChangedDevices() throws Exception {
		long id = this.runner.run(GroupBasedTask.of(new TakeGroupSnapshotTask(this.group.id(), null, 24, false, false), this.group.name(), "Test", "tester"));

		Assertions.assertEquals(List.of(this.old.id()), deviceIds(TaskRunner.children(id)));
		Assertions.assertTrue(TaskRunner.load(id).log().contains("Ignoring device " + this.recent.name()));
	}

	@Test
	@DisplayName("Snapshots the devices of a list")
	void snapshotsAList() throws Exception {
		long id = this.runner.run(TaskRunner.forDevices(new TakeGroupSnapshotTask(null, TaskRunner.ids(this.old), -1, false, false), "Test", "tester"));

		List<Task> children = TaskRunner.children(id);
		Assertions.assertEquals(List.of(this.old.id()), deviceIds(children));
		Assertions.assertEquals("Started due to 1-device snapshot list", children.get(0).comments());
	}

	@Test
	@DisplayName("Succeeds when child tasks fail (parallel mode)")
	void succeedsWhenChildrenFail() throws Exception {
		this.runner.setChildStatus(Status.FAILURE);

		long id = this.runner.run(GroupBasedTask.of(new TakeGroupSnapshotTask(this.group.id(), null, -1, false, false), this.group.name(), "Test", "tester"));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(id).status());
	}

	/**
	 * Resuming from the child tasks already created (after a restart): the existing children are scheduled,
	 * no other is created. (With the former Hibernate entities, this failed on a lazy device of the children,
	 * and left the task running.)
	 */
	@Test
	@DisplayName("Resumes from the child tasks already created")
	void resumesFromExistingChildren() throws Exception {
		Task task = GroupBasedTask.of(new TakeGroupSnapshotTask(this.group.id(), null, -1, false, false), this.group.name(),
			"Test", "tester");
		long id = TaskRunner.save(task);
		Task existing = TestTasks.add(TakeSnapshotTask.of(this.old, "Earlier run", "tester", false, false, false).toBuilder()
			.parentTaskId(id)
			.childOrder(0)
			.status(Status.DELAYED)
			.build());

		this.runner.run(id);

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(id).status());
		Assertions.assertEquals(List.of(existing.id()), TaskRunner.children(id).stream().map(Task::id).toList(),
			"No child task is created for the other devices");
		Assertions.assertEquals(List.of(existing.id()), this.runner.scheduledChildIds());
	}

	@Test
	@DisplayName("Is cancelled without group nor device")
	void cancelledWithoutTarget() throws Exception {
		long id = this.runner.run(TaskRunner.forDevices(new TakeGroupSnapshotTask(null, TaskRunner.ids(), -1, false, false), "Test", "tester"));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.CANCELLED, task.status());
		Assertions.assertTrue(task.log().contains("Neither a device group nor a device list is set"), task.log());
	}
}
