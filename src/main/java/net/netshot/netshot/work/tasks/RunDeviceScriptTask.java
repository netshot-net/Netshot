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
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.DynamicDeviceGroup;
import net.netshot.netshot.device.script.UserDeviceScript;
import net.netshot.netshot.rest.RestViews.DefaultView;
import net.netshot.netshot.work.DebugLog;
import net.netshot.netshot.work.Task;

/**
 * This task runs a JS script on a device.
 */
@Entity
@DiscriminatorValue("RunDeviceScriptTask")
@Slf4j
public final class RunDeviceScriptTask extends Task implements DeviceBasedTask {

	/**
	 * Instantiates a new RunDeviceScriptTask task.
	 */
	protected RunDeviceScriptTask() {
	}

	/**
	 * Instantiates a new RunDeviceScriptTask task.
	 *
	 * @param device the device
	 * @param script the script
	 * @param driver the device driver
	 * @param comments the comments
	 * @param author the author
	 */
	public RunDeviceScriptTask(Device device, String script, DeviceDriver driver, String comments, String author) {
		super(comments, device.getLastConfig() == null ? device.getMgmtAddress() : device.getName(),
			author);
		this.setDevice(device);
		this.setScript(script);
		this.setDeviceDriver(driver.getName());
	}

	@Override
	public void run() {
		log.debug("Task {}. Starting script task for device {}.", this.getId(),
			device == null ? "null" : device.getId());
		if (device == null) {
			this.logger.info("The device doesn't exist, the task will be cancelled.");
			this.status = Status.CANCELLED;
			return;
		}

		UserDeviceScript deviceScript = null;
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			// Start over from a fresh device from DB
			device = session.get(Device.class, device.getId());
			this.logger.info("Run script task for device {} ({}).",
				device.getName(), device.getMgmtAddress());
			if (deviceDriver == null || !deviceDriver.equals(device.getDriver())) {
				log.trace("Task {}. The script doesn't apply to the driver of the device.", this.getId());
				this.logger.error("The script doesn't apply to the driver of the device.");
				this.status = Status.CANCELLED;
				return;
			}
			if (device.getStatus() != Device.Status.INPRODUCTION) {
				log.trace("Task {}. Device not INPRODUCTION, stopping the run script task.", this.getId());
				this.logger.warn("The device is not enabled (not in production).");
				this.status = Status.CANCELLED;
				return;
			}

			deviceScript = new UserDeviceScript(this.deviceDriver, this.script, this.logger);
			deviceScript.setUserInputValues(this.userInputValues);
			deviceScript.connectRun(session, device);

			session.merge(device);
			session.getTransaction().commit();
			this.status = Status.SUCCESS;
		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			log.error("Task {}. Error while running the script.", this.getId(), e);
			this.logger.error("Error while running the script: {}", e.getMessage());

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

		// The script may have changed the device tags/comments
		DynamicDeviceGroup.refreshAllGroupsOfOneDevice(device);

		if (this.runSnapshot) {
			try {
				Task snapshotTask = new TakeSnapshotTask(device, "Snapshot after device script execution", "Auto",
					false, !this.runDiagnostics, !this.checkCompliance);
				snapshotTask.setPriority(this.getPriority());
				snapshotTask.setParentTaskId(this.getId());
				TaskManager.addTask(snapshotTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the snapshot task.", this.getId(), e);
			}
		}
		else if (this.runDiagnostics) {
			try {
				Task diagTask = new RunDiagnosticsTask(device, "Run diagnostics after device script execution", "Auto", !this.checkCompliance);
				diagTask.setPriority(this.getPriority());
				diagTask.setParentTaskId(this.getId());
				TaskManager.addTask(diagTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the diagnostic task.", this.getId(), e);
			}
		}
		else if (this.checkCompliance) {
			try {
				Task checkTask = new CheckComplianceTask(device, "Check compliance after device script execution", "Auto");
				checkTask.setPriority(this.getPriority());
				checkTask.setParentTaskId(this.getId());
				TaskManager.addTask(checkTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the check compliance task.", this.getId(), e);
			}
		}
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#deviceLockPolicy()
	 */
	@Override
	public DeviceLockPolicy deviceLockPolicy() {
		// Each script run is unique: never cancelled because of another script, waits for its turn
		return DeviceLockPolicy.WAIT;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#getTaskDescription()
	 */
	@Override
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public String getTaskDescription() {
		return "Device script execution";
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
		RunDeviceScriptTask task = (RunDeviceScriptTask) super.clone();
		task.setDevice(this.device);
		task.setDeviceDriver(this.deviceDriver);
		task.setRunSnapshot(this.runSnapshot);
		task.setRunDiagnostics(this.runDiagnostics);
		task.setCheckCompliance(this.checkCompliance);
		return task;
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
}
