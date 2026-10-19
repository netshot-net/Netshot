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

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestDrivers;

/**
 * Characterization of {@link RunDeviceScriptHandler}, run through the task engine against a Cisco IOS
 * device simulator: what a device script changes, and the follow-up task it submits.
 */
@DatabaseTest
@SimulatorTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Run device script task (RunDeviceScriptHandler)")
class RunDeviceScriptHandlerTest {

	@Container
	private static final GenericContainer<?> CONTAINER = DeviceSimulators.ssh("Cisco_IOS");

	/** A script reading the CLI and changing the device. */
	private static final String SCRIPT = """
		function run(cli, device) {
			cli.macro("enable");
			const version = cli.command("show version");
			device.set("comments", version.includes("CSR1000V") ? "CSR seen" : "unexpected");
		}
		""";

	private TaskRunner runner;

	private DeviceDriver driver;

	@BeforeEach
	void createRunner() {
		this.runner = new TaskRunner();
		this.driver = TestDrivers.registry().getDriverByName("CiscoIOS12");
	}

	@AfterEach
	void cleanUp() {
		TaskRunner.cleanDatabase();
	}

	private RunDeviceScriptTask script(Device device, String code) {
		RunDeviceScriptTask task = new RunDeviceScriptTask(device, code, this.driver, "Test", "tester");
		task.setUserInputValues(new HashMap<>());
		return task;
	}

	@Test
	@DisplayName("Runs the script on the device and saves the device")
	void runsTheScript() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);

		long id = this.runner.run(this.script(device, SCRIPT));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(RunDeviceScriptTask.class, id).getStatus());
		Assertions.assertEquals("CSR seen", TaskRunner.inSession(session -> session.get(Device.class, device.getId()).getComments()));
		Assertions.assertEquals(List.of(), this.runner.submitted(), "No follow-up task by default");
	}

	@Test
	@DisplayName("Submits a snapshot afterwards when asked, passing the diagnostics and compliance options")
	void submitsTheSnapshot() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		RunDeviceScriptTask task = this.script(device, SCRIPT);
		task.setRunSnapshot(true);
		task.setRunDiagnostics(false);
		task.setCheckCompliance(true);

		long id = this.runner.run(task);

		List<Task> submitted = this.runner.submitted();
		Assertions.assertEquals(1, submitted.size());
		TakeSnapshotTask snapshot = (TakeSnapshotTask) submitted.get(0);
		Assertions.assertEquals("Snapshot after device script execution", snapshot.getComments());
		Assertions.assertEquals("Auto", snapshot.getAuthor());
		Assertions.assertFalse(snapshot.isAutomatic());
		Assertions.assertTrue(snapshot.isDontRunDiagnostics());
		Assertions.assertFalse(snapshot.isDontCheckCompliance());
		Assertions.assertEquals(id, snapshot.getParentTaskId());
	}

	@Test
	@DisplayName("Without snapshot, submits the diagnostics afterwards when asked")
	void submitsTheDiagnostics() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		RunDeviceScriptTask task = this.script(device, SCRIPT);
		task.setRunDiagnostics(true);

		this.runner.run(task);

		List<Task> submitted = this.runner.submitted();
		Assertions.assertEquals(1, submitted.size());
		RunDiagnosticsTask diagnostics = (RunDiagnosticsTask) submitted.get(0);
		Assertions.assertEquals("Run diagnostics after device script execution", diagnostics.getComments());
		Assertions.assertTrue(diagnostics.isDontCheckCompliance());
	}

	@Test
	@DisplayName("Without snapshot nor diagnostics, submits the compliance check afterwards when asked")
	void submitsTheComplianceCheck() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		RunDeviceScriptTask task = this.script(device, SCRIPT);
		task.setCheckCompliance(true);

		this.runner.run(task);

		List<Task> submitted = this.runner.submitted();
		Assertions.assertEquals(1, submitted.size());
		Assertions.assertEquals("Check compliance after device script execution", ((CheckComplianceTask) submitted.get(0)).getComments());
	}

	@Test
	@DisplayName("Fails when the script fails, without follow-up task")
	void failsWhenTheScriptFails() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		RunDeviceScriptTask task = this.script(device, "function run(cli, device) { throw 'Broken script'; }");
		task.setRunSnapshot(true);

		long id = this.runner.run(task);

		RunDeviceScriptTask result = TaskRunner.load(RunDeviceScriptTask.class, id);
		Assertions.assertEquals(Status.FAILURE, result.getStatus());
		Assertions.assertTrue(result.getLog().contains("Error while running the script"), result.getLog());
		Assertions.assertEquals(List.of(), this.runner.submitted());
	}

	@Test
	@DisplayName("Is cancelled when the script is for another driver")
	void cancelledForAnotherDriver() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		RunDeviceScriptTask task = this.script(device, SCRIPT);
		task.setDeviceDriver("JuniperJunos");

		long id = this.runner.run(task);

		RunDeviceScriptTask result = TaskRunner.load(RunDeviceScriptTask.class, id);
		Assertions.assertEquals(Status.CANCELLED, result.getStatus());
		Assertions.assertTrue(result.getLog().contains("The script doesn't apply to the driver of the device."), result.getLog());
	}

	@Test
	@DisplayName("Is cancelled for a disabled device")
	void cancelledForDisabledDevice() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		TaskRunner.inSession(session -> {
			session.beginTransaction();
			session.get(Device.class, device.getId()).setStatus(Device.Status.DISABLED);
			session.getTransaction().commit();
			return null;
		});

		long id = this.runner.run(this.script(device, SCRIPT));

		RunDeviceScriptTask result = TaskRunner.load(RunDeviceScriptTask.class, id);
		Assertions.assertEquals(Status.CANCELLED, result.getStatus());
		Assertions.assertTrue(result.getLog().contains("The device is not enabled (not in production)."), result.getLog());
	}
}
