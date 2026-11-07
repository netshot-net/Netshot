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

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;

/**
 * A device group.
 *
 * @param id the ID
 * @param url the path of the group
 * @param display the display name
 * @param version the version
 * @param changeDate the date of the last change
 * @param name the name
 * @param folder the folder
 * @param type STATIC or DYNAMIC
 * @param hiddenFromReports whether the group is hidden from the reports
 * @param query the query selecting the devices of a dynamic group (null for a static group)
 */
@Schema(name = "Group", description = "Device group")
public record GroupDto(
	long id,
	String url,
	String display,
	long version,
	Instant changeDate,
	@Brief String name,
	@Brief String folder,
	@Brief String type,
	boolean hiddenFromReports,
	String query) {
}
