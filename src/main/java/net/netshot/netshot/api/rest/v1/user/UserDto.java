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
package net.netshot.netshot.api.rest.v1.user;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;

/**
 * A Netshot user, local (authenticated by its password) or remote (authenticated by RADIUS, TACACS+ or OIDC; the
 * user is then registered to give it a permission level). The password and its hash are never written.
 *
 * @param id the ID
 * @param url the path of the user
 * @param display the user name
 * @param version the version, incremented on each change (the ETag)
 * @param changeDate the date of the last change
 * @param username the user name
 * @param local whether the user is a local one
 * @param level the permission level (10 read-only, 50 operator, 100 read-write, 500 execute-read-write, 1000 admin)
 * @param role the name of the highest role the level gives
 * @param hasPassword whether the user has a password (local users only)
 * @param lastPasswordChangeDate when the password was last changed (null for a user without password)
 */
@Schema(name = "User", description = "Netshot user")
public record UserDto(
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) long id,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String url,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String display,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) long version,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) Instant changeDate,
	@Brief String username,
	@Brief boolean local,
	@Brief int level,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY, nullable = true) String role,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) boolean hasPassword,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY, nullable = true) Instant lastPasswordChangeDate) {
}
