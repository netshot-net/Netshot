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
package net.netshot.netshot.task.type;

import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.engine.TaskLockManager;

/**
 * Runs the diagnostics on a device.
 *
 * @param deviceId the ID of the device (null if it was deleted)
 * @param dontCheckCompliance true not to check the compliance afterwards
 */
public record RunDiagnosticsTask(Long deviceId, boolean dontCheckCompliance) implements TaskSpec, DeviceBasedTask {

	/**
	 * Builds a new diagnostics task.
	 *
	 * @param device the device
	 * @param comments the comments
	 * @param author the author
	 * @param dontCheckCompliance true not to check the compliance afterwards
	 * @return the new task
	 */
	public static Task of(Device device, String comments, String author, boolean dontCheckCompliance) {
		return Task.of(new RunDiagnosticsTask(device.id(), dontCheckCompliance), comments, DeviceBasedTask.targetOf(device), author);
	}

	@Override
	public String description() {
		return "Device diagnostics";
	}

	@Override
	public String identityGroup() {
		return DeviceBasedTask.identityGroup("RunDevice", this.deviceId);
	}

	@Override
	public long runnerHash() {
		return this.deviceId == null ? 0 : this.deviceId;
	}

	@Override
	public Task.DeviceLockPolicy deviceLockPolicy() {
		return Task.DeviceLockPolicy.CANCEL_IF_SAME_TYPE;
	}

	@Override
	public int deviceLockTypeNamespace() {
		return TaskLockManager.NS_DIAGNOSTICS;
	}
}
