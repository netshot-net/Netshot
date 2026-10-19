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
package net.netshot.netshot;

import net.netshot.netshot.driver.DriverLoader;
import net.netshot.netshot.driver.DriverRegistry;

/**
 * Test fixture: a registry holding the drivers of the {@code drivers} resource folder, loaded
 * once per test JVM, to hand to whatever needs a {@link DriverRegistry}.
 */
public final class TestDrivers {

	private static DriverRegistry registry;

	private TestDrivers() {
		// Static helpers only
	}

	/**
	 * Gets the registry shared by the tests, loading the drivers the first time.
	 *
	 * @return the loaded registry
	 */
	public static synchronized DriverRegistry registry() {
		if (registry == null) {
			DriverRegistry loading = new DriverRegistry(new DriverLoader());
			try {
				loading.refresh();
			}
			catch (Exception e) {
				throw new IllegalStateException("Unable to load the drivers", e);
			}
			registry = loading;
		}
		return registry;
	}

	/**
	 * Reloads the drivers of the shared registry from their sources.
	 *
	 * @return the registry
	 * @throws Exception if the drivers can't be loaded
	 */
	public static synchronized DriverRegistry reload() throws Exception {
		DriverRegistry shared = registry();
		shared.refresh();
		return shared;
	}

}
