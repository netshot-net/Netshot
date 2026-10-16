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

import java.util.List;

/**
 * What the client gives to add a group or to edit one. The name is given as it comes from the
 * client (the service trims it).
 *
 * @param type the kind of group to add: {@code StaticDeviceGroup} or {@code DynamicDeviceGroup}
 *        (not used when editing: the kind of a group can't change)
 * @param name the name
 * @param staticDevices the IDs of the devices of a static group, in order (not used by a dynamic group)
 * @param query the search query of a dynamic group (not used by a static group)
 * @param folder the folder containing the group
 * @param hiddenFromReports whether the group is hidden in the reports
 */
public record GroupDefinition(
	String type,
	String name,
	List<Long> staticDevices,
	String query,
	String folder,
	boolean hiddenFromReports) {
}
