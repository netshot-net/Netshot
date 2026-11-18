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
package net.netshot.netshot.api.rest.v1.diagnostic;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

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
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
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
import net.netshot.netshot.api.rest.v1.group.GroupDto;
import net.netshot.netshot.api.rest.v1.group.GroupMapper;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticDefinition;
import net.netshot.netshot.diagnostic.DiagnosticService;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;

/**
 * The diagnostics (v1): list, read, create, replace, update and delete.
 */
@Path("/" + DiagnosticMapper.PATH)
@Tag(name = "Diagnostics", description = "Diagnostics (data collected from devices by commands or scripts)")
public class DiagnosticResource {

	private final DiagnosticService diagnosticService;
	private final GroupService groupService;
	private final DriverRegistry driverRegistry;
	private final ListParams lists;
	private final V1Links links;
	private final DiagnosticMapper diagnosticMapper;
	private final GroupMapper groupMapper;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	@Context
	private HttpHeaders headers;

	/**
	 * Instantiates the resource.
	 *
	 * @param diagnosticService the diagnostic use cases
	 * @param groupService the group use cases
	 * @param driverRegistry the loaded device drivers
	 * @param lists the reader of the list parameters
	 * @param links the paths of the v1 objects
	 * @param diagnosticMapper maps the diagnostic objects
	 * @param groupMapper maps the group objects
	 */
	@Inject
	public DiagnosticResource(DiagnosticService diagnosticService, GroupService groupService, DriverRegistry driverRegistry,
		ListParams lists, V1Links links, DiagnosticMapper diagnosticMapper, GroupMapper groupMapper) {
		this.diagnosticService = diagnosticService;
		this.groupService = groupService;
		this.driverRegistry = driverRegistry;
		this.lists = lists;
		this.links = links;
		this.diagnosticMapper = diagnosticMapper;
		this.groupMapper = groupMapper;
	}

	/**
	 * Loads the target groups of diagnostics, in one query (only if the selection writes them).
	 *
	 * @param groupService the group use cases
	 * @param groupMapper maps the group objects
	 * @param diagnosticMapper maps the diagnostic objects
	 * @param load whether the groups are written
	 * @param diagnostics the diagnostics
	 * @return the groups, by ID (a reference by ID only for a group not loaded)
	 */
	static LongFunction<GroupDto> groups(GroupService groupService, GroupMapper groupMapper, DiagnosticMapper diagnosticMapper, boolean load,
		Collection<? extends Diagnostic> diagnostics) {
		Set<Long> ids = diagnostics.stream().map(Diagnostic::targetGroupId).filter(Objects::nonNull).collect(Collectors.toSet());
		Map<Long, GroupDto> groups = new HashMap<>();
		if (!ids.isEmpty() && load) {
			groupService.list(ListQuery.byIds(ids)).items().forEach(g -> groups.put(g.id(), groupMapper.toDto(g)));
		}
		return id -> groups.getOrDefault(id, diagnosticMapper.groupById(id));
	}

	/**
	 * Lists the diagnostics.
	 *
	 * @return the page of diagnostics
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the diagnostics", description = "Lists the diagnostics, in their brief view by default.")
	@ListFilters(ListSchema.DIAGNOSTICS)
	public ListResponse<DiagnosticDto> listDiagnostics() {
		ListQuery query = this.lists.parse(this.uriInfo.getQueryParameters(), ListSchema.DIAGNOSTICS.schema());
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), DiagnosticDto.class, Selection.View.BRIEF);
		V1Response.select(this.request, selection, DiagnosticDto.class);
		ListResult<Diagnostic> result = this.diagnosticService.list(query);
		LongFunction<GroupDto> groups = groups(this.groupService, this.groupMapper, this.diagnosticMapper, selection.includes("targetGroup"), result.items());
		return ListResponse.of(this.uriInfo, query, result, d -> this.diagnosticMapper.toDto(this.driverRegistry, d, groups));
	}

	/**
	 * Gets a diagnostic.
	 *
	 * @param id the diagnostic ID
	 * @return the diagnostic
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get a diagnostic")
	public DiagnosticDto getDiagnostic(@PathParam("id") @Parameter(description = "Diagnostic ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.diagnosticService.get(id));
	}

	/**
	 * Creates a diagnostic.
	 *
	 * @param diagnosticRequest the definition of the diagnostic
	 * @return the new diagnostic
	 */
	@POST
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Create a diagnostic", description = "Creates a diagnostic: a CLI command (SIMPLE), "
		+ "or a JavaScript or Python script.")
	public DiagnosticDto createDiagnostic(DiagnosticRequest diagnosticRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Diagnostic diagnostic = this.diagnosticService.create(this.diagnosticMapper.toDefinition(diagnosticRequest));
		V1Response.created(this.request, this.links.of(DiagnosticMapper.PATH, diagnostic.id()));
		return this.respond(diagnostic);
	}

	/**
	 * Replaces a diagnostic.
	 *
	 * @param id the diagnostic ID
	 * @param diagnosticRequest the new definition of the diagnostic
	 * @return the diagnostic
	 */
	@PUT
	@Path("/{id}")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Replace a diagnostic", description = "Replaces the definition of a diagnostic (its type can't change). "
		+ "The results of the diagnostic are deleted when its target group changes. With If-Match, the diagnostic must still be "
		+ "at the given version (ETag).")
	public DiagnosticDto replaceDiagnostic(@PathParam("id") @Parameter(description = "Diagnostic ID") long id,
		DiagnosticRequest diagnosticRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		DiagnosticDefinition definition = this.diagnosticMapper.toDefinition(diagnosticRequest);
		return this.respond(this.diagnosticService.update(id, _ -> definition, V1Response.ifMatch(this.headers)));
	}

	/**
	 * Updates a diagnostic with a merge patch.
	 *
	 * @param id the diagnostic ID
	 * @param patch the merge patch
	 * @return the diagnostic
	 */
	@PATCH
	@Path("/{id}")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Consumes({V1Mappers.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Update a diagnostic", description = "Changes some properties of a diagnostic (JSON merge patch, RFC 7396). "
		+ "With If-Match, the diagnostic must still be at the given version (ETag).")
	public DiagnosticDto updateDiagnostic(@PathParam("id") @Parameter(description = "Diagnostic ID") long id,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The properties to change") JsonNode patch) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.diagnosticService.update(id, MergePatch.of(patch, this.headers.getMediaType(), DiagnosticRequest.class,
			this.diagnosticMapper::toRequest, this.diagnosticMapper::toDefinition), V1Response.ifMatch(this.headers)));
	}

	/**
	 * Deletes a diagnostic.
	 *
	 * @param id the diagnostic ID
	 */
	@DELETE
	@Path("/{id}")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Operation(summary = "Delete a diagnostic", description = "Deletes a diagnostic, with its results. With If-Match, the "
		+ "diagnostic must still be at the given version (ETag).")
	public void deleteDiagnostic(@PathParam("id") @Parameter(description = "Diagnostic ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		this.diagnosticService.delete(id, V1Response.ifMatch(this.headers));
		V1Response.status(this.request, Response.Status.NO_CONTENT);
	}

	private DiagnosticDto respond(Diagnostic diagnostic) {
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), DiagnosticDto.class, Selection.View.FULL);
		V1Response.select(this.request, selection, DiagnosticDto.class);
		V1Response.etag(this.request, diagnostic.version());
		return this.diagnosticMapper.toDto(this.driverRegistry, diagnostic,
			groups(this.groupService, this.groupMapper, this.diagnosticMapper, selection.includes("targetGroup"), List.of(diagnostic)));
	}
}
