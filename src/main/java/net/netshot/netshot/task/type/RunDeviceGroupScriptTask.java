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
import java.util.Map;

/**
 * Runs a user script (one child task per device), for the devices of a group or a list of devices.
 *
 * @param deviceGroupId the ID of the target group (null for a device list)
 * @param deviceIds the IDs of the target devices, when there is no group
 * @param script the script
 * @param deviceDriver the name of the driver the script is written for
 * @param userInputValues the values of the user inputs of the script (or null)
 * @param runSnapshot true to take a snapshot of each device afterwards
 * @param runDiagnostics true to run the diagnostics on each device afterwards
 * @param checkCompliance true to check the compliance of each device afterwards
 */
public record RunDeviceGroupScriptTask(Long deviceGroupId, List<Long> deviceIds, String script, String deviceDriver, Map<String, String> userInputValues,
	boolean runSnapshot, boolean runDiagnostics, boolean checkCompliance) implements TaskSpec, GroupBasedTask, ChildOrchestratingTask {

	/**
	 * Canonical constructor: a null list of devices is replaced by an empty one.
	 */
	public RunDeviceGroupScriptTask {
		deviceIds = deviceIds == null ? List.of() : List.copyOf(deviceIds);
	}

	@Override
	public String description() {
		return "Group script execution";
	}

	@Override
	public String identityGroup() {
		return GroupBasedTask.identityGroup("RunDeviceGroupScript", this.deviceGroupId);
	}
}
