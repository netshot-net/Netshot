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
package net.netshot.netshot.api.rest.v1.report;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A reference to an object a report row is about (a group, a policy, a rule), by its ID, path and name.
 *
 * @param id the ID
 * @param url the path of the object
 * @param display the display name
 */
@Schema(name = "ReportObject", description = "Reference to a group, policy or rule")
public record ReportObjectDto(long id, String url, String display) {
}
