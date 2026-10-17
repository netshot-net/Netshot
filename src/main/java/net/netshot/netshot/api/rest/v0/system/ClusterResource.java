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
package net.netshot.netshot.api.rest.v0.system;

import java.util.List;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.ResponseCodeFilter;
import net.netshot.netshot.cluster.ClusterService;
import net.netshot.netshot.cluster.ClusterService.MasterStatus;

/**
 * The REST resource of the high availability cluster: members and mastership status of the
 * local server. The class is mounted on {@code /} with the full path on each method.
 */
@Path("/")
@Slf4j
public class ClusterResource {

	private final ClusterService clusterService;

	@Context
	private HttpServletRequest request;

	/**
	 * Instantiates the resource.
	 *
	 * @param clusterService the cluster use cases
	 */
	@Inject
	public ClusterResource(ClusterService clusterService) {
		this.clusterService = clusterService;
	}

	/**
	 * Gets the cluster members.
	 *
	 * @return the cluster members
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/cluster/members")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get cluster members",
		description = "Returns the members of the Netshot high availability cluster "
			+ "(empty list if clustering is not enabled)."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public List<ClusterMemberDto> getClusterMembers() throws WebApplicationException {
		log.debug("REST request, get cluster members");
		return SystemMapper.toMemberDtos(this.clusterService.members());
	}

	/**
	 * Gets the cluster mastership status of the current node.
	 *
	 * @return the mastership status
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@PermitAll
	@Path("/cluster/masterstatus")
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Check if local server is cluster master",
		description = "Returns cluster mastership info of the current node. "
			+ "Might be used by local-balancer to redirect http to the proper server "
			+ "(return code 205 means the local server is not master)."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public MasterStatusDto getClusterMasterStatus() throws WebApplicationException {
		MasterStatus status = this.clusterService.masterStatus();
		if (status.clusterEnabled() && !status.master()) {
			ResponseCodeFilter.suggest(this.request, Response.Status.RESET_CONTENT);
		}
		return SystemMapper.toDto(status);
	}
}
