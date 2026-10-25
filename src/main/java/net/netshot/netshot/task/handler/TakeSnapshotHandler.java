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

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.devicescript.DeviceScript;
import net.netshot.netshot.devicescript.SnapshotDeviceScript;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.group.DynamicGroups;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.access.collector.SshServer;
import net.netshot.netshot.task.AutoSnapshotQueue;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.TaskSubmitter;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.vault.SecretResolver;

/**
 * Takes the snapshot of a device.
 */
@Slf4j
public class TakeSnapshotHandler implements TaskHandler<TakeSnapshotTask> {

	private final DriverRegistry driverRegistry;
	private final TaskSubmitter taskSubmitter;

	/** The devices with a queued automatic snapshot. */
	private final AutoSnapshotQueue autoSnapshots;

	/** The embedded SSH server, where the devices upload files (null if disabled). */
	private final SshServer sshServer;
	private final Transactions transactions = new Transactions();
	private final DeviceRepository deviceRepository = new DeviceRepository();

	/** The refresher of the dynamic groups. */
	private final DynamicGroups dynamicGroups;

	/** Resolves the secrets of the credentials (local or Vault-backed). */
	private final SecretResolver secrets;

	/**
	 * Creates the handler.
	 * @param driverRegistry the loaded device drivers
	 * @param taskSubmitter the task submitter
	 * @param autoSnapshots the devices with a queued automatic snapshot (cleared at the end of an automatic snapshot)
	 * @param sshServer the embedded SSH server, where the devices upload files (null if disabled)
	 * @param dynamicGroups the refresher of the dynamic groups
	 * @param secrets resolves the secrets of the credentials (local or Vault-backed)
	 */
	public TakeSnapshotHandler(DriverRegistry driverRegistry, TaskSubmitter taskSubmitter, AutoSnapshotQueue autoSnapshots,
		SshServer sshServer, DynamicGroups dynamicGroups, SecretResolver secrets) {
		this.secrets = secrets;
		this.dynamicGroups = dynamicGroups;
		this.driverRegistry = driverRegistry;
		this.sshServer = sshServer;
		this.taskSubmitter = taskSubmitter;
		this.autoSnapshots = autoSnapshots;
	}

	@Override
	public Class<TakeSnapshotTask> taskType() {
		return TakeSnapshotTask.class;
	}

	@Override
	public void run(Task task, TakeSnapshotTask spec, TaskRun run) {
		Long deviceId = spec.deviceId();
		Device device = null;
		log.debug("Task {}. Starting snapshot task for device {}.", task.id(),
			deviceId);
		if (deviceId == null) {
			run.logger.info("The device doesn't exist, the task will be cancelled.");
			run.setStatus(Status.CANCELLED);
			return;
		}
		DeviceScript deviceScript = new SnapshotDeviceScript(this.driverRegistry, this.secrets, this.sshServer, run.logger);
		try {
			// Start over from a fresh device from DB
			device = this.transactions.read(dsl -> this.deviceRepository.find(dsl, deviceId));
			if (device == null) {
				throw new IllegalStateException("The device %d doesn't exist anymore".formatted(deviceId));
			}
			run.logger.info("Snapshot task for device {} ({}).",
				device.name(), device.mgmtAddress());
			if (device.status() != Device.Status.INPRODUCTION) {
				log.trace("Task {}. Device not INPRODUCTION, stopping the snapshot task.", task.id());
				run.logger.warn("The device is not enabled (not in production).");
				run.setStatus(Status.CANCELLED);
				return;
			}
			DeviceDraft draft = new DeviceDraft(device);
			deviceScript.connectRun(draft);
			Config newConfig = this.transactions.write(dsl -> this.deviceRepository.saveDraft(dsl, draft));
			device = newConfig == null ? draft.toDevice() : draft.toDevice().toBuilder().lastConfigId(newConfig.id()).build();
			if (newConfig != null) {
				for (ConfigAttribute ca : newConfig.attributes()) {
					if (ca instanceof ConfigBinaryFileAttribute cbfa) {
						cbfa.finalizeStorage(deviceId, newConfig.id());
					}
				}
			}
			run.setStatus(Status.SUCCESS);
		}
		catch (Exception e) {
			log.error("Task {}. Error while taking the snapshot.", task.id(), e);
			run.logger.error("Error while taking the snapshot: {}", e.getMessage());
			run.setStatus(Status.FAILURE);
			return;
		}
		finally {
			try {
				run.attachDebugLog();
			}
			catch (Exception e1) {
				log.error("Task {}. Error while saving the debug logs.", task.id(), e1);
			}
			if (spec.automatic()) {
				this.autoSnapshots.clear(deviceId);
			}
		}

		log.debug("Task {}. Request to refresh all the groups for the device after the snapshot.", task.id());
		this.dynamicGroups.refreshAllGroupsOfOneDevice(deviceId);

		if (!spec.dontRunDiagnostics()) {
			try {
				Task diagTask = RunDiagnosticsTask.of(device, "Run diagnostics after device snapshot", "Auto", spec.dontCheckCompliance()).toBuilder()
					.priority(task.priority())
					.parentTaskId(task.id())
					.build();
				this.taskSubmitter.submit(diagTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the diagnostic task.", task.id(), e);
			}

		}
		else if (!spec.dontCheckCompliance()) {
			try {
				Task checkTask = CheckComplianceTask.of(device, "Check compliance after device snapshot", "Auto").toBuilder()
					.priority(task.priority())
					.parentTaskId(task.id())
					.build();
				this.taskSubmitter.submit(checkTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the check compliance task.", task.id(), e);
			}
		}
	}

}
