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

import net.netshot.netshot.testing.TestDevices;
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

	private Task script(Device device, String code) {
		return this.script(device, code, this.driver.getName(), false, false, false);
	}

	private Task script(Device device, String code, String driverName, boolean runSnapshot, boolean runDiagnostics, boolean checkCompliance) {
		return RunDeviceScriptTask.of(device, new RunDeviceScriptTask(null, code, driverName, new HashMap<>(),
			runSnapshot, runDiagnostics, checkCompliance), "Test", "tester");
	}

	/**
	 * Checks that a task succeeded, showing its log otherwise (such as a connection error to the simulator).
	 *
	 * @param id the ID of the task
	 */
	private static void assertSucceeded(long id) {
		Task result = TaskRunner.load(id);
		Assertions.assertEquals(Status.SUCCESS, result.status(), () -> "The script failed: " + result.log());
	}

	@Test
	@DisplayName("Runs the script on the device and saves the device")
	void runsTheScript() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);

		long id = this.runner.run(this.script(device, SCRIPT));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(id).status());
		Assertions.assertEquals("CSR seen", TestDevices.find(device.id()).comments());
		Assertions.assertEquals(List.of(), this.runner.submitted(), "No follow-up task by default");
	}

	@Test
	@DisplayName("Submits a snapshot afterwards when asked, passing the diagnostics and compliance options")
	void submitsTheSnapshot() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		Task task = this.script(device, SCRIPT, this.driver.getName(), true, false, true);

		long id = this.runner.run(task);

		assertSucceeded(id);
		List<Task> submitted = this.runner.submitted();
		Assertions.assertEquals(1, submitted.size());
		TakeSnapshotTask snapshot = (TakeSnapshotTask) submitted.get(0).spec();
		Assertions.assertEquals("Snapshot after device script execution", submitted.get(0).comments());
		Assertions.assertEquals("Auto", submitted.get(0).author());
		Assertions.assertFalse(snapshot.automatic());
		Assertions.assertTrue(snapshot.dontRunDiagnostics());
		Assertions.assertFalse(snapshot.dontCheckCompliance());
		Assertions.assertEquals(id, submitted.get(0).parentTaskId());
	}

	@Test
	@DisplayName("Without snapshot, submits the diagnostics afterwards when asked")
	void submitsTheDiagnostics() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		Task task = this.script(device, SCRIPT, this.driver.getName(), false, true, false);

		long id = this.runner.run(task);

		assertSucceeded(id);
		List<Task> submitted = this.runner.submitted();
		Assertions.assertEquals(1, submitted.size());
		RunDiagnosticsTask diagnostics = (RunDiagnosticsTask) submitted.get(0).spec();
		Assertions.assertEquals("Run diagnostics after device script execution", submitted.get(0).comments());
		Assertions.assertTrue(diagnostics.dontCheckCompliance());
	}

	@Test
	@DisplayName("Without snapshot nor diagnostics, submits the compliance check afterwards when asked")
	void submitsTheComplianceCheck() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		Task task = this.script(device, SCRIPT, this.driver.getName(), false, false, true);

		long id = this.runner.run(task);

		assertSucceeded(id);
		List<Task> submitted = this.runner.submitted();
		Assertions.assertEquals(1, submitted.size());
		Assertions.assertInstanceOf(CheckComplianceTask.class, submitted.get(0).spec());
		Assertions.assertEquals("Check compliance after device script execution", submitted.get(0).comments());
	}

	@Test
	@DisplayName("Fails when the script fails, without follow-up task")
	void failsWhenTheScriptFails() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		Task task = this.script(device, "function run(cli, device) { throw 'Broken script'; }", this.driver.getName(), true, false, false);

		long id = this.runner.run(task);

		Task result = TaskRunner.load(id);
		Assertions.assertEquals(Status.FAILURE, result.status());
		Assertions.assertTrue(result.log().contains("Error while running the script"), result.log());
		Assertions.assertEquals(List.of(), this.runner.submitted());
	}

	@Test
	@DisplayName("Is cancelled when the script is for another driver")
	void cancelledForAnotherDriver() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		Task task = this.script(device, SCRIPT, "JuniperJunos", false, false, false);

		long id = this.runner.run(task);

		Task result = TaskRunner.load(id);
		Assertions.assertEquals(Status.CANCELLED, result.status());
		Assertions.assertTrue(result.log().contains("The script doesn't apply to the driver of the device."), result.log());
	}

	@Test
	@DisplayName("Is cancelled for a disabled device")
	void cancelledForDisabledDevice() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		TestDevices.save(TestDevices.find(device.id()).toBuilder().status(Device.Status.DISABLED).build());

		long id = this.runner.run(this.script(device, SCRIPT));

		Task result = TaskRunner.load(id);
		Assertions.assertEquals(Status.CANCELLED, result.status());
		Assertions.assertTrue(result.log().contains("The device is not enabled (not in production)."), result.log());
	}
}
