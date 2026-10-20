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

import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestProperty;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

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
		Domain domain = new Domain("Lab", "Test domain", null, null);
		this.first = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1);
		this.second = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 2);
		this.group = new StaticDeviceGroup("Routers");
		this.group.addDevice(this.first);
		this.group.addDevice(this.second);
		TaskRunner.persist(domain, this.first, this.second, this.group);
	}

	@AfterEach
	void cleanUp() {
		TaskRunner.cleanDatabase();
	}

	private static List<Long> deviceIds(List<Task> children) {
		return TaskRunner.inSession(session -> children.stream()
			.map(child -> session.get(RunDeviceScriptTask.class, child.getId()).getDevice().getId())
			.toList());
	}

	@Test
	@DisplayName("Creates and schedules one script task per device of the group, with the script options")
	void runsOnTheGroup() throws Exception {
		RunDeviceGroupScriptTask task = new RunDeviceGroupScriptTask(this.group, SCRIPT, this.driver, "Test", "tester");
		task.setUserInputValues(Map.of("name", "value"));
		task.setRunSnapshot(true);
		task.setRunDiagnostics(false);
		task.setCheckCompliance(true);

		long id = this.runner.run(task);

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(RunDeviceGroupScriptTask.class, id).getStatus());
		List<Task> children = TaskRunner.children(id);
		Assertions.assertEquals(List.of(this.first.getId(), this.second.getId()), deviceIds(children));
		Assertions.assertEquals(children.stream().map(Task::getId).toList(), this.runner.scheduledChildIds());
		TaskRunner.inSession(session -> {
			for (Task child : children) {
				RunDeviceScriptTask script = session.get(RunDeviceScriptTask.class, child.getId());
				Assertions.assertEquals("Started due to group Routers script task", script.getComments());
				Assertions.assertEquals("tester", script.getAuthor());
				Assertions.assertEquals(SCRIPT, script.getScript());
				Assertions.assertEquals("CiscoIOS12", script.getDeviceDriver());
				Assertions.assertEquals(Map.of("name", "value"), script.getUserInputValues());
				Assertions.assertTrue(script.isRunSnapshot());
				Assertions.assertFalse(script.isRunDiagnostics());
				Assertions.assertTrue(script.isCheckCompliance());
			}
			return null;
		});
	}

	@Test
	@DisplayName("Runs on the devices of a list")
	void runsOnAList() throws Exception {
		long id = this.runner.run(new RunDeviceGroupScriptTask(List.of(this.second), SCRIPT, this.driver, "Test", "tester"));

		List<Task> children = TaskRunner.children(id);
		Assertions.assertEquals(List.of(this.second.getId()), deviceIds(children));
		Assertions.assertEquals("Started due to 1-device script list", children.get(0).getComments());
	}

	@Test
	@DisplayName("Fails for an unknown driver")
	void failsForUnknownDriver() throws Exception {
		RunDeviceGroupScriptTask task = new RunDeviceGroupScriptTask(this.group, SCRIPT, this.driver, "Test", "tester");
		task.setDeviceDriver("NoSuchDriver");

		long id = this.runner.run(task);

		RunDeviceGroupScriptTask result = TaskRunner.load(RunDeviceGroupScriptTask.class, id);
		Assertions.assertEquals(Status.FAILURE, result.getStatus());
		Assertions.assertTrue(result.getLog().contains("Unknown device driver."), result.getLog());
		Assertions.assertEquals(List.of(), TaskRunner.children(id));
	}

	@Test
	@DisplayName("Is cancelled without group nor device")
	void cancelledWithoutTarget() throws Exception {
		long id = this.runner.run(new RunDeviceGroupScriptTask(List.of(), SCRIPT, this.driver, "Test", "tester"));

		RunDeviceGroupScriptTask task = TaskRunner.load(RunDeviceGroupScriptTask.class, id);
		Assertions.assertEquals(Status.CANCELLED, task.getStatus());
		Assertions.assertTrue(task.getLog().contains("Neither a device group nor a device list is set"), task.getLog());
	}
}
