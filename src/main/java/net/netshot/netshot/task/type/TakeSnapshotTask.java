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
package net.netshot.netshot.task.type;

import org.quartz.JobKey;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.AutoSnapshotScheduler;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.engine.TaskLockManager;

/**
 * This task takes a snapshot of a device.
 */
@Entity
@DiscriminatorValue("TakeSnapshotTask")
@Slf4j
public final class TakeSnapshotTask extends Task implements DeviceBasedTask {

	/**
	 * Instantiates a new take snapshot task.
	 */
	protected TakeSnapshotTask() {
	}

	/**
	 * Whether this is an automatic snapshot (as opposed to user/API requested).
	 * @return true if automatic
	 */
	@Transient
	public boolean isAutomatic() {
		return this.getBooleanAttribute("automatic", false);
	}

	/**
	 * Sets whether this is an automatic snapshot.
	 * @param automatic true if automatic
	 */
	public void setAutomatic(boolean automatic) {
		this.setAttribute("automatic", automatic);
	}

	/**
	 * Instantiates a new take snapshot task.
	 * @param device The device to run the snapshot on
	 * @param comments Any comment about the task
	 * @param author The author of the task
	 * @param automatic Is it an automatic snapshot?
	 * @param dontRunDiagnostics Set to the true to disable running diagnostics
	 * @param dontCheckCompliance Set to true to disable compliance checking
	 */
	public TakeSnapshotTask(Device device, String comments, String author, boolean automatic,
		boolean dontRunDiagnostics, boolean dontCheckCompliance) {
		super(comments, device.getLastConfig() == null ? device.getMgmtAddress() : device.getName(),
			author);
		this.setDevice(device);
		this.setAutomatic(automatic);
		this.setDontRunDiagnostics(dontRunDiagnostics);
		this.setDontCheckCompliance(dontCheckCompliance);
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#deviceLockPolicy()
	 */
	@Override
	public DeviceLockPolicy deviceLockPolicy() {
		return DeviceLockPolicy.CANCEL_IF_SAME_TYPE;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#deviceLockTypeNamespace()
	 */
	@Override
	public int deviceLockTypeNamespace() {
		return TaskLockManager.NS_SNAPSHOT;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getTaskDescription()
	 */
	@Override
	@Transient
	public String getTaskDescription() {
		return "Device snapshot";
	}

	/**
	 * Get the ID of the device.
	 * 
	 * @return the ID of the device
	 */
	@Transient
	protected long getDeviceId() {
		if (this.device == null) {
			return 0;
		}
		return device.getId();
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		TakeSnapshotTask task = (TakeSnapshotTask) super.clone();
		task.setDevice(this.device);
		return task;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#onSchedule()
	 */
	@Override
	public void onSchedule() {
		if (this.isAutomatic()) {
			AutoSnapshotScheduler.checkAutoSnapshot(this.getDevice().getId());
		}
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#onCancel()
	 */
	@Override
	public void onCancel() {
		AutoSnapshotScheduler.clearScheduledAutoSnapshot(this.device.getId());
	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getIdentity()
	 */
	@Override
	@Transient
	public JobKey getIdentity() {
		return new JobKey(String.format("Task_%d", this.getId()),
			String.format("RunDevice_%d", this.getDeviceId()));
	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getRunnerHash()
	 */
	@Override
	@Transient
	public long getRunnerHash() {
		return this.getDeviceId();
	}

}
