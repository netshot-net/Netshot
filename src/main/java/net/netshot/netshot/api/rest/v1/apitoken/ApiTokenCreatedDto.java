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
package net.netshot.netshot.api.rest.v1.apitoken;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A newly created API token, with its value. This is the only time the value of the token is given: it can't be read
 * afterwards.
 *
 * @param id the ID
 * @param url the path of the API token
 * @param display the description (or a name made of the ID if there is none)
 * @param description the description
 * @param level the permission level
 * @param role the name of the highest role the level gives
 * @param token the token value (32 alphanumeric characters), to send in the {@code X-Netshot-API-Token} header
 */
@Schema(name = "ApiTokenCreated", description = "A newly created API token, with its value (only given once)")
public record ApiTokenCreatedDto(
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) long id,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String url,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String display,
	String description,
	int level,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY, nullable = true) String role,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY, format = "password") String token) {
}
