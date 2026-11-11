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

import java.time.Instant;
import java.util.List;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceAccess.SshHostKeyVerification;
import net.netshot.netshot.inventory.device.DeviceUpdate;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;

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
		return TestDevices.find(device.id());
	}

	@Test
	@DisplayName("Updates the device from the snapshot and stores its configuration")
	void snapshotsTheDevice() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);

		long id = this.runner.run(TakeSnapshotTask.of(device, "Test", "tester", false, true, true));

		Assertions.assertEquals(Status.SUCCESS, TaskRunner.load(id).status());
		Device snapshot = reload(device);
		Assertions.assertEquals("router1", snapshot.name());
		Assertions.assertEquals("15.5(3)S7b", snapshot.softwareVersion());
		Assertions.assertEquals("Cisco CSR1000V", snapshot.family());
		Config config = TestDevices.findConfig(snapshot.lastConfigId());
		String runningConfig = ((ConfigLongTextAttribute) config.getAttribute("runningConfig")).text();
		Assertions.assertTrue(runningConfig.contains("ip ssh version 2"), "Running configuration stored");
		Assertions.assertEquals(List.of(), this.runner.submitted(), "No follow-up task when diagnostics and compliance are disabled");
	}

	@Test
	@DisplayName("Sets the last snapshot success date, and only that one")
	void setsTheSuccessDate() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		Instant before = Instant.now().minusSeconds(1);

		this.runner.run(TakeSnapshotTask.of(device, "Test", "tester", false, true, true));

		Device snapshot = reload(device);
		Assertions.assertNotNull(snapshot.lastSnapshotSuccessDate());
		Assertions.assertTrue(snapshot.lastSnapshotSuccessDate().isAfter(before));
		Assertions.assertNull(snapshot.lastSnapshotFailureDate());
	}

	@Test
	@DisplayName("Submits the diagnostics afterwards, passing the compliance option")
	void submitsTheDiagnostics() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		Task task = TakeSnapshotTask.of(device, "Test", "tester", false, false, true).toBuilder().priority(7).build();

		long id = this.runner.run(task);

		List<Task> submitted = this.runner.submitted();
		Assertions.assertEquals(1, submitted.size());
		RunDiagnosticsTask diagnostics = (RunDiagnosticsTask) submitted.get(0).spec();
		Assertions.assertEquals("Run diagnostics after device snapshot", submitted.get(0).comments());
		Assertions.assertEquals("Auto", submitted.get(0).author());
		Assertions.assertTrue(diagnostics.dontCheckCompliance());
		Assertions.assertEquals(7, submitted.get(0).priority());
		Assertions.assertEquals(id, submitted.get(0).parentTaskId());
		Assertions.assertEquals(device.id(), diagnostics.deviceId());
	}

	@Test
	@DisplayName("Submits the compliance check afterwards when the diagnostics are disabled")
	void submitsTheComplianceCheck() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);

		long id = this.runner.run(TakeSnapshotTask.of(device, "Test", "tester", false, true, false));

		List<Task> submitted = this.runner.submitted();
		Assertions.assertEquals(1, submitted.size());
		Task check = submitted.get(0);
		Assertions.assertInstanceOf(CheckComplianceTask.class, check.spec());
		Assertions.assertEquals("Check compliance after device snapshot", check.comments());
		Assertions.assertEquals("Auto", check.author());
		Assertions.assertEquals(id, check.parentTaskId());
	}

	@Test
	@DisplayName("Refreshes the dynamic groups of the device afterwards")
	void refreshesTheDynamicGroups() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		DynamicDeviceGroup group = TestDevices.addDynamicGroup("Router 1", "[Name] is \"router1\"");

		this.runner.run(TakeSnapshotTask.of(device, "Test", "tester", false, true, true));

		List<Long> members = TestDevices.memberIds(group.id());
		Assertions.assertEquals(List.of(device.id()), members);
	}

	@Test
	@DisplayName("Keeps the debug log when debugging is enabled")
	void keepsTheDebugLog() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		Task task = TakeSnapshotTask.of(device, "Test", "tester", false, true, true).toBuilder().debugEnabled(true).build();

		long id = this.runner.run(task);

		String debugLog = TestTasks.debugLog(id);
		Assertions.assertTrue(debugLog.contains("show version"), "The debug log has the CLI exchanges");
	}

	@Test
	@DisplayName("Fails when the device can't be reached, without follow-up task")
	void failsWhenUnreachable() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER, 1);

		long id = this.runner.run(TakeSnapshotTask.of(device, "Test", "tester", false, false, false));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.FAILURE, task.status());
		Assertions.assertTrue(task.log().contains("Error while taking the snapshot"), task.log());
		Assertions.assertEquals(List.of(), this.runner.submitted());
	}

	@Test
	@DisplayName("Sets the last snapshot failure date without changing the device version nor the success date")
	void setsTheFailureDate() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER, 1);
		Instant success = Instant.parse("2026-01-01T10:00:00Z");
		TestDevices.setSnapshotDate(device.id(), true, success);
		Device before = reload(device);

		this.runner.run(TakeSnapshotTask.of(device, "Test", "tester", false, false, false));

		Device failed = reload(device);
		Assertions.assertNotNull(failed.lastSnapshotFailureDate());
		Assertions.assertEquals(success, failed.lastSnapshotSuccessDate());
		Assertions.assertEquals(before.version(), failed.version(), "The device version is not bumped");
		Assertions.assertEquals(before.changeDate(), failed.changeDate());
	}

	@Test
	@DisplayName("Is cancelled for a disabled device")
	void cancelledForDisabledDevice() throws Exception {
		Device device = SimulatedRouter.persist(CONTAINER);
		TestDevices.save(device.toBuilder().status(Device.Status.DISABLED).build());

		long id = this.runner.run(TakeSnapshotTask.of(device, "Test", "tester", false, false, false));

		Task task = TaskRunner.load(id);
		Assertions.assertEquals(Status.CANCELLED, task.status());
		Assertions.assertTrue(task.log().contains("The device is not enabled (not in production)."), task.log());
		Assertions.assertEquals(List.of(), this.runner.submitted(), "No follow-up task for a cancelled snapshot");
		Device reloaded = reload(device);
		Assertions.assertNull(reloaded.lastSnapshotSuccessDate());
		Assertions.assertNull(reloaded.lastSnapshotFailureDate());
	}

	/**
	 * The SSH host key verification of the snapshots, with the keys saved in the database.
	 */
	@Nested
	@DisplayName("SSH host key verification")
	class HostKeyVerification {

		/**
		 * Changes the SSH access of a device as the device service does for the web UI and the API.
		 *
		 * @param device the device
		 * @param mode the verification mode
		 * @param trustedKeys the trusted keys, or null for none
		 * @return the access as stored
		 */
		private DeviceAccess setAccess(Device device, SshHostKeyVerification mode, String trustedKeys) {
			DeviceAccess ssh = reload(device).getDeviceAccess("ssh").toBuilder()
				.sshHostKeyVerification(mode).sshTrustedHostKeys(trustedKeys).build();
			TestWiring.get().deviceService().update(device.id(), new DeviceUpdate(null, null, null, null, List.of(ssh), null, null, null));
			return reload(device).getDeviceAccess("ssh");
		}

		private Task snapshot(Device device) throws Exception {
			return TaskRunner.load(TakeSnapshotHandlerTest.this.runner.run(TakeSnapshotTask.of(device, "Test", "tester", false, false, false)));
		}

		/**
		 * Builds another key of the algorithm of a trusted key.
		 *
		 * @param trustedKey the trusted key line
		 * @return the line of another key
		 */
		private String otherKey(String trustedKey) throws Exception {
			String algorithm = trustedKey.substring(0, trustedKey.indexOf(' '));
			int size = KeyPairProvider.SSH_RSA.equals(algorithm) ? 2048
				: algorithm.endsWith("384") ? 384 : algorithm.endsWith("521") ? 521 : 256;
			return PublicKeyEntry.toString(KeyUtils.generateKeyPair(algorithm, size).getPublic());
		}

		@Test
		@DisplayName("Trust any (the default of an access): the snapshot succeeds, no key is saved")
		void trustAny() throws Exception {
			Device device = SimulatedRouter.persist(CONTAINER);
			Assertions.assertEquals(SshHostKeyVerification.TRUST_ANY, device.getDeviceAccess("ssh").sshHostKeyVerification());
			Assertions.assertEquals(Status.SUCCESS, this.snapshot(device).status());
			Assertions.assertNull(reload(device).getDeviceAccess("ssh").sshTrustedHostKeys());
		}

		@Test
		@DisplayName("Trust known: the first snapshot learns and saves the host key, the next one verifies it")
		void learnsThenVerifies() throws Exception {
			Device device = SimulatedRouter.persist(CONTAINER);
			this.setAccess(device, SshHostKeyVerification.TRUST_KNOWN, null);

			Task first = this.snapshot(device);
			Assertions.assertEquals(Status.SUCCESS, first.status(), first.log());
			Assertions.assertTrue(first.log().contains("New SSH host key accepted and saved for access 'ssh'"), first.log());
			DeviceAccess learned = reload(device).getDeviceAccess("ssh");
			Assertions.assertNotNull(learned.sshTrustedHostKeys());
			Assertions.assertNotNull(learned.sshTrustedHostKeysSince());

			Task second = this.snapshot(device);
			Assertions.assertEquals(Status.SUCCESS, second.status(), second.log());
			Assertions.assertFalse(second.log().contains("New SSH host key"), second.log());
			DeviceAccess verified = reload(device).getDeviceAccess("ssh");
			Assertions.assertEquals(learned.sshTrustedHostKeys(), verified.sshTrustedHostKeys());
			Assertions.assertEquals(learned.sshTrustedHostKeysSince(), verified.sshTrustedHostKeysSince());
		}

		@Test
		@DisplayName("Trust known: a changed host key fails the snapshot with the reason; once cleared, the key is learned again")
		void changedThenCleared() throws Exception {
			Device device = SimulatedRouter.persist(CONTAINER);
			this.setAccess(device, SshHostKeyVerification.TRUST_KNOWN, null);
			Assertions.assertEquals(Status.SUCCESS, this.snapshot(device).status());
			String realKey = reload(device).getDeviceAccess("ssh").sshTrustedHostKeys();

			// Another key set through the device service, as the web UI or the API would
			String otherKey = this.otherKey(realKey);
			this.setAccess(device, SshHostKeyVerification.TRUST_KNOWN, otherKey);
			Task refused = this.snapshot(device);
			Assertions.assertEquals(Status.FAILURE, refused.status());
			Assertions.assertTrue(refused.log().contains("does not match the key trusted so far"), refused.log());
			Assertions.assertEquals(otherKey, reload(device).getDeviceAccess("ssh").sshTrustedHostKeys(), "The trusted key changed");

			// Cleared: learned again
			DeviceAccess cleared = this.setAccess(device, SshHostKeyVerification.TRUST_KNOWN, null);
			Assertions.assertNull(cleared.sshTrustedHostKeys());
			Assertions.assertEquals(Status.SUCCESS, this.snapshot(device).status());
			Assertions.assertEquals(realKey, reload(device).getDeviceAccess("ssh").sshTrustedHostKeys());
		}

		@Test
		@DisplayName("Trust known: the right key set before the first snapshot is verified, nothing is learned")
		void keySetBeforeFirstSnapshot() throws Exception {
			Device learner = SimulatedRouter.persist(CONTAINER);
			this.setAccess(learner, SshHostKeyVerification.TRUST_KNOWN, null);
			Assertions.assertEquals(Status.SUCCESS, this.snapshot(learner).status());
			String realKey = reload(learner).getDeviceAccess("ssh").sshTrustedHostKeys();
			TaskRunner.cleanDatabase();

			Device device = SimulatedRouter.persist(CONTAINER);
			DeviceAccess set = this.setAccess(device, SshHostKeyVerification.TRUST_KNOWN, realKey);
			Task task = this.snapshot(device);
			Assertions.assertEquals(Status.SUCCESS, task.status(), task.log());
			Assertions.assertFalse(task.log().contains("New SSH host key"), task.log());
			DeviceAccess after = reload(device).getDeviceAccess("ssh");
			Assertions.assertEquals(set.sshTrustedHostKeys(), after.sshTrustedHostKeys());
			Assertions.assertEquals(set.sshTrustedHostKeysSince(), after.sshTrustedHostKeysSince());
		}
	}
}
