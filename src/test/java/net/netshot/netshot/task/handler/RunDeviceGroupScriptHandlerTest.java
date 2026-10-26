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
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.GroupBasedTask;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestProperty;

/**
 * Characterization of {@link RunDeviceGroupScriptHandler}, run through the task engine (the child tasks
 * are not run, see {@link TaskRunner}): the script tasks it creates for the devices of a group or list.
 */
@DatabaseTest
@TestProperty(name = "netshot.tasks.sequentialpollintervalms", value = "500")
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Run device group script task (RunDeviceGroupScriptHandler)")
class RunDeviceGroupScriptHandlerTest {

	private static final String SCRIPT = "function run(cli, device) {}";

	private TaskRunner runner;

	private Device first;

	private Device second;

	private StaticDeviceGroup group;

	private DeviceDriver driver;

	@BeforeEach
	void createRunner() {
		this.runner = new TaskRunner();
	}

	@BeforeEach
	void createDevices() {
		this.driver = TestDrivers.registry().getDriverByName("CiscoIOS12");
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
		return children.stream().map(child -> ((RunDeviceScriptTask) child.spec()).deviceId()).toList();
	}

	@Test
	@DisplayName("Creates and schedules one script task per device of the group, with the script options")
	void runsOnTheGroup() throws Exception {
		Task task = GroupBasedTask.of(new RunDeviceGroupScriptTask(this.group.id(), null, SCRIPT, this.driver.getName(),
			Map.of("name", "value"), true, false, true), this.group.name(), "Test", "tester");

		long id = this.runner.run(task);

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(id).status());
		List<Task> children = TaskRunner.children(id);
		Assertions.assertEquals(List.of(this.first.id(), this.second.id()), deviceIds(children));
		Assertions.assertEquals(children.stream().map(Task::id).toList(), this.runner.scheduledChildIds());
		for (Task child : children) {
			RunDeviceScriptTask script = (RunDeviceScriptTask) child.spec();
			Assertions.assertEquals("Started due to group Routers script task", child.comments());
			Assertions.assertEquals("tester", child.author());
			Assertions.assertEquals(SCRIPT, script.script());
			Assertions.assertEquals("CiscoIOS12", script.deviceDriver());
			Assertions.assertEquals(Map.of("name", "value"), script.userInputValues());
			Assertions.assertTrue(script.runSnapshot());
			Assertions.assertFalse(script.runDiagnostics());
			Assertions.assertTrue(script.checkCompliance());
		}
	}

	@Test
	@DisplayName("Runs on the devices of a list")
	void runsOnAList() throws Exception {
		long id = this.runner.run(TaskRunner.forDevices(new RunDeviceGroupScriptTask(null, TaskRunner.ids(this.second), SCRIPT,
			this.driver.getName(), null, false, false, false), "Test", "tester"));

		List<Task> children = TaskRunner.children(id);
		Assertions.assertEquals(List.of(this.second.id()), deviceIds(children));
		Assertions.assertEquals("Started due to 1-device script list", children.get(0).comments());
	}

	@Test
	@DisplayName("Fails for an unknown driver")
	void failsForUnknownDriver() throws Exception {
		Task task = GroupBasedTask.of(new RunDeviceGroupScriptTask(this.group.id(), null, SCRIPT, "NoSuchDriver", null, false, false, false),
			this.group.name(), "Test", "tester");

		long id = this.runner.run(task);

		Task result = TaskRunner.load(id);
		Assertions.assertEquals(Status.FAILURE, result.status());
		Assertions.assertTrue(result.log().contains("Unknown device driver."), result.log());
		Assertions.assertEquals(List.of(), TaskRunner.children(id));
	}

	@Test
	@DisplayName("Is cancelled without group nor device")
	void cancelledWithoutTarget() throws Exception {
		long id = this.runner.run(TaskRunner.forDevices(new RunDeviceGroupScriptTask(null, TaskRunner.ids(), SCRIPT, this.driver.getName(), null, false, false, false), "Test", "tester"));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.CANCELLED, task.status());
		Assertions.assertTrue(task.log().contains("Neither a device group nor a device list is set"), task.log());
	}
}
