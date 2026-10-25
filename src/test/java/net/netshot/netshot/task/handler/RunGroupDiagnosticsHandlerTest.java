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
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.RunGroupDiagnosticsTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestProperty;

/**
 * Characterization of {@link RunGroupDiagnosticsHandler}, run through the task engine (the child tasks
 * are not run, see {@link TaskRunner}): the diagnostic tasks it creates for the devices of a group or list.
 */
@DatabaseTest
@TestProperty(name = "netshot.tasks.sequentialpollintervalms", value = "500")
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Run group diagnostics task (RunGroupDiagnosticsHandler)")
class RunGroupDiagnosticsHandlerTest {

	private TaskRunner runner;

	private Device first;

	private Device second;

	private StaticDeviceGroup group;

	@BeforeEach
	void createRunner() {
		this.runner = new TaskRunner();
	}

	@BeforeEach
	void createDevices() {
		Domain domain = TestDevices.addDomain("Lab", "Test domain");
		this.first = TestDevices.addFakeCiscoIos(domain, 1);
		this.second = TestDevices.addFakeCiscoIos(domain, 2);
		this.group = TestDevices.addStaticGroup("Routers");
		TestDevices.addMembers(this.group, this.first);
		TestDevices.addMembers(this.group, this.second);
	}

	@AfterEach
	void cleanUp() {
		TaskRunner.cleanDatabase();
	}

	private static List<Long> deviceIds(List<Task> children) {
		return children.stream()
			.map(child -> ((RunDiagnosticsTask) child.spec()).deviceId())
			.toList();
	}

	@Test
	@DisplayName("Creates and schedules one diagnostics task per device of the group")
	void runsOnTheGroup() throws Exception {
		long id = this.runner.run(GroupBasedTask.of(new RunGroupDiagnosticsTask(this.group.id(), null, true), this.group.name(), "Test", "tester"));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(id).status());
		List<Task> children = TaskRunner.children(id);
		Assertions.assertEquals(List.of(this.first.id(), this.second.id()), deviceIds(children));
		Assertions.assertEquals(children.stream().map(Task::id).toList(), this.runner.scheduledChildIds());
		for (Task child : children) {
			RunDiagnosticsTask diagnostics = (RunDiagnosticsTask) child.spec();
			Assertions.assertEquals("Started due to group Routers diagnostics", child.comments());
			Assertions.assertEquals("tester", child.author());
			Assertions.assertTrue(diagnostics.dontCheckCompliance());
		}
	}

	@Test
	@DisplayName("Runs on the devices of a list")
	void runsOnAList() throws Exception {
		long id = this.runner.run(TaskRunner.forDevices(new RunGroupDiagnosticsTask(null, TaskRunner.ids(this.second), false), "Test", "tester"));

		List<Task> children = TaskRunner.children(id);
		Assertions.assertEquals(List.of(this.second.id()), deviceIds(children));
		Assertions.assertEquals("Started due to 1-device diagnostics list", children.get(0).comments());
		Assertions.assertFalse(((RunDiagnosticsTask) children.get(0).spec()).dontCheckCompliance());
	}

	@Test
	@DisplayName("Is cancelled without group nor device")
	void cancelledWithoutTarget() throws Exception {
		long id = this.runner.run(TaskRunner.forDevices(new RunGroupDiagnosticsTask(null, TaskRunner.ids(), false), "Test", "tester"));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.CANCELLED, task.status());
		Assertions.assertTrue(task.log().contains("Neither a device group nor a device list is set"), task.log());
	}
}
