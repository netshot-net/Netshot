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
package net.netshot.netshot.api.rest.v1.system;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * General information about the server.
 *
 * @param version the version of Netshot
 * @param serverTime the current time of the server
 * @param sessionMaxIdleTime the number of seconds of inactivity after which a user session expires
 */
@Schema(name = "SystemInfo", description = "General information about the Netshot server")
public record SystemInfoDto(
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String version,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) Instant serverTime,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) int sessionMaxIdleTime) {
}
