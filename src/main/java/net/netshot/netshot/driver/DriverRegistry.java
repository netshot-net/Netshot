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
package net.netshot.netshot.driver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registry of the loaded device drivers: lookups by name or description, and the global hash of
 * the drivers. The drivers are read by a {@link DriverLoader} on {@link #refresh()}.
 */
public class DriverRegistry {

	/**
	 * The process-wide registry behind the deprecated static lookups of {@link DeviceDriver}.
	 * Transitional: the wiring uses it too, so that code which cannot be injected (entities,
	 * tests which load the drivers before the wiring exists) and injected code see the same drivers.
	 */
	private static final DriverRegistry SHARED = new DriverRegistry(new DriverLoader());

	private final DriverLoader loader;

	/** The list of loaded drivers. */
	private volatile Map<String, DeviceDriver> drivers = new ConcurrentHashMap<>();

	/** Hash for all drivers. */
	private volatile String allDriverHash = "";

	/**
	 * Creates a registry.
	 *
	 * @param loader the loader reading the drivers on refresh
	 */
	public DriverRegistry(DriverLoader loader) {
		this.loader = loader;
	}

	/**
	 * Gets the process-wide registry (transitional, see {@link #SHARED}).
	 *
	 * @return the registry
	 */
	public static DriverRegistry shared() {
		return SHARED;
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
		DriverLoader.LoadedDrivers loaded = this.loader.load();
		this.allDriverHash = loaded.hash();
		this.drivers = loaded.drivers();
	}
}
