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
package net.netshot.netshot.api.rest.v0.system;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.system.ServerInfoService;

/**
 * The REST resource of the general server information. The class is mounted on {@code /} with
 * the full path on each method.
 */
@Path("/")
@Slf4j
public class ServerInfoResource {

	private final ServerInfoService serverInfoService;

	/**
	 * Instantiates the resource.
	 *
	 * @param serverInfoService the server information use case
	 */
	@Inject
	public ServerInfoResource(ServerInfoService serverInfoService) {
		this.serverInfoService = serverInfoService;
	}

	@GET
	@Path("/serverinfo")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get Netshot server info.",
		description = "Retrieves some general info about Netshot server."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public ServerInfoDto getServerInfo() {
		log.debug("REST request, get server info");
		return SystemMapper.toDto(this.serverInfoService.get());
	}
}
