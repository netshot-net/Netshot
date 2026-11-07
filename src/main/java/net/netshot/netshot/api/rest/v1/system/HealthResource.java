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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.system.ServiceHealthService;

/**
 * The health probe of load balancers and orchestrators: not authenticated, and giving nothing but the status.
 */
@Path("/health")
@Tag(name = "System", description = "Server information, cluster and services")
public class HealthResource {

	private final ServiceHealthService serviceHealthService;

	@Context
	private HttpServletRequest request;

	/**
	 * Instantiates the resource.
	 *
	 * @param serviceHealthService the use cases reporting the state of the services
	 */
	@Inject
	public HealthResource(ServiceHealthService serviceHealthService) {
		this.serviceHealthService = serviceHealthService;
	}

	/**
	 * Probes the health of the server.
	 *
	 * @return the status
	 */
	@GET
	@PermitAll
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Probe the health of the server", description = "Liveness and readiness probe, for load balancers "
		+ "and orchestrators. Not authenticated, and it gives nothing but the status (no version, no detail): 200 with "
		+ "status UP when all the services of the server are running and healthy, 503 with status DOWN otherwise (while "
		+ "starting or stopping, or when a service fails). The services are detailed by GET /system/services.")
	public HealthDto getHealth() {
		boolean healthy = this.serviceHealthService.isHealthy();
		if (!healthy) {
			V1Response.status(this.request, Response.Status.SERVICE_UNAVAILABLE);
		}
		return new HealthDto(healthy ? "UP" : "DOWN");
	}
}
