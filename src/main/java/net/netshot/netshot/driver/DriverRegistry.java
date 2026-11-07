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
package net.netshot.netshot.driver;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import net.netshot.netshot.platform.lifecycle.Service;

/**
 * The registry of the loaded device drivers: lookups by name or description, and the global hash of
 * the drivers. The drivers are read by a {@link DriverLoader} on {@link #refresh()}.
 */
public class DriverRegistry implements Service {

	private final DriverLoader loader;

	/** The list of loaded drivers. */
	private volatile Map<String, DeviceDriver> drivers = new ConcurrentHashMap<>();

	/** Hash for all drivers. */
	private volatile String allDriverHash = "";

	/** The number of (successful) loads of the drivers. */
	private final AtomicLong loads = new AtomicLong();

	/** The number of failed loads of the drivers. */
	private final AtomicLong failedLoads = new AtomicLong();

	/** When the drivers were last loaded. */
	private volatile Instant lastLoad;

	/**
	 * Creates a registry.
	 *
	 * @param loader the loader reading the drivers on refresh
	 */
	public DriverRegistry(DriverLoader loader) {
		this.loader = loader;
	}

	/**
	 * Gets all loaded drivers.
	 *
	 * @return the loaded drivers, sorted
	 */
	public List<DeviceDriver> getAllDrivers() {
		List<DeviceDriver> allDrivers = new ArrayList<>(this.drivers.values());
		Collections.sort(allDrivers);
		return allDrivers;
	}

	/**
	 * Gets a driver from its name.
	 *
	 * @param name
	 *                 the name of the device driver
	 * @return the device driver from that name, or null if not found
	 */
	public DeviceDriver getDriverByName(String name) {
		if (name == null) {
			return null;
		}
		return this.drivers.get(name);
	}

	/**
	 * Gets the driver of a device.
	 *
	 * @param device the device
	 * @return the driver of the device
	 * @throws MissingDeviceDriverException if the device has no driver, or if its driver isn't loaded
	 */
	public DeviceDriver forDevice(DrivenDevice device) throws MissingDeviceDriverException {
		return this.forDevice(device.deviceId(), device.driverName());
	}

	/**
	 * Gets the driver of a device.
	 *
	 * @param deviceId the ID of the device
	 * @param driverName the name of the driver of the device
	 * @return the driver of the device
	 * @throws MissingDeviceDriverException if the device has no driver, or if its driver isn't loaded
	 */
	public DeviceDriver forDevice(long deviceId, String driverName) throws MissingDeviceDriverException {
		if (driverName == null) {
			String message = String.format("The device with ID %d has no associated driver.", deviceId);
			throw new MissingDeviceDriverException(message);
		}
		DeviceDriver deviceDriver = this.getDriverByName(driverName);
		if (deviceDriver == null) {
			String message = String.format("Unable to locate the driver %s for device %d.", driverName, deviceId);
			throw new MissingDeviceDriverException(message);
		}
		return deviceDriver;
	}

	/**
	 * Gets a driver by description.
	 *
	 * @param description The description to look for
	 * @return the device driver from that name, or null if not found
	 */
	public DeviceDriver getDriverByDescription(String description) {
		if (description == null) {
			return null;
		}
		for (DeviceDriver driver : this.getAllDrivers()) {
			if (description.equalsIgnoreCase(driver.getDescription())) {
				return driver;
			}
		}
		return null;
	}

	/**
	 * Gets the loaded drivers by name (the live map).
	 *
	 * @return the loaded drivers
	 */
	public Map<String, DeviceDriver> getDrivers() {
		return this.drivers;
	}

	/**
	 * Get a global hash for all drivers combined.
	 *
	 * @return the hash (hex-encoded string)
	 */
	public String getAllDriverHash() {
		return this.allDriverHash;
	}

	/**
	 * Reloads all the drivers from their sources.
	 *
	 * @throws Exception
	 *                       something bad
	 */
	public void refresh() throws Exception {
		DriverLoader.LoadedDrivers loaded;
		try {
			loaded = this.loader.load();
		}
		catch (Exception e) {
			this.failedLoads.incrementAndGet();
			throw e;
		}
		this.allDriverHash = loaded.hash();
		this.drivers = loaded.drivers();
		this.loads.incrementAndGet();
		this.lastLoad = Instant.now();
	}

	@Override
	public String name() {
		return "device drivers";
	}

	/**
	 * Loads the drivers (the service has nothing else to start).
	 *
	 * @throws Exception if the drivers can't be loaded
	 */
	@Override
	public void start() throws Exception {
		this.refresh();
	}

	@Override
	public void stop() {
		// Nothing to release
	}

	@Override
	public Map<String, Object> stats() {
		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("drivers", this.drivers.size());
		stats.put("loads", this.loads.get());
		stats.put("failedLoads", this.failedLoads.get());
		Instant last = this.lastLoad;
		stats.put("lastLoad", last == null ? null : last.toString());
		return stats;
	}
}
