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
import net.netshot.netshot.devicescript.DeviceScript;
import net.netshot.netshot.devicescript.SnapshotDeviceScript;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.AutoSnapshotScheduler;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.TaskSubmitter;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * Takes the snapshot of a device.
 */
@Slf4j
public class TakeSnapshotHandler implements TaskHandler<TakeSnapshotTask> {

	private final DriverRegistry driverRegistry;
	private final TaskSubmitter taskSubmitter;

	/**
	 * Creates the handler.
	 * @param driverRegistry the loaded device drivers
	 * @param taskSubmitter the task submitter
	 */
	public TakeSnapshotHandler(DriverRegistry driverRegistry, TaskSubmitter taskSubmitter) {
		this.driverRegistry = driverRegistry;
		this.taskSubmitter = taskSubmitter;
	}

	@Override
	public Class<TakeSnapshotTask> taskType() {
		return TakeSnapshotTask.class;
	}

	@Override
	public void run(TakeSnapshotTask task) {
		Device device = task.getDevice();
		log.debug("Task {}. Starting snapshot task for device {}.", task.getId(),
			device == null ? "null" : device.getId());
		if (device == null) {
			task.logger.info("The device doesn't exist, the task will be cancelled.");
			task.setStatus(Status.CANCELLED);
			return;
		}
		Session session = Database.getSession();
		DeviceScript deviceScript = new SnapshotDeviceScript(this.driverRegistry, task.logger);
		try {
			session.beginTransaction();
			// Start over from a fresh device from DB
			device = session.get(Device.class, device.getId());
			task.setDevice(device);
			task.logger.info("Snapshot task for device {} ({}).",
				device.getName(), device.getMgmtAddress());
			if (device.getStatus() != Device.Status.INPRODUCTION) {
				log.trace("Task {}. Device not INPRODUCTION, stopping the snapshot task.", task.getId());
				task.logger.warn("The device is not enabled (not in production).");
				task.setStatus(Status.CANCELLED);
				return;
			}
			deviceScript.connectRun(session, device);
			session.persist(device);
			session.getTransaction().commit();
			Config lastConfig = device.getLastConfig();
			if (lastConfig != null) {
				for (ConfigAttribute ca : lastConfig.getAttributes()) {
					if (ca instanceof ConfigBinaryFileAttribute cbfa) {
						cbfa.finalizeStorage();
					}
				}
			}
			task.setStatus(Status.SUCCESS);
		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			log.error("Task {}. Error while taking the snapshot.", task.getId(), e);
			task.logger.error("Error while taking the snapshot: {}", e.getMessage());
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
			if (task.isAutomatic()) {
				AutoSnapshotScheduler.clearScheduledAutoSnapshot(task.getDevice().getId());
			}
		}

		log.debug("Task {}. Request to refresh all the groups for the device after the snapshot.", task.getId());
		DynamicDeviceGroup.refreshAllGroupsOfOneDevice(device);

		if (!task.isDontRunDiagnostics()) {
			try {
				Task diagTask = new RunDiagnosticsTask(device, "Run diagnostics after device snapshot", "Auto", task.isDontCheckCompliance());
				diagTask.setPriority(task.getPriority());
				diagTask.setParentTaskId(task.getId());
				this.taskSubmitter.submit(diagTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the diagnostic task.", task.getId(), e);
			}

		}
		else if (!task.isDontCheckCompliance()) {
			try {
				Task checkTask = new CheckComplianceTask(device, "Check compliance after device snapshot", "Auto");
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
