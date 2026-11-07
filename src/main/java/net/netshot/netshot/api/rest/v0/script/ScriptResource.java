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
package net.netshot.netshot.api.rest.v0.script;

import java.security.Principal;
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
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v0.PaginationParams;
import net.netshot.netshot.api.rest.v0.ResponseCodeFilter;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.userscript.DeviceJsScript;
import net.netshot.netshot.userscript.ScriptService;

/**
 * The REST resource of the command scripts.
 */
@Path("/")
@Slf4j
public class ScriptResource {

	private final ScriptService scriptService;
	private final DriverRegistry driverRegistry;

	@Context
	private HttpServletRequest request;

	@Context
	private SecurityContext securityContext;

	/**
	 * Instantiates the resource.
	 *
	 * @param scriptService the script use cases
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public ScriptResource(ScriptService scriptService, DriverRegistry driverRegistry) {
		this.scriptService = scriptService;
		this.driverRegistry = driverRegistry;
	}

	@POST
	@Path("/scripts")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Add/validate a command script",
		description = "Create a command script (script to be later run over devices)."
	)
	@Tag(name = "Scripts", description = "Script management (push changes to devices)")
	public ScriptDto addScript(
		ScriptRequest rsScript,
		@DefaultValue("false") @QueryParam("validateonly") @Parameter(description = "True to validate script without saving") boolean validateOnly)
		throws WebApplicationException {
		log.debug("REST request, add device script.");
		// The user is the author; if it cannot be determined, the posted author is kept
		Principal principal = this.securityContext.getUserPrincipal();
		String author = principal instanceof User user ? user.username() : null;
		if (validateOnly) {
			return ScriptMapper.toDto(this.driverRegistry, this.scriptService.validate(ScriptMapper.toCreation(rsScript, author)));
		}
		DeviceJsScript script = this.scriptService.create(ScriptMapper.toCreation(rsScript, author));
		ResponseCodeFilter.suggest(this.request, Response.Status.CREATED);
		return ScriptMapper.toDto(this.driverRegistry, script);
	}

	@DELETE
	@Path("/scripts/{id}")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Remove a script",
		description = "Removes a given script, by ID."
	)
	@Tag(name = "Scripts", description = "Script management (push changes to devices)")
	public void deleteScript(@PathParam("id") @Parameter(description = "Script ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete script {}.", id);
		this.scriptService.delete(id);
		ResponseCodeFilter.suggest(this.request, Response.Status.NO_CONTENT);
	}

	@GET
	@Path("/scripts/{id}")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get a command script",
		description = "Returns a given command script, by ID."
	)
	@Tag(name = "Scripts", description = "Script management (push changes to devices)")
	public ScriptDto getScript(@PathParam("id") @Parameter(description = "Script ID") Long id) {
		log.debug("REST request, get script {}", id);
		DeviceJsScript script = this.scriptService.get(id);
		if (script == null) {
			throw new NetshotException(ErrorCode.NETSHOT_SCRIPT_NOT_FOUND, "Script not found.");
		}
		return ScriptMapper.toDto(this.driverRegistry, script);
	}

	/**
	 * Get the saved scripts.
	 * @param paginationParams = the pagination parameters
	 * @return the list of scripts
	 */
	@GET
	@Path("/scripts")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get command scripts",
		description = "Returns the list of command scripts."
	)
	@Tag(name = "Scripts", description = "Script management (push changes to devices)")
	public List<ScriptDto> getScripts(@BeanParam PaginationParams paginationParams) {
		log.debug("REST request, get scripts.");
		return ScriptMapper.toDtos(this.driverRegistry, this.scriptService.list(paginationParams.toPage()));
	}
}
