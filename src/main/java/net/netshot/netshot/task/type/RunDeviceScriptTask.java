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

import java.util.Map;

import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.Task;

/**
 * Runs a user script on a device.
 *
 * @param deviceId the ID of the device (null if it was deleted)
 * @param script the script
 * @param deviceDriver the name of the driver the script is written for
 * @param userInputValues the values of the user inputs of the script (or null)
 * @param runSnapshot true to take a snapshot afterwards
 * @param runDiagnostics true to run the diagnostics afterwards
 * @param checkCompliance true to check the compliance afterwards
 */
public record RunDeviceScriptTask(Long deviceId, String script, String deviceDriver, Map<String, String> userInputValues,
	boolean runSnapshot, boolean runDiagnostics, boolean checkCompliance) implements TaskSpec, DeviceBasedTask {

	/**
	 * Builds a new script task.
	 *
	 * @param device the device
	 * @param spec the specification (its device ID is ignored)
	 * @param comments the comments
	 * @param author the author
	 * @return the new task
	 */
	public static Task of(Device device, RunDeviceScriptTask spec, String comments, String author) {
		return Task.of(new RunDeviceScriptTask(device.id(), spec.script, spec.deviceDriver, spec.userInputValues,
			spec.runSnapshot, spec.runDiagnostics, spec.checkCompliance), comments, DeviceBasedTask.targetOf(device), author);
	}

	@Override
	public String description() {
		return "Device script execution";
	}

	@Override
	public String identityGroup() {
		return DeviceBasedTask.identityGroup("RunDevice", this.deviceId);
	}

	@Override
	public Task.DeviceLockPolicy deviceLockPolicy() {
		// Each script run is unique: never cancelled because of another script, waits for its turn
		return Task.DeviceLockPolicy.WAIT;
	}
}
