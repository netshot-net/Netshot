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
 * Takes a snapshot of a device.
 *
 * @param deviceId the ID of the device (null if it was deleted)
 * @param automatic true for a snapshot scheduled automatically (e.g. after a configuration change)
 * @param dontRunDiagnostics true not to run the diagnostics afterwards
 * @param dontCheckCompliance true not to check the compliance afterwards
 */
public record TakeSnapshotTask(Long deviceId, boolean automatic, boolean dontRunDiagnostics, boolean dontCheckCompliance)
	implements TaskSpec, DeviceBasedTask {

	/**
	 * Builds a new snapshot task.
	 *
	 * @param device the device
	 * @param comments the comments
	 * @param author the author
	 * @param automatic true for an automatic snapshot
	 * @param dontRunDiagnostics true not to run the diagnostics afterwards
	 * @param dontCheckCompliance true not to check the compliance afterwards
	 * @return the new task
	 */
	public static Task of(Device device, String comments, String author, boolean automatic,
			boolean dontRunDiagnostics, boolean dontCheckCompliance) {
		return Task.of(new TakeSnapshotTask(device.id(), automatic, dontRunDiagnostics, dontCheckCompliance),
			comments, DeviceBasedTask.targetOf(device), author);
	}

	@Override
	public String description() {
		return "Device snapshot";
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
		return TaskLockManager.NS_SNAPSHOT;
	}
}
