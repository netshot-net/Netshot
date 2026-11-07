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

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.access.Connectors;
import net.netshot.netshot.devicescript.UserDeviceScript;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.group.DynamicGroups;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.TaskSubmitter;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * Runs a script on a device.
 */
@Slf4j
public class RunDeviceScriptHandler implements TaskHandler<RunDeviceScriptTask> {

	private final DriverRegistry driverRegistry;
	private final TaskSubmitter taskSubmitter;
	/** The transaction support. */
	private final Transactions transactions;
	private final DeviceRepository deviceRepository = new DeviceRepository();

	/** The refresher of the dynamic groups. */
	private final DynamicGroups dynamicGroups;

	/** The secret resolver and the SSH client of the device accesses. */
	private final Connectors connectors;

	/**
	 * Creates the handler.
	 * @param transactions the transaction support
	 * @param driverRegistry the loaded device drivers
	 * @param taskSubmitter the task submitter
	 * @param dynamicGroups the refresher of the dynamic groups
	 * @param connectors the secret resolver and the SSH client of the device accesses
	 */
	public RunDeviceScriptHandler(Transactions transactions, DriverRegistry driverRegistry, TaskSubmitter taskSubmitter, DynamicGroups dynamicGroups, Connectors connectors) {
		this.transactions = transactions;
		this.connectors = connectors;
		this.dynamicGroups = dynamicGroups;
		this.driverRegistry = driverRegistry;
		this.taskSubmitter = taskSubmitter;
	}

	@Override
	public Class<RunDeviceScriptTask> taskType() {
		return RunDeviceScriptTask.class;
	}

	@Override
	public void run(Task task, RunDeviceScriptTask spec, TaskRun run) {
		Long deviceId = spec.deviceId();
		Device device = null;
		log.debug("Task {}. Starting script task for device {}.", task.id(),
			deviceId);
		if (deviceId == null) {
			run.logger.info("The device doesn't exist, the task will be cancelled.");
			run.setStatus(Status.CANCELLED);
			return;
		}

		UserDeviceScript deviceScript = null;
		try {
			// Start over from a fresh device from DB
			device = this.transactions.read(dsl -> this.deviceRepository.find(dsl, deviceId));
			if (device == null) {
				throw new IllegalStateException("The device %d doesn't exist anymore".formatted(deviceId));
			}
			run.logger.info("Run script task for device {} ({}).",
				device.name(), device.mgmtAddress());
			if (spec.deviceDriver() == null || !spec.deviceDriver().equals(device.driver())) {
				log.trace("Task {}. The script doesn't apply to the driver of the device.", task.id());
				run.logger.error("The script doesn't apply to the driver of the device.");
				run.setStatus(Status.CANCELLED);
				return;
			}
			if (device.status() != Device.Status.INPRODUCTION) {
				log.trace("Task {}. Device not INPRODUCTION, stopping the run script task.", task.id());
				run.logger.warn("The device is not enabled (not in production).");
				run.setStatus(Status.CANCELLED);
				return;
			}

			deviceScript = new UserDeviceScript(this.driverRegistry, this.connectors, spec.deviceDriver(), spec.script(), run.logger);
			deviceScript.setUserInputValues(spec.userInputValues());
			DeviceDraft draft = new DeviceDraft(device);
			deviceScript.connectRun(draft);
			this.transactions.write(dsl -> this.deviceRepository.saveDraft(dsl, draft));
			device = draft.toDevice();
			run.setStatus(Status.SUCCESS);
		}
		catch (Exception e) {
			log.error("Task {}. Error while running the script.", task.id(), e);
			run.logger.error("Error while running the script: {}", e.getMessage());

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
		}

		// The script may have changed the device tags/comments
		this.dynamicGroups.refreshAllGroupsOfOneDevice(deviceId);

		if (spec.runSnapshot()) {
			try {
				Task snapshotTask = TakeSnapshotTask.of(device, "Snapshot after device script execution", "Auto",
					false, !spec.runDiagnostics(), !spec.checkCompliance()).toBuilder()
					.priority(task.priority())
					.parentTaskId(task.id())
					.build();
				this.taskSubmitter.submit(snapshotTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the snapshot task.", task.id(), e);
			}
		}
		else if (spec.runDiagnostics()) {
			try {
				Task diagTask = RunDiagnosticsTask.of(device, "Run diagnostics after device script execution", "Auto", !spec.checkCompliance()).toBuilder()
					.priority(task.priority())
					.parentTaskId(task.id())
					.build();
				this.taskSubmitter.submit(diagTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the diagnostic task.", task.id(), e);
			}
		}
		else if (spec.checkCompliance()) {
			try {
				Task checkTask = CheckComplianceTask.of(device, "Check compliance after device script execution", "Auto").toBuilder()
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
