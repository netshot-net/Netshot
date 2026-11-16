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
package net.netshot.netshot.devicescript.helper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.netshot.netshot.access.DeviceReads;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResult;

/**
 * What the device helpers read from the database while a script runs (each read in a short
 * transaction of its own): the other devices, the last configurations and the diagnostic
 * results. The configurations are cached.
 */
final class DeviceLookup {

	/** The reads of the database. */
	private final DeviceReads reads;
	private final Map<Long, Config> configs = new HashMap<>();

	/**
	 * Instantiates the lookup.
	 *
	 * @param reads the reads of the database
	 */
	DeviceLookup(DeviceReads reads) {
		this.reads = reads;
	}

	/**
	 * Finds a device by ID.
	 *
	 * @param id the device ID
	 * @return the device, or null if there is no such device
	 */
	Device findDevice(long id) {
		return this.reads.findDevice(id);
	}

	/**
	 * Finds a device by name.
	 *
	 * @param name the device name
	 * @return the device, or null if there is no such device
	 */
	Device findDevice(String name) {
		return this.reads.findDevice(name);
	}

	/**
	 * Gets the last configuration of a device, with its long texts.
	 *
	 * @param device the device
	 * @return the configuration, or null if the device has none
	 */
	Config lastConfig(Device device) {
		Long configId = device.lastConfigId();
		if (configId == null) {
			return null;
		}
		return this.configs.computeIfAbsent(configId, this.reads::findConfig);
	}

	/**
	 * Gets the diagnostic results of a device.
	 *
	 * @param deviceId the device ID
	 * @return the results
	 */
	List<DiagnosticResult> diagnosticResults(long deviceId) {
		return this.reads.findDiagnosticResults(deviceId);
	}
}
