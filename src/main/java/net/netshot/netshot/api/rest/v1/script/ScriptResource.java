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
package net.netshot.netshot.api.rest.v1.script;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.ListFilters;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.ListSchema;
import net.netshot.netshot.api.rest.v1.MergePatch;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.userscript.DeviceJsScript;
import net.netshot.netshot.userscript.ScriptCreation;
import net.netshot.netshot.userscript.ScriptService;

/**
 * The command scripts (v1): list, read, create, validate and delete. A script can't be edited (as in v0): it is
 * created again.
 */
@Slf4j
@Path("/" + ScriptMapper.PATH)
@Tag(name = "Scripts", description = "Command scripts (to be run over devices)")
public class ScriptResource {

	private final ScriptService scriptService;
	private final DriverRegistry driverRegistry;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	@Context
	private SecurityContext securityContext;

	@Context
	private HttpHeaders headers;

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

	/**
	 * Lists the scripts.
	 *
	 * @return the page of scripts
	 */
	@GET
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the scripts", description = "Lists the command scripts, in their brief view by default "
		+ "(without the code). The user inputs are only extracted (by evaluating the scripts) when they are asked for.")
	@ListFilters(ListSchema.SCRIPTS)
	public ListResponse<ScriptDto> listScripts() {
		ListQuery query = ListParams.parse(this.uriInfo.getQueryParameters(), ListSchema.SCRIPTS.schema());
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), ScriptDto.class, Selection.View.BRIEF);
		V1Response.select(this.request, selection, ScriptDto.class);
		boolean inputs = selection.includes("userInputDefinitions");
		return ListResponse.of(this.uriInfo, query, this.scriptService.list(query),
			s -> ScriptMapper.toDto(this.driverRegistry, inputs ? this.withInputs(s) : s));
	}

	/**
	 * Gets a script.
	 *
	 * @param id the script ID
	 * @return the script
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get a script")
	public ScriptDto getScript(@PathParam("id") @Parameter(description = "Script ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		DeviceJsScript script = this.scriptService.get(id);
		if (script == null) {
			throw new NetshotException(ErrorCode.NETSHOT_SCRIPT_NOT_FOUND, "The script doesn't exist.");
		}
		return this.respond(this.withInputs(script));
	}

	/**
	 * Creates a script.
	 *
	 * @param scriptRequest the definition of the script
	 * @return the new script
	 */
	@POST
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Create a script", description = "Validates and creates a command script, the author being the user.")
	public ScriptDto createScript(ScriptRequest scriptRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		DeviceJsScript script = this.scriptService.create(ScriptMapper.toCreation(scriptRequest, this.author()));
		V1Response.created(this.request, V1Links.of(ScriptMapper.PATH, script.id()));
		return this.respond(script);
	}

	/**
	 * Validates a script without saving it.
	 *
	 * @param scriptRequest the definition of the script
	 * @return the script as it would be created (ID 0), with its user inputs
	 */
	@POST
	@Path("/validation")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Validate a script", description = "Checks the driver, the name and the code of a script definition "
		+ "(by evaluating it) without saving it: 200 with the script and its user inputs, 400 with the problem otherwise.")
	public ScriptDto validateScript(ScriptRequest scriptRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.scriptService.validate(ScriptMapper.toCreation(scriptRequest, this.author())));
	}

	/**
	 * Replaces a script.
	 *
	 * @param id the script ID
	 * @param scriptRequest the new definition of the script
	 * @return the script
	 */
	@PUT
	@Path("/{id}")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Replace a script", description = "Validates and replaces the definition of a command script (the "
		+ "author is kept). With If-Match, the script must still be at the given version (ETag).")
	public ScriptDto replaceScript(@PathParam("id") @Parameter(description = "Script ID") long id, ScriptRequest scriptRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		ScriptCreation definition = ScriptMapper.toCreation(scriptRequest, null);
		return this.respond(this.scriptService.update(id, _ -> definition, V1Response.ifMatch(this.headers)));
	}

	/**
	 * Updates a script with a merge patch.
	 *
	 * @param id the script ID
	 * @param patch the merge patch
	 * @return the script
	 */
	@PATCH
	@Path("/{id}")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Consumes({V1Mappers.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Update a script", description = "Changes some properties of a command script (JSON merge patch, "
		+ "RFC 7396), validated as on creation (the author is kept). With If-Match, the script must still be at the given "
		+ "version (ETag).")
	public ScriptDto updateScript(@PathParam("id") @Parameter(description = "Script ID") long id,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The properties to change") JsonNode patch) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.scriptService.update(id, MergePatch.of(patch, this.headers.getMediaType(), ScriptRequest.class,
			ScriptMapper::toRequest, r -> ScriptMapper.toCreation(r, null)), V1Response.ifMatch(this.headers)));
	}

	/**
	 * Deletes a script.
	 *
	 * @param id the script ID
	 */
	@DELETE
	@Path("/{id}")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Operation(summary = "Delete a script", description = "Deletes a command script. With If-Match, the script must still be "
		+ "at the given version (ETag).")
	public void deleteScript(@PathParam("id") @Parameter(description = "Script ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		this.scriptService.delete(id, V1Response.ifMatch(this.headers));
		V1Response.status(this.request, Response.Status.NO_CONTENT);
	}

	private String author() {
		return this.securityContext.getUserPrincipal() instanceof User user ? user.username() : null;
	}

	// The script with its user inputs, extracted by evaluating it (none if it can't be evaluated)
	private DeviceJsScript withInputs(DeviceJsScript script) {
		try {
			return script.withUserInputDefinitions(this.driverRegistry);
		}
		catch (RuntimeException e) {
			log.warn("Unable to extract the user inputs of the script {}.", script.id(), e);
			return script;
		}
	}

	private ScriptDto respond(DeviceJsScript script) {
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), ScriptDto.class, Selection.View.FULL),
			ScriptDto.class);
		if (script.id() != 0) {
			V1Response.etag(this.request, script.version());
		}
		return ScriptMapper.toDto(this.driverRegistry, script);
	}
}
