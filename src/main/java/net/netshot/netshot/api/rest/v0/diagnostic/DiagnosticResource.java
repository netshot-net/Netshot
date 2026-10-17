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
package net.netshot.netshot.api.rest.v0.diagnostic;

import java.util.List;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.PaginationParams;
import net.netshot.netshot.api.rest.ResponseCodeFilter;
import net.netshot.netshot.diagnostic.DiagnosticService;

/**
 * The REST resource of the diagnostics and of the diagnostic results of the devices.
 */
@Path("/")
@Slf4j
public class DiagnosticResource {

	private final DiagnosticService diagnosticService;

	@Context
	private HttpServletRequest request;

	/**
	 * Instantiates the resource.
	 *
	 * @param diagnosticService the diagnostic use cases
	 */
	@Inject
	public DiagnosticResource(DiagnosticService diagnosticService) {
		this.diagnosticService = diagnosticService;
	}

	/**
	 * Gets the diagnotics.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the diagnotics
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/diagnostics")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the diagnostics",
		description = "Returns the list of diagnostics."
	)
	@Tag(
		name = "Diagnostics",
		description = "Diagnostic management (execute commands and retrieve custom data from devices)"
	)
	public List<DiagnosticDto> getDiagnostics(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, get diagnotics.");
		return DiagnosticMapper.toDtos(this.diagnosticService.list(paginationParams.toPage()));
	}

	/**
	 * Gets a specific diagnotic.
	 *
	 * @param id = the ID of the diagnostic
	 * @return the diagnotic
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/diagnostics/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get a diagnostic",
		description = "Returns a specific diagnostic, by ID."
	)
	@Tag(
		name = "Diagnostics",
		description = "Diagnostic management (execute commands and retrieve custom data from devices)"
	)
	public DiagnosticDto getDiagnostic(@PathParam("id") @Parameter(description = "Diagnostic ID") Long id) throws WebApplicationException {
		log.debug("REST request, get diagnotic.");
		return DiagnosticMapper.toDto(this.diagnosticService.get(id));
	}

	@POST
	@Path("/diagnostics")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Add a diagnostic.",
		description = "Creates a diagnostic."
	)
	@Tag(
		name = "Diagnostics",
		description = "Diagnostic management (execute commands and retrieve custom data from devices)"
	)
	public DiagnosticDto addDiagnostic(DiagnosticRequest rsDiagnostic) throws WebApplicationException {
		log.debug("REST request, add diagnostic");
		DiagnosticDto diagnostic = DiagnosticMapper.toDto(this.diagnosticService.create(DiagnosticMapper.toDefinition(rsDiagnostic)));
		ResponseCodeFilter.suggest(this.request, Response.Status.CREATED);
		return diagnostic;
	}

	/**
	 * Updates the diagnostic.
	 *
	 * @param id the diagnostic ID
	 * @param rsDiagnostic the passed diagnostic
	 * @return the updated diagnostic
	 * @throws WebApplicationException a web application exception
	 */
	@PUT
	@Path("/diagnostics/{id}")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Update a diagnostic",
		description = "Creates a new diagnostic."
	)
	@Tag(
		name = "Diagnostics",
		description = "Diagnostic management (execute commands and retrieve custom data from devices)"
	)
	public DiagnosticDto setDiagnostic(@PathParam("id") @Parameter(description = "Diagnostic ID") Long id, DiagnosticRequest rsDiagnostic)
		throws WebApplicationException {
		log.debug("REST request, edit diagnostic {}.", id);
		return DiagnosticMapper.toDto(this.diagnosticService.update(id, DiagnosticMapper.toDefinition(rsDiagnostic)));
	}

	/**
	 * Delete diagnostic.
	 *
	 * @param id the id of the diagnostic to delete
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/diagnostics/{id}")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Remove a diagnostic",
		description = "Removes a given diagnostic, by ID."
	)
	@Tag(
		name = "Diagnostics",
		description = "Diagnostic management (execute commands and retrieve custom data from devices)"
	)
	public void deleteDiagnostic(@PathParam("id") @Parameter(description = "Diagnostic ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete diagnostic {}.", id);
		if (this.diagnosticService.delete(id)) {
			ResponseCodeFilter.suggest(this.request, Response.Status.NO_CONTENT);
		}
		else {
			ResponseCodeFilter.suggest(this.request, Response.Status.NOT_FOUND);
		}
	}

	/**
	 * Gets the device diagnostic results.
	 *
	 * @param id the id
	 * @param paginationParams = the pagination parameters
	 * @return the device compliance
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devices/{id}/diagnosticresults")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get diagnostic results",
		description = "Returns the results of a given diagnostic, by ID."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	@Tag(
		name = "Diagnostics",
		description = "Diagnostic management (execute commands and retrieve custom data from devices)"
	)
	public List<DiagnosticResultDto> getDeviceDiagnosticResults(@BeanParam PaginationParams paginationParams,
		@PathParam("id") @Parameter(description = "Device ID") Long id) throws WebApplicationException {
		log.debug("REST request, get diagnostic results for device {}.", id);
		return DiagnosticMapper.toResultDtos(this.diagnosticService.listResults(id, paginationParams.toPage()));
	}
}
