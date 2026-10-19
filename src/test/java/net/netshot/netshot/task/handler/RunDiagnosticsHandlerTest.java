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

import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;

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
		Device device = SimulatedRouter.persist(CONTAINER);
		StaticDeviceGroup group = new StaticDeviceGroup("Routers");
		group.addDevice(device);
		SimpleDiagnostic diagnostic = new SimpleDiagnostic(name, enabled, group, AttributeType.TEXT, "CiscoIOS12",
			"enable", "show version", "(?s).*, Version ([^,]+), RELEASE.*", "$1");
		TaskRunner.persist(group, diagnostic);
		return device;
	}

	private static Map<String, String> results(Device device) {
		return TaskRunner.inSession(session -> session
			.createQuery("select r from DiagnosticResult r join fetch r.diagnostic where r.device.id = :id", DiagnosticResult.class)
			.setParameter("id", device.getId())
			.list()
			.stream()
			.collect(Collectors.toMap(DiagnosticResult::getDiagnosticName, DiagnosticResult::getAsText)));
	}

	@Test
	@DisplayName("Runs the enabled diagnostics of the groups of the device and stores the results")
	void runsTheDiagnostics() throws Exception {
		Device device = this.routerWithDiagnostic("Version", true);

		long id = this.runner.run(new RunDiagnosticsTask(device, "Test", "tester", true));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(RunDiagnosticsTask.class, id).getStatus());
		Assertions.assertEquals(Map.of("Version", "15.5(3)S7b"), results(device));
		Assertions.assertEquals(List.of(), this.runner.submitted(), "No compliance check when disabled");
	}

	@Test
	@DisplayName("Submits the compliance check afterwards")
	void submitsTheComplianceCheck() throws Exception {
		Device device = this.routerWithDiagnostic("Version", true);
		RunDiagnosticsTask task = new RunDiagnosticsTask(device, "Test", "tester", false);
		task.setPriority(7);

		long id = this.runner.run(task);

		List<Task> submitted = this.runner.submitted();
		Assertions.assertEquals(1, submitted.size());
		CheckComplianceTask check = (CheckComplianceTask) submitted.get(0);
		Assertions.assertEquals("Check compliance after device diagnostics", check.getComments());
		Assertions.assertEquals("Auto", check.getAuthor());
		Assertions.assertEquals(7, check.getPriority());
		Assertions.assertEquals(id, check.getParentTaskId());
	}

	@Test
	@DisplayName("Succeeds without connecting when no enabled diagnostic applies, and still submits the compliance check")
	void succeedsWithoutDiagnostic() throws Exception {
		Device device = this.routerWithDiagnostic("Disabled", false);

		long id = this.runner.run(new RunDiagnosticsTask(device, "Test", "tester", false));

		RunDiagnosticsTask task = TaskRunner.load(RunDiagnosticsTask.class, id);
		Assertions.assertEquals(Status.SUCCESS, task.getStatus());
		Assertions.assertTrue(task.getLog().contains("No diagnostic was found that apply to this device."), task.getLog());
		Assertions.assertEquals(Map.of(), results(device));
		Assertions.assertEquals(1, this.runner.submitted().size());
	}

	@Test
	@DisplayName("Is cancelled for a disabled device")
	void cancelledForDisabledDevice() throws Exception {
		Device device = this.routerWithDiagnostic("Version", true);
		TaskRunner.inSession(session -> {
			session.beginTransaction();
			session.get(Device.class, device.getId()).setStatus(Device.Status.DISABLED);
			session.getTransaction().commit();
			return null;
		});

		long id = this.runner.run(new RunDiagnosticsTask(device, "Test", "tester", false));

		RunDiagnosticsTask task = TaskRunner.load(RunDiagnosticsTask.class, id);
		Assertions.assertEquals(Status.CANCELLED, task.getStatus());
		Assertions.assertTrue(task.getLog().contains("The device is not enabled (not in production)."), task.getLog());
		Assertions.assertEquals(List.of(), this.runner.submitted());
	}
}
