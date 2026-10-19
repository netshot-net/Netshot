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

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestProperty;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

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
		Domain domain = new Domain("Lab", "Test domain", null, null);
		this.recent = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1);
		this.recent.setChangeDate(new Date());
		this.old = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 2);
		this.old.setChangeDate(hoursAgo(48));
		this.group = new StaticDeviceGroup("Routers");
		this.group.addDevice(this.recent);
		this.group.addDevice(this.old);
		TaskRunner.persist(domain, this.recent, this.old, this.group);
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
		return TaskRunner.inSession(session -> children.stream()
			.map(child -> session.get(TakeSnapshotTask.class, child.getId()).getDevice().getId())
			.toList());
	}

	@Test
	@DisplayName("Creates and schedules one snapshot task per device of the group")
	void snapshotsTheGroup() throws Exception {
		long id = this.runner.run(new TakeGroupSnapshotTask(this.group, "Test", "tester", -1, true, false));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(TakeGroupSnapshotTask.class, id).getStatus());
		List<Task> children = TaskRunner.children(id);
		Assertions.assertEquals(List.of(this.recent.getId(), this.old.getId()), deviceIds(children));
		Assertions.assertEquals(children.stream().map(Task::getId).toList(), this.runner.scheduledChildIds());
		for (Task child : children) {
			TakeSnapshotTask snapshot = (TakeSnapshotTask) child;
			Assertions.assertEquals("Started due to group Routers snapshot", snapshot.getComments());
			Assertions.assertEquals("tester", snapshot.getAuthor());
			Assertions.assertTrue(snapshot.isDontRunDiagnostics());
			Assertions.assertFalse(snapshot.isDontCheckCompliance());
			Assertions.assertFalse(snapshot.isAutomatic());
		}
	}

	@Test
	@DisplayName("Ignores the devices changed less than the given number of hours ago")
	void ignoresRecentlyChangedDevices() throws Exception {
		long id = this.runner.run(new TakeGroupSnapshotTask(this.group, "Test", "tester", 24, false, false));

		Assertions.assertEquals(List.of(this.old.getId()), deviceIds(TaskRunner.children(id)));
		Assertions.assertTrue(TaskRunner.load(TakeGroupSnapshotTask.class, id).getLog().contains("Ignoring device " + this.recent.getName()));
	}

	@Test
	@DisplayName("Snapshots the devices of a list")
	void snapshotsAList() throws Exception {
		long id = this.runner.run(new TakeGroupSnapshotTask(List.of(this.old), "Test", "tester", -1, false, false));

		List<Task> children = TaskRunner.children(id);
		Assertions.assertEquals(List.of(this.old.getId()), deviceIds(children));
		Assertions.assertEquals("Started due to 1-device snapshot list", children.get(0).getComments());
	}

	@Test
	@DisplayName("Succeeds when child tasks fail (parallel mode)")
	void succeedsWhenChildrenFail() throws Exception {
		this.runner.setChildStatus(Status.FAILURE);

		long id = this.runner.run(new TakeGroupSnapshotTask(this.group, "Test", "tester", -1, false, false));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(TakeGroupSnapshotTask.class, id).getStatus());
	}

	/**
	 * Resuming from the child tasks already created (after a restart) currently fails: the children are
	 * reloaded in a closed session, and describing the device of a child to schedule it reads an
	 * uninitialized proxy. The exception escapes the task engine, which leaves the task running.
	 * Pinned as is; the expected behavior is to schedule the existing children without creating others.
	 */
	@Test
	@DisplayName("Resuming from the child tasks already created fails (lazy device of the children)")
	void resumesFromExistingChildren() throws Exception {
		TakeGroupSnapshotTask task = new TakeGroupSnapshotTask(this.group, "Test", "tester", -1, false, false);
		long id = TaskRunner.save(task);
		TakeSnapshotTask existing = new TakeSnapshotTask(this.old, "Earlier run", "tester", false, false, false);
		existing.setParentTaskId(id);
		existing.setChildOrder(0);
		existing.setStatus(Status.DELAYED);
		TaskRunner.persist(existing);

		Assertions.assertThrows(LazyInitializationException.class, () -> this.runner.run(id));

		Assertions.assertEquals(Status.RUNNING, TaskRunner.load(TakeGroupSnapshotTask.class, id).getStatus());
		Assertions.assertEquals(List.of(existing.getId()), TaskRunner.children(id).stream().map(Task::getId).toList(),
			"No child task is created for the other devices");
		Assertions.assertEquals(List.of(), this.runner.scheduledChildIds());
	}

	@Test
	@DisplayName("Is cancelled without group nor device")
	void cancelledWithoutTarget() throws Exception {
		long id = this.runner.run(new TakeGroupSnapshotTask(List.of(), "Test", "tester", -1, false, false));

		TakeGroupSnapshotTask task = TaskRunner.load(TakeGroupSnapshotTask.class, id);
		Assertions.assertEquals(Status.CANCELLED, task.getStatus());
		Assertions.assertTrue(task.getLog().contains("Neither a device group nor a device list is set"), task.getLog());
	}
}
