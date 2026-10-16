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
package net.netshot.netshot.inventory.config;

import java.util.Date;

import net.netshot.netshot.inventory.device.Device;

/**
 * The few attributes of a configuration needed to list configuration changes, projected by
 * {@link ConfigRepository#findSummaries} without loading the configurations.
 *
 * @param id the configuration ID
 * @param deviceName the name of the device
 * @param deviceId the ID of the device
 * @param changeDate the date of the change
 * @param author the author of the change
 * @param deviceNetworkClass the network class of the device
 */
public record ConfigSummary(
	long id,
	String deviceName,
	long deviceId,
	Date changeDate,
	String author,
	Device.NetworkClass deviceNetworkClass) {
}
