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

import net.netshot.netshot.inventory.device.Device;

/**
 * A task working on one device.
 */
public interface DeviceBasedTask {

	/**
	 * Gets the ID of the target device.
	 *
	 * @return the device ID, null if the device was deleted
	 */
	Long deviceId();

	/**
	 * Gets the description of a device as a task target: its name, or its management address
	 * when it was never captured.
	 *
	 * @param device the device
	 * @return the target description
	 */
	static String targetOf(Device device) {
		return device.lastConfigId() == null ? device.mgmtAddress() : device.name();
	}

	/**
	 * Gets the identity group of a device task, from a prefix.
	 *
	 * @param prefix the prefix
	 * @param deviceId the device ID (or null)
	 * @return the identity group
	 */
	static String identityGroup(String prefix, Long deviceId) {
		return String.format("%s_%d", prefix, deviceId == null ? 0 : deviceId);
	}
}
