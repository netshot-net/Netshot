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
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.cluster.ClusterManager;

/**
 * The device driver use case: list the device types (the drivers loaded by the server), and
 * optionally reload them all from their sources first.
 *
 * <p>The drivers are held by a static registry ({@link DeviceDriver}), not by the database.</p>
 */
@Slf4j
public class DeviceDriverService {

	/**
	 * Lists the device types.
	 *
	 * <p>The caller is the one to check that the user may reload the drivers.</p>
	 *
	 * @param refresh whether to reload all the drivers from their sources (on the other cluster nodes too) first
	 * @return the drivers (a reload which fails is logged, and the previous drivers are returned)
	 */
	public List<DeviceDriver> list(boolean refresh) {
		if (refresh) {
			ClusterManager.requestDriverReload();
			try {
				DeviceDriver.refreshDrivers();
			}
			catch (Exception e) {
				log.error("Error while refreshing the device types.", e);
			}
		}
		return new ArrayList<>(DeviceDriver.getAllDrivers());
	}
}
