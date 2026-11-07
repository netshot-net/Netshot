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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import net.netshot.netshot.diagnostic.DiagnosticRepository;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResult;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestWiring;

/**
 * Characterization of {@link RunDiagnosticsHandler}, run through the task engine against a Cisco IOS
 * device simulator: the diagnostic results it stores, and the follow-up compliance check.
 */
@DatabaseTest
@SimulatorTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Run diagnostics task (RunDiagnosticsHandler)")
class RunDiagnosticsHandlerTest {

	@Container
	private static final GenericContainer<?> CONTAINER = DeviceSimulators.ssh("Cisco_IOS");

	private TaskRunner runner;

	@BeforeEach
	void createRunner() {
		this.runner = new TaskRunner();
	}

	@AfterEach
	void cleanUp() {
		TaskRunner.cleanDatabase();
	}

	/**
	 * Saves the simulated router in a group, with a diagnostic on that group.
	 *
	 * @param name the name of the diagnostic
	 * @param enabled whether the diagnostic is enabled
	 * @return the saved device
	 */
	private Device routerWithDiagnostic(String name, boolean enabled) {
		return this.routerWithDiagnostic(name, enabled, CONTAINER.getMappedPort(22));
	}

	/**
	 * Saves the simulated router, reached on the given SSH port, in a group, with a diagnostic on that group.
	 *
	 * @param name the name of the diagnostic
	 * @param enabled whether the diagnostic is enabled
	 * @param port the SSH port to use (the mapped port of the container, or another one to fail)
	 * @return the saved device
	 */
	private Device routerWithDiagnostic(String name, boolean enabled, int port) {
		Device device = SimulatedRouter.persist(CONTAINER, port);
		StaticDeviceGroup group = TestDevices.addStaticGroup("Routers");
		TestDevices.addMembers(group, device);
		SimpleDiagnostic diagnostic = new SimpleDiagnostic(0, 0, null, name, enabled, group.id(), AttributeType.TEXT, "CiscoIOS12",
			"enable", "show version", "(?s).*, Version ([^,]+), RELEASE.*", "$1");
		TestWiring.get().transactions().run(dsl -> new DiagnosticRepository().add(dsl, diagnostic));
		return device;
	}

	private static Map<String, String> results(Device device) {
		return TestDevices.diagnosticResults(device.id())
			.stream()
			.collect(Collectors.toMap(DiagnosticResult::diagnosticName, DiagnosticResult::getAsText));
	}

	@Test
	@DisplayName("Runs the enabled diagnostics of the groups of the device and stores the results")
	void runsTheDiagnostics() throws Exception {
		Device device = this.routerWithDiagnostic("Version", true);

		long id = this.runner.run(RunDiagnosticsTask.of(device, "Test", "tester", true));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(id).status());
		Assertions.assertEquals(Map.of("Version", "15.5(3)S7b"), results(device));
		Assertions.assertEquals(List.of(), this.runner.submitted(), "No compliance check when disabled");
	}

	@Test
	@DisplayName("Submits the compliance check afterwards")
	void submitsTheComplianceCheck() throws Exception {
		Device device = this.routerWithDiagnostic("Version", true);
		Task task = RunDiagnosticsTask.of(device, "Test", "tester", false).toBuilder().priority(7).build();

		long id = this.runner.run(task);

		List<Task> submitted = this.runner.submitted();
		Assertions.assertEquals(1, submitted.size());
		Task check = submitted.get(0);
		Assertions.assertInstanceOf(CheckComplianceTask.class, check.spec());
		Assertions.assertEquals("Check compliance after device diagnostics", check.comments());
		Assertions.assertEquals("Auto", check.author());
		Assertions.assertEquals(7, check.priority());
		Assertions.assertEquals(id, check.parentTaskId());
	}

	@Test
	@DisplayName("Succeeds without connecting when no enabled diagnostic applies, and still submits the compliance check")
	void succeedsWithoutDiagnostic() throws Exception {
		Device device = this.routerWithDiagnostic("Disabled", false);

		long id = this.runner.run(RunDiagnosticsTask.of(device, "Test", "tester", false));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.SUCCESS, task.status());
		Assertions.assertTrue(task.log().contains("No diagnostic was found that apply to this device."), task.log());
		Assertions.assertEquals(Map.of(), results(device));
		Assertions.assertEquals(1, this.runner.submitted().size());
	}

	@Test
	@DisplayName("Fails when the device can't be reached, without follow-up task")
	void failsWhenUnreachable() throws Exception {
		Device device = this.routerWithDiagnostic("Version", true, 1);

		long id = this.runner.run(RunDiagnosticsTask.of(device, "Test", "tester", false));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.FAILURE, task.status());
		Assertions.assertTrue(task.log().contains("Error while executing the diagnostics"), task.log());
		Assertions.assertEquals(Map.of(), results(device));
		Assertions.assertEquals(List.of(), this.runner.submitted());
	}

	@Test
	@DisplayName("Is cancelled for a disabled device")
	void cancelledForDisabledDevice() throws Exception {
		Device device = this.routerWithDiagnostic("Version", true);
		TestDevices.save(TestDevices.find(device.id()).toBuilder().status(Device.Status.DISABLED).build());

		long id = this.runner.run(RunDiagnosticsTask.of(device, "Test", "tester", false));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.CANCELLED, task.status());
		Assertions.assertTrue(task.log().contains("The device is not enabled (not in production)."), task.log());
		Assertions.assertEquals(List.of(), this.runner.submitted());
	}
}
