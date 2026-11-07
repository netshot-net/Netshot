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

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The mastership status of the server answering.
 *
 * @param clusterEnabled whether clustering is enabled
 * @param master whether the server is the master (true if clustering is not enabled)
 * @param currentMasterId the instance ID of the current master, if any
 */
@Schema(name = "ClusterMaster", description = "Mastership status of the server in the high availability cluster")
public record ClusterMasterDto(
	boolean clusterEnabled,
	boolean master,
	@Schema(nullable = true) String currentMasterId) {
}
