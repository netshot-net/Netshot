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
package net.netshot.netshot.api.rest.v1.group;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;

/**
 * The definition of a device group, to create one ({@code POST}) or replace one ({@code PUT}, and the result of a
 * {@code PATCH}). The read-only properties of the representation ({@link GroupDto}) are ignored.
 *
 * @param name the name
 * @param folder the folder (none if absent)
 * @param type STATIC or DYNAMIC (it can't change)
 * @param hiddenFromReports whether the group is hidden from the reports (false if absent)
 * @param query the query selecting the devices of a dynamic group (the default query if absent)
 * @param devices the IDs of the devices of a static group, in order (write-only: absent means unchanged, no
 *        device for a new group)
 */
@Schema(name = "GroupRequest", description = "Definition of a device group")
@ReadOnlyFrom(GroupDto.class)
public record GroupRequest(
	@JsonProperty(required = true) String name,
	String folder,
	@JsonProperty(required = true) @Schema(allowableValues = {"STATIC", "DYNAMIC"}) String type,
	boolean hiddenFromReports,
	String query,
	@Schema(description = "IDs of the devices of a static group (write-only: absent means unchanged)") List<Long> devices) {
}
