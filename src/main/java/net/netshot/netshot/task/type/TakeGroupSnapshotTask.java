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

import java.util.List;

/**
 * Takes a snapshot of the devices (one child task per device), for the devices of a group or a list of devices.
 *
 * @param deviceGroupId the ID of the target group (null for a device list)
 * @param deviceIds the IDs of the target devices, when there is no group
 * @param limitToOutofdateDeviceHours only the devices not changed for this number of hours are captured
 * @param dontRunDiagnostics true not to run the diagnostics after the snapshots
 * @param dontCheckCompliance true not to check the compliance after the snapshots
 */
public record TakeGroupSnapshotTask(Long deviceGroupId, List<Long> deviceIds, int limitToOutofdateDeviceHours,
	boolean dontRunDiagnostics, boolean dontCheckCompliance) implements TaskSpec, GroupBasedTask, ChildOrchestratingTask {

	/**
	 * Canonical constructor: a null list of devices is replaced by an empty one.
	 */
	public TakeGroupSnapshotTask {
		deviceIds = deviceIds == null ? List.of() : List.copyOf(deviceIds);
	}

	@Override
	public String description() {
		return "Group snapshot";
	}

	@Override
	public String identityGroup() {
		return GroupBasedTask.identityGroup("TakeGroupSnapshot", this.deviceGroupId);
	}
}
