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
package net.netshot.netshot.inventory.group;

import java.time.Instant;

/**
 * A group of devices. Its members are stored apart (in order), see {@link GroupRepository}.
 */
public sealed interface DeviceGroup permits StaticDeviceGroup, DynamicDeviceGroup {

	/**
	 * The kind of group (as stored).
	 */
	enum Type {
		/** A group of chosen devices. */
		STATIC,
		/** A group of the devices matching a search query. */
		DYNAMIC,
	}

	/**
	 * Gets the kind of the group.
	 *
	 * @return the type
	 */
	default Type type() {
		return switch (this) {
			case StaticDeviceGroup _ -> Type.STATIC;
			case DynamicDeviceGroup _ -> Type.DYNAMIC;
		};
	}

	/**
	 * Gets the ID.
	 *
	 * @return the ID (0 for a group which isn't stored yet)
	 */
	long id();

	/**
	 * Gets the version.
	 *
	 * @return the version, incremented on each change
	 */
	int version();

	/**
	 * Gets the time of the last change.
	 *
	 * @return the time
	 */
	Instant changeDate();

	/**
	 * Gets the name.
	 *
	 * @return the name, unique
	 */
	String name();

	/**
	 * Gets the folder the group is shown in.
	 *
	 * @return the folder
	 */
	String folder();

	/**
	 * Tells whether the group is hidden from the reports.
	 *
	 * @return true if the group is hidden
	 */
	boolean hiddenFromReports();

	/**
	 * Describes a group (for the logs).
	 *
	 * @param group the group
	 * @return the description
	 */
	static String describe(DeviceGroup group) {
		return "Device Group " + group.id() + " (name '" + group.name() + "')";
	}
}
