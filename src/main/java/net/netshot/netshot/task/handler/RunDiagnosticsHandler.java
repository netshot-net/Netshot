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

import org.hibernate.Session;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.devicescript.RunDiagnosticDeviceScript;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.run.DiagnosticAdapters;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.TaskSubmitter;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;

/**
 * Runs the diagnostics on a device.
 */
@Slf4j
public class RunDiagnosticsHandler implements TaskHandler<RunDiagnosticsTask> {

	private final DiagnosticAdapters diagnosticAdapters;
	private final TaskSubmitter taskSubmitter;

	/**
	 * Creates the handler.
	 * @param diagnosticAdapters the diagnostic adapters
	 * @param taskSubmitter the task submitter
	 */
	public RunDiagnosticsHandler(DiagnosticAdapters diagnosticAdapters, TaskSubmitter taskSubmitter) {
		this.diagnosticAdapters = diagnosticAdapters;
		this.taskSubmitter = taskSubmitter;
	}

	@Override
	public Class<RunDiagnosticsTask> taskType() {
		return RunDiagnosticsTask.class;
	}

	@Override
	public void run(RunDiagnosticsTask task) {
		Device device = task.getDevice();
		log.debug("Task {}. Starting diagnostic task for device {}.", task.getId(),
			device == null ? "null" : device.getId());
		if (device == null) {
			task.logger.info("The device doesn't exist, the task will be cancelled.");
			task.setStatus(Status.CANCELLED);
			return;
		}
		Session session = Database.getSession();
		RunDiagnosticDeviceScript deviceScript = null;
		try {
			session.beginTransaction();
			// Start over from a fresh device from DB
			device = session.get(Device.class, device.getId());
			task.setDevice(device);
			task.logger.trace("Run diagnostic task for device {} ({}).",
				device.getName(), device.getMgmtAddress());
			if (device.getStatus() != Device.Status.INPRODUCTION) {
				log.trace("Task {}. Device not INPRODUCTION, stopping the diagnostic task.", task.getId());
				task.logger.warn("The device is not enabled (not in production).");
				task.setStatus(Status.CANCELLED);
				return;
			}
			List<Diagnostic> diagnostics = session.createQuery(
				"select dg from Diagnostic dg join fetch dg.targetGroup tg where dg.enabled = :enabled and "
					+ "tg in (select gm.key.group from DeviceGroupMembership gm where gm.key.device = :device)",
				Diagnostic.class)
				.setParameter("device", device)
				.setParameter("enabled", true)
				.list();
			if (diagnostics.size() > 0) {
				deviceScript = new RunDiagnosticDeviceScript(diagnostics, this.diagnosticAdapters, task.logger);
				deviceScript.connectRun(session, device);
				session.merge(device);
				session.getTransaction().commit();
			}
			else {
				task.logger.info("No diagnostic was found that apply to this device.");
			}
			task.setStatus(Status.SUCCESS);

		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			log.error("Task {}. Error while executing the diagnostics.", task.getId(), e);
			String message = e.getMessage();
			if (e.getCause() != null && e.getCause().getCause() != null) {
				// Add SQL error if possible
				message += " / " + e.getCause().getCause().getMessage();
			}
			task.logger.error("Error while executing the diagnostics: {}", message);
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

		log.debug("Task {}. Request to refresh all the groups for the device after the diagnostics.", task.getId());
		DynamicDeviceGroup.refreshAllGroupsOfOneDevice(device);

		if (!task.isDontCheckCompliance()) {
			try {
				Task checkTask = new CheckComplianceTask(device, "Check compliance after device diagnostics", "Auto");
				checkTask.setPriority(task.getPriority());
				checkTask.setParentTaskId(task.getId());
				this.taskSubmitter.submit(checkTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the new task.", task.getId(), e);
			}
		}
	}

}
