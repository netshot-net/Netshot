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
package net.netshot.netshot.devicescript.helper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.diagnostic.DiagnosticResultRepository;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.ConfigRepository;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.platform.database.Transactions;

/**
 * What the device helpers read from the database while a script runs (each read in a short
 * transaction of its own): the other devices, the last configurations and the diagnostic
 * results. The configurations are cached.
 */
final class DeviceLookup {

	private final Transactions transactions = new Transactions();
	private final DeviceRepository deviceRepository = new DeviceRepository();
	private final ConfigRepository configRepository = new ConfigRepository();
	private final DiagnosticResultRepository diagnosticResultRepository = new DiagnosticResultRepository();
	private final Map<Long, Config> configs = new HashMap<>();

	/**
	 * Finds a device by ID.
	 *
	 * @param id the device ID
	 * @return the device, or null if there is no such device
	 */
	Device findDevice(long id) {
		return this.transactions.read(dsl -> this.deviceRepository.find(dsl, id));
	}

	/**
	 * Finds a device by name.
	 *
	 * @param name the device name
	 * @return the device, or null if there is no such device
	 */
	Device findDevice(String name) {
		return this.transactions.read(dsl -> this.deviceRepository.findByName(dsl, name));
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
		return this.configs.computeIfAbsent(configId, id -> this.transactions.read(dsl -> this.configRepository.find(dsl, id, true)));
	}

	/**
	 * Gets the diagnostic results of a device.
	 *
	 * @param deviceId the device ID
	 * @return the results
	 */
	List<DiagnosticResult> diagnosticResults(long deviceId) {
		return this.transactions.read(dsl -> this.diagnosticResultRepository.findByDevice(dsl, deviceId));
	}
}
