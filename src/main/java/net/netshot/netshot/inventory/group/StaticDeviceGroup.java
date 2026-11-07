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
package net.netshot.netshot.inventory.group;

import java.time.Instant;

import lombok.Builder;

/**
 * A static group of devices.
 * The devices are manually listed.
 *
 * @param id the ID (0 for a group which isn't stored yet)
 * @param version the version, incremented on each change
 * @param changeDate the time of the last change
 * @param name the name, unique
 * @param folder the folder the group is shown in
 * @param hiddenFromReports whether the group is hidden from the reports
 */
@Builder(toBuilder = true)
public record StaticDeviceGroup(
	long id,
	int version,
	Instant changeDate,
	String name,
	String folder,
	boolean hiddenFromReports) implements DeviceGroup {

	@Override
	public String toString() {
		return DeviceGroup.describe(this);
	}
}
