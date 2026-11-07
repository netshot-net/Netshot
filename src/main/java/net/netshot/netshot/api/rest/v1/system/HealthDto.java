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

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The health of the server, as probed by a load balancer or an orchestrator. It holds nothing else on purpose: the
 * endpoint is not authenticated.
 *
 * @param status {@code UP} if all the services of the server are running and healthy, {@code DOWN} otherwise
 */
@Schema(name = "Health", description = "Liveness and readiness of the server")
public record HealthDto(String status) {
}
