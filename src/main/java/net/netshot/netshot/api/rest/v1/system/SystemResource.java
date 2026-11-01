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

import java.time.Instant;
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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.cluster.ClusterService;
import net.netshot.netshot.cluster.ClusterService.MasterStatus;
import net.netshot.netshot.system.ServerInfoService;
import net.netshot.netshot.system.ServiceHealthService;

/**
 * Information about the server, the high availability cluster and the services of the server.
 */
@Path("/system")
@Tag(name = "System", description = "Server information, cluster and services")
public class SystemResource {

	private final ServerInfoService serverInfoService;
	private final ClusterService clusterService;
	private final ServiceHealthService serviceHealthService;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	/**
	 * Instantiates the resource.
	 *
	 * @param serverInfoService the server information use case
	 * @param clusterService the cluster use cases
	 * @param serviceHealthService the use cases reporting the state of the services
	 */
	@Inject
	public SystemResource(ServerInfoService serverInfoService, ClusterService clusterService,
		ServiceHealthService serviceHealthService) {
		this.serverInfoService = serverInfoService;
		this.clusterService = clusterService;
		this.serviceHealthService = serviceHealthService;
	}

	/**
	 * Gets the server information.
	 *
	 * @return the information
	 */
	@GET
	@Path("/info")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get the server information", description = "Gets the version of Netshot, the time of the server "
		+ "and the idle timeout of the user sessions.")
	public SystemInfoDto getInfo() {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), SystemInfoDto.class, Selection.View.FULL),
			SystemInfoDto.class);
		return SystemMapper.toDto(this.serverInfoService.get(), Instant.now());
	}

	/**
	 * Lists the members of the cluster.
	 *
	 * @return the members
	 */
	@GET
	@Path("/cluster/members")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the cluster members", description = "Lists the members of the high availability cluster "
		+ "(none if clustering is not enabled), in a single page.")
	public ListResponse<ClusterMemberDto> listClusterMembers() {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters(), ListParams.EXPORT);
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), ClusterMemberDto.class, Selection.View.FULL),
			ClusterMemberDto.class);
		return single(this.clusterService.members().stream().map(SystemMapper::toDto).toList());
	}

	/**
	 * Gets the mastership status of the server.
	 *
	 * @return the status
	 */
	@GET
	@Path("/cluster/master")
	@PermitAll
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Check whether the server is the cluster master", description = "Gets the mastership status of the "
		+ "server answering, to let a load balancer send the requests to the master only. Not authenticated. Answers 200 when "
		+ "the server is the master (or clustering is not enabled) and 503 when it is not.")
	public ClusterMasterDto getClusterMaster() {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), ClusterMasterDto.class, Selection.View.FULL),
			ClusterMasterDto.class);
		MasterStatus status = this.clusterService.masterStatus();
		if (status.clusterEnabled() && !status.master()) {
			V1Response.status(this.request, Response.Status.SERVICE_UNAVAILABLE);
		}
		return SystemMapper.toDto(status);
	}

	/**
	 * Lists the services of the server.
	 *
	 * @return the services
	 */
	@GET
	@Path("/services")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the services of the server", description = "Lists the services of the server (task manager, "
		+ "Syslog server, web server...) in start order, with their state, whether they are healthy and their statistics, "
		+ "in a single page.")
	public ListResponse<ServiceDto> listServices() {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters(), ListParams.EXPORT);
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), ServiceDto.class, Selection.View.FULL),
			ServiceDto.class);
		return single(this.serviceHealthService.services().stream().map(SystemMapper::toDto).toList());
	}

	/**
	 * Builds the page of a list which is always given whole.
	 *
	 * @param <T> the type of the items
	 * @param items the items
	 * @return the page
	 */
	private static <T> ListResponse<T> single(List<T> items) {
		return new ListResponse<>((long) items.size(), 0, items.size(), null, null, items);
	}
}
