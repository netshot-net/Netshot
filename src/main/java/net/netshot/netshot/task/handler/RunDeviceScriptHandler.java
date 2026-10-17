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

import org.hibernate.Session;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.devicescript.UserDeviceScript;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
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

	private final TaskSubmitter taskSubmitter;

	/**
	 * Creates the handler.
	 * @param taskSubmitter the task submitter
	 */
	public RunDeviceScriptHandler(TaskSubmitter taskSubmitter) {
		this.taskSubmitter = taskSubmitter;
	}

	@Override
	public Class<RunDeviceScriptTask> taskType() {
		return RunDeviceScriptTask.class;
	}

	@Override
	public void run(RunDeviceScriptTask task) {
		Device device = task.getDevice();
		log.debug("Task {}. Starting script task for device {}.", task.getId(),
			device == null ? "null" : device.getId());
		if (device == null) {
			task.logger.info("The device doesn't exist, the task will be cancelled.");
			task.setStatus(Status.CANCELLED);
			return;
		}

		UserDeviceScript deviceScript = null;
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			// Start over from a fresh device from DB
			device = session.get(Device.class, device.getId());
			task.setDevice(device);
			task.logger.info("Run script task for device {} ({}).",
				device.getName(), device.getMgmtAddress());
			if (task.getDeviceDriver() == null || !task.getDeviceDriver().equals(device.getDriver())) {
				log.trace("Task {}. The script doesn't apply to the driver of the device.", task.getId());
				task.logger.error("The script doesn't apply to the driver of the device.");
				task.setStatus(Status.CANCELLED);
				return;
			}
			if (device.getStatus() != Device.Status.INPRODUCTION) {
				log.trace("Task {}. Device not INPRODUCTION, stopping the run script task.", task.getId());
				task.logger.warn("The device is not enabled (not in production).");
				task.setStatus(Status.CANCELLED);
				return;
			}

			deviceScript = new UserDeviceScript(task.getDeviceDriver(), task.getScript(), task.logger);
			deviceScript.setUserInputValues(task.getUserInputValues());
			deviceScript.connectRun(session, device);

			session.merge(device);
			session.getTransaction().commit();
			task.setStatus(Status.SUCCESS);
		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			log.error("Task {}. Error while running the script.", task.getId(), e);
			task.logger.error("Error while running the script: {}", e.getMessage());

			task.setStatus(Status.FAILURE);
			return;
		}
		finally {
			try {
				task.attachDebugLog();
			}
			catch (Exception e1) {
				log.error("Task {}. Error while saving the debug logs.", task.getId(), e1);
			}
			session.close();
		}

		// The script may have changed the device tags/comments
		DynamicDeviceGroup.refreshAllGroupsOfOneDevice(device);

		if (task.isRunSnapshot()) {
			try {
				Task snapshotTask = new TakeSnapshotTask(device, "Snapshot after device script execution", "Auto",
					false, !task.isRunDiagnostics(), !task.isCheckCompliance());
				snapshotTask.setPriority(task.getPriority());
				snapshotTask.setParentTaskId(task.getId());
				this.taskSubmitter.submit(snapshotTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the snapshot task.", task.getId(), e);
			}
		}
		else if (task.isRunDiagnostics()) {
			try {
				Task diagTask = new RunDiagnosticsTask(device, "Run diagnostics after device script execution", "Auto", !task.isCheckCompliance());
				diagTask.setPriority(task.getPriority());
				diagTask.setParentTaskId(task.getId());
				this.taskSubmitter.submit(diagTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the diagnostic task.", task.getId(), e);
			}
		}
		else if (task.isCheckCompliance()) {
			try {
				Task checkTask = new CheckComplianceTask(device, "Check compliance after device script execution", "Auto");
				checkTask.setPriority(task.getPriority());
				checkTask.setParentTaskId(task.getId());
				this.taskSubmitter.submit(checkTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the check compliance task.", task.getId(), e);
			}
		}
	}

}
