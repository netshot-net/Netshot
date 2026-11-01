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
package net.netshot.netshot.api.rest.v1;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A minimal reference to another object, where its full representation doesn't apply (a value statistics are
 * grouped by...).
 *
 * @param id the ID
 * @param url the path of the object
 * @param display the display name
 */
@Schema(name = "Ref", description = "Reference to an object")
public record RefDto(long id, String url, String display) {
}
