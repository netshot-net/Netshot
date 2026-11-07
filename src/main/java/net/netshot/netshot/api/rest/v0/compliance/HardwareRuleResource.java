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
package net.netshot.netshot.api.rest.v0.compliance;

import java.util.List;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
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
import net.netshot.netshot.api.rest.v0.ResponseCodeFilter;
import net.netshot.netshot.compliance.HardwareRuleService;
import net.netshot.netshot.driver.DriverRegistry;

/**
 * The REST resource of the hardware compliance rules: list, add, edit and delete.
 *
 * <p>It only translates between HTTP and the {@link HardwareRuleService}. The class is mounted on
 * {@code /} with the full path on each method (see {@code DeviceResource}).</p>
 */
@Path("/")
@Slf4j
public class HardwareRuleResource {

	private final HardwareRuleService hardwareRuleService;
	private final DriverRegistry driverRegistry;

	@Context
	private HttpServletRequest request;

	/**
	 * Instantiates the resource.
	 *
	 * @param hardwareRuleService the hardware rule use cases
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public HardwareRuleResource(HardwareRuleService hardwareRuleService, DriverRegistry driverRegistry) {
		this.hardwareRuleService = hardwareRuleService;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Gets the hardware rules.
	 *
	 * @return the hardware rules
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/hardwarerules")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the hardware compliance rules",
		description = "Returns the list of hardware compliance rules."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<HardwareRuleDto> getHardwareRules() throws WebApplicationException {
		log.debug("REST request, hardware rules.");
		return HardwareRuleMapper.toDtos(this.driverRegistry, this.hardwareRuleService.list());
	}

	/**
	 * Adds an hardware rule.
	 *
	 * @param rsRule the rule
	 * @return the hardware rule
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/hardwarerules")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Add an hardware compliance rule",
		description = "Creates an hardware compliance rule."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public HardwareRuleDto addHardwareRule(HardwareRuleRequest rsRule) throws WebApplicationException {
		log.debug("REST request, add hardware rule.");
		HardwareRuleDto rule = HardwareRuleMapper.toDto(this.driverRegistry, 
			this.hardwareRuleService.create(HardwareRuleMapper.toDefinition(rsRule)));
		ResponseCodeFilter.suggest(this.request, Response.Status.CREATED);
		return rule;
	}

	/**
	 * Delete hardware rule.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/hardwarerules/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Remove an hardware compliance rule",
		description = "Removes an hardware compliance rule, by ID."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public void deleteHardwareRule(@PathParam("id") @Parameter(description = "Hardware rule ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete hardware rule {}.", id);
		if (this.hardwareRuleService.delete(id)) {
			ResponseCodeFilter.suggest(this.request, Response.Status.NO_CONTENT);
		}
		else {
			ResponseCodeFilter.suggest(this.request, Response.Status.NOT_FOUND);
		}
	}

	/**
	 * Sets the hardware rule.
	 *
	 * @param id the id
	 * @param rsRule the rule
	 * @return the hardware rule
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/hardwarerules/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Update an hardware compliance rule",
		description = "Edits an hardware compliance rule, by ID."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public HardwareRuleDto setHardwareRule(
		@PathParam("id") @Parameter(description = "Hardware rule ID") Long id,
		HardwareRuleRequest rsRule)
		throws WebApplicationException {
		log.debug("REST request, edit hardware rule {}.", id);
		return HardwareRuleMapper.toDto(this.driverRegistry, this.hardwareRuleService.update(id, HardwareRuleMapper.toDefinition(rsRule)));
	}
}
