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
package net.netshot.netshot.api.rest.v0.compliance;

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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v0.PaginationParams;
import net.netshot.netshot.api.rest.v0.ResponseCodeFilter;
import net.netshot.netshot.compliance.SoftwareRuleService;
import net.netshot.netshot.driver.DriverRegistry;

/**
 * The REST resource of the software compliance rules: list, add, edit, delete and sort.
 *
 * <p>It only translates between HTTP and the {@link SoftwareRuleService}. The class is mounted on
 * {@code /} with the full path on each method (see {@code DeviceResource}).</p>
 */
@Path("/")
@Slf4j
public class SoftwareRuleResource {

	private final SoftwareRuleService softwareRuleService;
	private final DriverRegistry driverRegistry;

	@Context
	private HttpServletRequest request;

	/**
	 * Instantiates the resource.
	 *
	 * @param softwareRuleService the software rule use cases
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public SoftwareRuleResource(SoftwareRuleService softwareRuleService, DriverRegistry driverRegistry) {
		this.softwareRuleService = softwareRuleService;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Gets the software rules.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the software rules
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/softwarerules")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the software compliance rules",
		description = "Returns the list of software compliance rules."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<SoftwareRuleDto> getSoftwareRules(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, software rules.");
		return SoftwareRuleMapper.toDtos(this.driverRegistry, this.softwareRuleService.list(paginationParams.toPage()));
	}

	/**
	 * Creates a software rule.
	 *
	 * @param rsRule the rule
	 * @return the software rule
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/softwarerules")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Add a software compliance rule",
		description = "Creates a software compliance rule."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public SoftwareRuleDto addSoftwareRule(SoftwareRuleRequest rsRule) throws WebApplicationException {
		log.debug("REST request, add software rule.");
		SoftwareRuleDto rule = SoftwareRuleMapper.toDto(this.driverRegistry, 
			this.softwareRuleService.create(SoftwareRuleMapper.toDefinition(rsRule)));
		ResponseCodeFilter.suggest(this.request, Response.Status.CREATED);
		return rule;
	}

	/**
	 * Delete software rule.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/softwarerules/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Remove a software compliance rule",
		description = "Removes a software compliance rule, by ID"
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public void deleteSoftwareRule(@PathParam("id") @Parameter(description = "Software rule ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete software rule {}.", id);
		if (this.softwareRuleService.delete(id)) {
			ResponseCodeFilter.suggest(this.request, Response.Status.NO_CONTENT);
		}
		else {
			ResponseCodeFilter.suggest(this.request, Response.Status.NOT_FOUND);
		}
	}

	/**
	 * Updates the software rule.
	 *
	 * @param id the id
	 * @param rsRule the rule
	 * @return the software rule
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/softwarerules/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Update a software compliance rule",
		description = "Edits a software compliance rule."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public SoftwareRuleDto setSoftwareRule(
		@PathParam("id") @Parameter(description = "Software rule ID") Long id,
		SoftwareRuleRequest rsRule)
		throws WebApplicationException {
		log.debug("REST request, edit software rule {}.", id);
		return SoftwareRuleMapper.toDto(this.driverRegistry, this.softwareRuleService.update(id, SoftwareRuleMapper.toDefinition(rsRule)));
	}

	/**
	 * Sort a software rule.
	 *
	 * @param id the ID of the rule
	 * @param nextId the next ID
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/softwarerules/{id}/sort")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Sort a software compliance rule",
		description = "Sorts a software compliance rule by moving it before another one."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public void sortSoftwareRule(
		@PathParam("id") @Parameter(description = "Software rule ID to move") Long id,
		@QueryParam("next") @Parameter(description = "ID of the other rule to move this rule before (omit parameter to place the rule at last position)") Long nextId)
		throws WebApplicationException {
		log.debug("REST request, sort software rule.");
		this.softwareRuleService.sort(id, nextId);
		ResponseCodeFilter.suggest(this.request, Response.Status.NO_CONTENT);
	}
}
