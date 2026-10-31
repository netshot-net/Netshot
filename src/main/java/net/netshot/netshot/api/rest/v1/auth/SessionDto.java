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
package net.netshot.netshot.api.rest.v1.auth;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The session of a user (the one the session cookie identifies). The session ID is never written: it is only in the
 * cookie.
 *
 * @param url the path of the session
 * @param creationDate when the session was created (the login)
 * @param maxIdleTime the number of seconds of inactivity after which the session expires
 * @param user the user of the session
 */
@Schema(name = "Session", description = "A user session (cookie-authenticated)")
public record SessionDto(
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String url,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) Instant creationDate,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) int maxIdleTime,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) CurrentUserDto user) {
}
