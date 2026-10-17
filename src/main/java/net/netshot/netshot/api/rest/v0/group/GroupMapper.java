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
package net.netshot.netshot.api.rest.v0.group;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;

import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.GroupDefinition;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;

/**
 * Maps the device groups between the entities of the inventory and their REST representation.
 * All the methods are pure functions.
 */
public final class GroupMapper {

	private GroupMapper() {
		// Static helpers only
	}

	/**
	 * Maps a device group to its representation (the members are not part of it).
	 *
	 * @param groupOrProxy the group, or a proxy of it
	 * @return the DTO
	 */
	public static DeviceGroupDto toDto(DeviceGroup groupOrProxy) {
		DeviceGroup group = (DeviceGroup) Hibernate.unproxy(groupOrProxy);
		if (group instanceof DynamicDeviceGroup dynamic) {
			return new DeviceGroupDto.DynamicGroup(dynamic.getId(), dynamic.getChangeDate(), dynamic.getName(),
				dynamic.getFolder(), dynamic.isHiddenFromReports(), dynamic.getQuery());
		}
		else if (group instanceof StaticDeviceGroup) {
			return new DeviceGroupDto.StaticGroup(group.getId(), group.getChangeDate(), group.getName(),
				group.getFolder(), group.isHiddenFromReports());
		}
		throw new IllegalArgumentException("Unknown kind of device group: " + group.getClass().getName());
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
