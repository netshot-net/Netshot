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
package net.netshot.netshot.platform.lifecycle;

import java.util.Map;

/**
 * The status of a service.
 *
 * @param name the name of the service
 * @param state the state
 * @param healthy whether the service is running and healthy
 * @param error the error which made it fail (null if none)
 * @param stats the statistics of the service
 */
public record ServiceStatus(String name, ServiceState state, boolean healthy, String error, Map<String, Object> stats) {
}
