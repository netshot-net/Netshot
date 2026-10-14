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
package net.netshot.netshot.api.rest.v0.device;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;

import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * A device group, as nested in the device representation (the groups a device belongs to).
 * The {@code type} property tells the kind of group, with the names of the entity classes.
 */
@JsonView(DefaultView.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = DeviceGroupDto.DynamicGroup.class, name = "DynamicDeviceGroup"),
	@JsonSubTypes.Type(value = DeviceGroupDto.StaticGroup.class, name = "StaticDeviceGroup"),
})
public sealed interface DeviceGroupDto {

	/**
	 * A group whose members are given by a search query.
	 *
	 * @param id the group ID
	 * @param changeDate the last change date
	 * @param name the name
	 * @param folder the folder containing the group
	 * @param hiddenFromReports whether the group is hidden in the reports
	 * @param query the search query
	 */
	@JsonView(DefaultView.class)
	record DynamicGroup(
		long id,
		Date changeDate,
		String name,
		String folder,
		boolean hiddenFromReports,
		String query) implements DeviceGroupDto {
	}

	/**
	 * A group whose members are manually chosen.
	 *
	 * @param id the group ID
	 * @param changeDate the last change date
	 * @param name the name
	 * @param folder the folder containing the group
	 * @param hiddenFromReports whether the group is hidden in the reports
	 */
	@JsonView(DefaultView.class)
	record StaticGroup(
		long id,
		Date changeDate,
		String name,
		String folder,
		boolean hiddenFromReports) implements DeviceGroupDto {
	}
}
