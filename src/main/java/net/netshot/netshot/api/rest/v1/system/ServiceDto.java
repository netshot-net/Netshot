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
package net.netshot.netshot.api.rest.v1.system;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A service of the server (task manager, Syslog server, web server...).
 *
 * @param display the name of the service
 * @param name the name of the service
 * @param state the state ({@code NEW}, {@code STARTING}, {@code RUNNING}, {@code STOPPING}, {@code STOPPED} or
 *        {@code FAILED})
 * @param healthy whether the service is running and works as expected
 * @param error the error which made the service fail (null if none)
 * @param stats the statistics of the service (free-form, they depend on the service)
 */
@Schema(name = "Service", description = "Service of the Netshot server")
public record ServiceDto(
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String display,
	String name,
	String state,
	boolean healthy,
	@Schema(nullable = true) String error,
	Map<String, Object> stats) {
}
