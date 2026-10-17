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
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.Task;

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

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#deviceLockPolicy()
	 */
	@Override
	public DeviceLockPolicy deviceLockPolicy() {
		// Each script run is unique: never cancelled because of another script, waits for its turn
		return DeviceLockPolicy.WAIT;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getTaskDescription()
	 */
	@Override
	@Transient
	public String getTaskDescription() {
		return "Device script execution";
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
	 * @see net.netshot.netshot.task.Task#getIdentity()
	 */
	@Override
	@Transient
	public JobKey getIdentity() {
		return new JobKey(String.format("Task_%d", this.getId()),
			String.format("RunDevice_%d", this.getDeviceId()));
	}
}
