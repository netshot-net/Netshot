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
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.engine.TaskLockManager;

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
		return TaskLockManager.NS_DIAGNOSTICS;
	}

	@Override
	@Transient
	public String getTaskDescription() {
		return "Device diagnostics";
	}

	/**
	 * Get the ID of the device.
	 * 
	 * @return the ID of the device
	 */
	@Transient
	public long getDeviceId() {
		if (this.device == null) {
			return 0;
		}
		return this.device.getId();
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		RunDiagnosticsTask task = (RunDiagnosticsTask) super.clone();
		task.setDevice(this.device);
		return task;
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
