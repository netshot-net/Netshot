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

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.access.Connectors;
import net.netshot.netshot.devicescript.RunDiagnosticDeviceScript;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticRepository;
import net.netshot.netshot.diagnostic.run.DiagnosticAdapters;
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
import net.netshot.netshot.task.type.RunDiagnosticsTask;

/**
 * Runs the diagnostics on a device.
 */
@Slf4j
public class RunDiagnosticsHandler implements TaskHandler<RunDiagnosticsTask> {

	private final DriverRegistry driverRegistry;
	private final DiagnosticAdapters diagnosticAdapters;
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
	 * @param diagnosticAdapters the diagnostic adapters
	 * @param taskSubmitter the task submitter
	 * @param dynamicGroups the refresher of the dynamic groups
	 * @param connectors the secret resolver and the SSH client of the device accesses
	 */
	public RunDiagnosticsHandler(Transactions transactions, DriverRegistry driverRegistry, DiagnosticAdapters diagnosticAdapters,
		TaskSubmitter taskSubmitter, DynamicGroups dynamicGroups, Connectors connectors) {
		this.transactions = transactions;
		this.connectors = connectors;
		this.dynamicGroups = dynamicGroups;
		this.driverRegistry = driverRegistry;
		this.diagnosticAdapters = diagnosticAdapters;
		this.taskSubmitter = taskSubmitter;
	}

	@Override
	public Class<RunDiagnosticsTask> taskType() {
		return RunDiagnosticsTask.class;
	}

	@Override
	public void run(Task task, RunDiagnosticsTask spec, TaskRun run) {
		Long deviceId = spec.deviceId();
		Device device = null;
		log.debug("Task {}. Starting diagnostic task for device {}.", task.id(),
			deviceId);
		if (deviceId == null) {
			run.logger.info("The device doesn't exist, the task will be cancelled.");
			run.setStatus(Status.CANCELLED);
			return;
		}
		RunDiagnosticDeviceScript deviceScript = null;
		try {
			// Start over from a fresh device from DB
			device = this.transactions.read(dsl -> this.deviceRepository.find(dsl, deviceId));
			if (device == null) {
				throw new IllegalStateException("The device %d doesn't exist anymore".formatted(deviceId));
			}
			run.logger.trace("Run diagnostic task for device {} ({}).",
				device.name(), device.mgmtAddress());
			if (device.status() != Device.Status.INPRODUCTION) {
				log.trace("Task {}. Device not INPRODUCTION, stopping the diagnostic task.", task.id());
				run.logger.warn("The device is not enabled (not in production).");
				run.setStatus(Status.CANCELLED);
				return;
			}
			List<Diagnostic> diagnostics = this.transactions.read(dsl -> new DiagnosticRepository().findEnabledForDevice(dsl, deviceId));
			if (diagnostics.size() > 0) {
				deviceScript = new RunDiagnosticDeviceScript(this.driverRegistry, this.connectors, diagnostics, this.diagnosticAdapters, run.logger);
				DeviceDraft draft = new DeviceDraft(device);
				deviceScript.connectRun(draft);
				this.transactions.write(dsl -> this.deviceRepository.saveDraft(dsl, draft));
				device = draft.toDevice();
			}
			else {
				run.logger.info("No diagnostic was found that apply to this device.");
			}
			run.setStatus(Status.SUCCESS);

		}
		catch (Exception e) {
			log.error("Task {}. Error while executing the diagnostics.", task.id(), e);
			String message = e.getMessage();
			if (e.getCause() != null && e.getCause().getCause() != null) {
				// Add SQL error if possible
				message += " / " + e.getCause().getCause().getMessage();
			}
			run.logger.error("Error while executing the diagnostics: {}", message);
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

		log.debug("Task {}. Request to refresh all the groups for the device after the diagnostics.", task.id());
		this.dynamicGroups.refreshAllGroupsOfOneDevice(deviceId);

		if (!spec.dontCheckCompliance()) {
			try {
				Task checkTask = CheckComplianceTask.of(device, "Check compliance after device diagnostics", "Auto").toBuilder()
					.priority(task.priority())
					.parentTaskId(task.id())
					.build();
				this.taskSubmitter.submit(checkTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the new task.", task.id(), e);
			}
		}
	}

}
