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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;

/**
 * Characterization of {@link TakeSnapshotHandler}, run through the task engine against a Cisco IOS
 * device simulator: what the snapshot of a device stores, and the follow-up task it submits.
 */
@DatabaseTest
@SimulatorTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Take snapshot task (TakeSnapshotHandler)")
class TakeSnapshotHandlerTest {

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

	private static Device reload(Device device) {
		return TaskRunner.inSession(session -> session.get(Device.class, device.getId()));
	}

	@Test
	@DisplayName("Updates the device from the snapshot and stores its configuration")
	void snapshotsTheDevice() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);

		long id = this.runner.run(new TakeSnapshotTask(device, "Test", "tester", false, true, true));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(TakeSnapshotTask.class, id).getStatus());
		Device snapshot = reload(device);
		Assertions.assertEquals("router1", snapshot.getName());
		Assertions.assertEquals("15.5(3)S7b", snapshot.getSoftwareVersion());
		Assertions.assertEquals("Cisco CSR1000V", snapshot.getFamily());
		String runningConfig = TaskRunner.inSession(session -> {
			Config config = session.get(Device.class, device.getId()).getLastConfig();
			return ((ConfigLongTextAttribute) config.getAttribute("runningConfig")).getLongText().getText();
		});
		Assertions.assertTrue(runningConfig.contains("ip ssh version 2"), "Running configuration stored");
		Assertions.assertEquals(List.of(), this.runner.submitted(), "No follow-up task when diagnostics and compliance are disabled");
	}

	@Test
	@DisplayName("Submits the diagnostics afterwards, passing the compliance option")
	void submitsTheDiagnostics() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		TakeSnapshotTask task = new TakeSnapshotTask(device, "Test", "tester", false, false, true);
		task.setPriority(7);

		long id = this.runner.run(task);

		List<Task> submitted = this.runner.submitted();
		Assertions.assertEquals(1, submitted.size());
		RunDiagnosticsTask diagnostics = (RunDiagnosticsTask) submitted.get(0);
		Assertions.assertEquals("Run diagnostics after device snapshot", diagnostics.getComments());
		Assertions.assertEquals("Auto", diagnostics.getAuthor());
		Assertions.assertTrue(diagnostics.isDontCheckCompliance());
		Assertions.assertEquals(7, diagnostics.getPriority());
		Assertions.assertEquals(id, diagnostics.getParentTaskId());
		Assertions.assertEquals(device.getId(), diagnostics.getDevice().getId());
	}

	@Test
	@DisplayName("Submits the compliance check afterwards when the diagnostics are disabled")
	void submitsTheComplianceCheck() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);

		long id = this.runner.run(new TakeSnapshotTask(device, "Test", "tester", false, true, false));

		List<Task> submitted = this.runner.submitted();
		Assertions.assertEquals(1, submitted.size());
		CheckComplianceTask check = (CheckComplianceTask) submitted.get(0);
		Assertions.assertEquals("Check compliance after device snapshot", check.getComments());
		Assertions.assertEquals("Auto", check.getAuthor());
		Assertions.assertEquals(id, check.getParentTaskId());
	}

	@Test
	@DisplayName("Refreshes the dynamic groups of the device afterwards")
	void refreshesTheDynamicGroups() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		DynamicDeviceGroup group = new DynamicDeviceGroup("Router 1", null, "[Name] is \"router1\"");
		TaskRunner.persist(group);

		this.runner.run(new TakeSnapshotTask(device, "Test", "tester", false, true, true));

		List<Long> members = TaskRunner.inSession(session -> session
			.createQuery("select m.key.device.id from DeviceGroupMembership m where m.key.group.id = :id", Long.class)
			.setParameter("id", group.getId())
			.list());
		Assertions.assertEquals(List.of(device.getId()), members);
	}

	@Test
	@DisplayName("Keeps the debug log when debugging is enabled")
	void keepsTheDebugLog() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		TakeSnapshotTask task = new TakeSnapshotTask(device, "Test", "tester", false, true, true);
		task.setDebugEnabled(true);

		long id = this.runner.run(task);

		String debugLog = TaskRunner.inSession(session -> session.get(TakeSnapshotTask.class, id).getDebugLog().getText());
		Assertions.assertTrue(debugLog.contains("show version"), "The debug log has the CLI exchanges");
	}

	@Test
	@DisplayName("Fails when the device can't be reached, without follow-up task")
	void failsWhenUnreachable() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER, 1);

		long id = this.runner.run(new TakeSnapshotTask(device, "Test", "tester", false, false, false));

		TakeSnapshotTask task = TaskRunner.load(TakeSnapshotTask.class, id);
		Assertions.assertEquals(Status.FAILURE, task.getStatus());
		Assertions.assertTrue(task.getLog().contains("Error while taking the snapshot"), task.getLog());
		Assertions.assertEquals(List.of(), this.runner.submitted());
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

		long id = this.runner.run(new TakeSnapshotTask(device, "Test", "tester", false, false, false));

		TakeSnapshotTask task = TaskRunner.load(TakeSnapshotTask.class, id);
		Assertions.assertEquals(Status.CANCELLED, task.getStatus());
		Assertions.assertTrue(task.getLog().contains("The device is not enabled (not in production)."), task.getLog());
		Assertions.assertEquals(List.of(), this.runner.submitted(), "No follow-up task for a cancelled snapshot");
	}
}
