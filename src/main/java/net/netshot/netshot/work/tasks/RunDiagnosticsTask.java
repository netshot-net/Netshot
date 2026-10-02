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
package net.netshot.netshot.work.tasks;

import java.util.List;

import org.hibernate.Session;
import org.quartz.JobKey;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.TaskManager;
import net.netshot.netshot.database.Database;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.DynamicDeviceGroup;
import net.netshot.netshot.device.script.RunDiagnosticDeviceScript;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.rest.RestViews.DefaultView;
import net.netshot.netshot.work.DebugLog;
import net.netshot.netshot.work.Task;
import net.netshot.netshot.work.TaskLockManager;

/**
 * This task runs the diagnostics (if any is defined) on the specified device.
 */
@Entity
@DiscriminatorValue("RunDiagnosticsTask")
@Slf4j
public final class RunDiagnosticsTask extends Task implements DeviceBasedTask {

	/**
	 * Instantiate a new RunDiagnosticTask (for Hibernate).
	 */
	protected RunDiagnosticsTask() {
	}

	/**
	 * Instantiate a new RunDiagnosticTask.
	 * @param device The device to run the task on
	 * @param comments Any commends about this task
	 * @param author Who requested that task
	 * @param dontCheckCompliance Do not check compliance after these diagnostics
	 */
	public RunDiagnosticsTask(Device device, String comments, String author, boolean dontCheckCompliance) {
		super(comments, device.getLastConfig() == null ? device.getMgmtAddress() : device.getName(),
			author);
		this.setDevice(device);
		this.setDontCheckCompliance(dontCheckCompliance);
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#deviceLockPolicy()
	 */
	@Override
	public DeviceLockPolicy deviceLockPolicy() {
		return DeviceLockPolicy.CANCEL_IF_SAME_TYPE;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#deviceLockTypeNamespace()
	 */
	@Override
	public int deviceLockTypeNamespace() {
		return TaskLockManager.NS_DIAGNOSTICS;
	}

	@Override
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public String getTaskDescription() {
		return "Device diagnostics";
	}

	/**
	 * Get the ID of the device.
	 * 
	 * @return the ID of the device
	 */
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public long getDeviceId() {
		if (this.device == null) {
			return 0;
		}
		return this.device.getId();
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		RunDiagnosticsTask task = (RunDiagnosticsTask) super.clone();
		task.setDevice(this.device);
		return task;
	}

	@Override
	public void run() {
		log.debug("Task {}. Starting diagnostic task for device {}.", this.getId(),
			device == null ? "null" : device.getId());
		if (device == null) {
			this.logger.info("The device doesn't exist, the task will be cancelled.");
			this.status = Status.CANCELLED;
			return;
		}
		Session session = Database.getSession();
		RunDiagnosticDeviceScript deviceScript = null;
		try {
			session.beginTransaction();
			// Start over from a fresh device from DB
			device = session.get(Device.class, device.getId());
			this.logger.trace("Run diagnostic task for device {} ({}).",
				device.getName(), device.getMgmtAddress());
			if (device.getStatus() != Device.Status.INPRODUCTION) {
				log.trace("Task {}. Device not INPRODUCTION, stopping the diagnostic task.", this.getId());
				this.logger.warn("The device is not enabled (not in production).");
				this.status = Status.CANCELLED;
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
				deviceScript = new RunDiagnosticDeviceScript(diagnostics, this.logger);
				deviceScript.connectRun(session, device);
				session.merge(device);
				session.getTransaction().commit();
			}
			else {
				this.logger.info("No diagnostic was found that apply to this device.");
			}
			this.status = Status.SUCCESS;

		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			log.error("Task {}. Error while executing the diagnostics.", this.getId(), e);
			String message = e.getMessage();
			if (e.getCause() != null && e.getCause().getCause() != null) {
				// Add SQL error if possible
				message += " / " + e.getCause().getCause().getMessage();
			}
			this.logger.error("Error while executing the diagnostics: {}", message);
			this.status = Status.FAILURE;
			return;
		}
		finally {
			try {
				if (this.fullLogs != null) {
					this.debugLog = new DebugLog(this.fullLogs.toString());
				}
			}
			catch (Exception e1) {
				log.error("Task {}. Error while saving the debug logs.", this.getId(), e1);
			}
			session.close();
		}

		log.debug("Task {}. Request to refresh all the groups for the device after the diagnostics.", this.getId());
		DynamicDeviceGroup.refreshAllGroupsOfOneDevice(device);

		if (!this.dontCheckCompliance) {
			try {
				Task checkTask = new CheckComplianceTask(device, "Check compliance after device diagnostics", "Auto");
				checkTask.setPriority(this.getPriority());
				checkTask.setParentTaskId(this.getId());
				TaskManager.addTask(checkTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the new task.", this.getId(), e);
			}
		}

	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.work.Task#getIdentity()
	 */
	@Override
	@Transient
	public JobKey getIdentity() {
		return new JobKey(String.format("Task_%d", this.getId()),
			String.format("RunDevice_%d", this.getDeviceId()));
	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.work.Task#getRunnerHash()
	 */
	@Override
	@Transient
	public long getRunnerHash() {
		return this.getDeviceId();
	}
}
