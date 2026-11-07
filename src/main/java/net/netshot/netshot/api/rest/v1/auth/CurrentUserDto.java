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
package net.netshot.netshot.api.rest.v1.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The principal of a request: the user of a session or of a JWT access token, or the API token.
 *
 * @param id the ID of the user or of the API token (0 for a remote user who isn't registered in Netshot)
 * @param url the path of the user or of the API token (null if there is none)
 * @param display the display name (the user name, or the description of the API token)
 * @param kind {@code USER} or {@code API_TOKEN}
 * @param authMethod how the request was authenticated: {@code SESSION}, {@code JWT} or {@code API_TOKEN}
 * @param username the user name (null for an API token)
 * @param local whether the user is a Netshot local user, authenticated by its password (null for an API token)
 * @param level the permission level
 * @param role the name of the highest role the level gives (null if the level is below the lowest one)
 */
@Schema(name = "CurrentUser", description = "The principal (user or API token) a request is authenticated as")
public record CurrentUserDto(
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) long id,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY, nullable = true) String url,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String display,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String kind,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String authMethod,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY, nullable = true) String username,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY, nullable = true) Boolean local,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) int level,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY, nullable = true) String role) {
}
