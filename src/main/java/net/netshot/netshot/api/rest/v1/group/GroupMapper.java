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
package net.netshot.netshot.api.rest.v1.group;

import java.util.stream.Collectors;

import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.api.rest.v1.StatsResponse;
import net.netshot.netshot.api.rest.v1.RefDto;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.GroupDefinition;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * Maps the device groups to their v1 representation.
 */
public final class GroupMapper {

	/** The path of the groups. */
	public static final String PATH = "groups";

	private GroupMapper() {
	}

	/**
	 * Maps a group.
	 *
	 * @param group the group (null gives null)
	 * @return the representation
	 */
	public static GroupDto toDto(DeviceGroup group) {
		return switch (group) {
			case null -> null;
			case StaticDeviceGroup g -> new GroupDto(g.id(), V1Links.of(PATH, g.id()), g.name(), g.version(), g.changeDate(),
				g.name(), g.folder(), "STATIC", g.hiddenFromReports(), null);
			case DynamicDeviceGroup g -> new GroupDto(g.id(), V1Links.of(PATH, g.id()), g.name(), g.version(), g.changeDate(),
				g.name(), g.folder(), "DYNAMIC", g.hiddenFromReports(), g.query());
		};
	}

	/**
	 * Maps a group to the request which would define it as it is (the base of a merge patch), without its
	 * devices (unchanged unless the patch gives them).
	 *
	 * @param group the group
	 * @return the request
	 */
	public static GroupRequest toRequest(DeviceGroup group) {
		return new GroupRequest(group.name(), group.folder(), group.type().name(), group.hiddenFromReports(),
			group instanceof DynamicDeviceGroup dynamic ? dynamic.query() : null, null);
	}

	/**
	 * Maps a request to the definition the service takes.
	 *
	 * @param request the request
	 * @return the definition
	 * @throws NetshotException if the type is unknown
	 */
	public static GroupDefinition toDefinition(GroupRequest request) {
		String type = switch (request.type() == null ? "" : request.type()) {
			case "STATIC" -> "StaticDeviceGroup";
			case "DYNAMIC" -> "DynamicDeviceGroup";
			default -> throw NetshotException.invalidParameter("type", "Invalid group type: STATIC or DYNAMIC is expected");
		};
		return new GroupDefinition(type, request.name(), request.devices(), request.query(),
			request.folder() == null ? "" : request.folder(), request.hiddenFromReports());
	}

	/**
	 * Builds the resolver of group IDs into references (statistics grouped by group).
	 *
	 * @param groupService the group use cases
	 * @return the resolver
	 */
	public static StatsResponse.Resolver resolver(GroupService groupService) {
		return ids -> groupService.list(ListQuery.byIds(ids)).items().stream()
			.collect(Collectors.toMap(DeviceGroup::id, g -> new RefDto(g.id(), V1Links.of(PATH, g.id()), g.name())));
	}
}
