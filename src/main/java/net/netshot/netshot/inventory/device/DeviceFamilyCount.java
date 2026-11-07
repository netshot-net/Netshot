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
package net.netshot.netshot.inventory.device;

/**
 * A device family with the number of devices having it, projected by {@link DeviceInventoryRepository#findFamilyCounts}.
 *
 * @param driver the name of the device driver
 * @param family the family (null for the devices which have none)
 * @param deviceCount the number of devices of the driver and family
 */
public record DeviceFamilyCount(String driver, String family, long deviceCount) {
}
