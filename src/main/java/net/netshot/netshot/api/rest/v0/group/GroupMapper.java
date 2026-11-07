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
package net.netshot.netshot.api.rest.v0.group;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.GroupDefinition;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;

/**
 * Maps the device groups between the records of the inventory and their REST representation.
 * All the methods are pure functions.
 */
public final class GroupMapper {

	private GroupMapper() {
		// Static helpers only
	}

	private static Date date(Instant instant) {
		return instant == null ? null : Date.from(instant);
	}

	/**
	 * Maps a device group to its representation (the members are not part of it).
	 *
	 * @param group the group
	 * @return the DTO
	 */
	public static DeviceGroupDto toDto(DeviceGroup group) {
		return switch (group) {
			case DynamicDeviceGroup dynamic -> new DeviceGroupDto.DynamicGroup(dynamic.id(), date(dynamic.changeDate()), dynamic.name(),
				dynamic.folder(), dynamic.hiddenFromReports(), dynamic.query());
			case StaticDeviceGroup staticGroup -> new DeviceGroupDto.StaticGroup(staticGroup.id(), date(staticGroup.changeDate()),
				staticGroup.name(), staticGroup.folder(), staticGroup.hiddenFromReports());
		};
	}

	/**
	 * Maps the device groups to their representation.
	 *
	 * @param groups the groups
	 * @return the DTOs, in the same order
	 */
	public static List<DeviceGroupDto> toDtos(List<DeviceGroup> groups) {
		List<DeviceGroupDto> dtos = new ArrayList<>(groups.size());
		groups.forEach(group -> dtos.add(toDto(group)));
		return dtos;
	}

	/**
	 * Maps a request to add or edit a group to the definition given to the service.
	 *
	 * @param request the request
	 * @return the definition
	 */
	public static GroupDefinition toDefinition(GroupRequest request) {
		return new GroupDefinition(request.getType(), request.getName(), request.getStaticDevices(),
			request.getQuery(), request.getFolder(), request.isHiddenFromReports());
	}
}
