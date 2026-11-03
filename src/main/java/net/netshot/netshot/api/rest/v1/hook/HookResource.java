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
package net.netshot.netshot.api.rest.v1.hook;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import net.netshot.netshot.hook.Hook;
import net.netshot.netshot.hook.HookService;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * The hooks (administration).
 */
@Path("/" + HookMapper.PATH)
@Tag(name = "Hooks", description = "Hooks (actions run when events occur)")
public class HookResource {

	private final HookService hookService;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	@Context
	private HttpHeaders headers;

	/**
	 * Instantiates the resource.
	 *
	 * @param hookService the hook use cases
	 */
	@Inject
	public HookResource(HookService hookService) {
		this.hookService = hookService;
	}

	/**
	 * Lists the hooks.
	 *
	 * @return the page of hooks
	 */
	@GET
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the hooks", description = "Lists the hooks, in their brief view by default.")
	@ListFilters(ListSchema.HOOKS)
	public ListResponse<HookDto> listHooks() {
		ListQuery query = ListParams.parse(this.uriInfo.getQueryParameters(), ListSchema.HOOKS.schema());
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), HookDto.class, Selection.View.BRIEF),
			HookDto.class);
		return ListResponse.of(this.uriInfo, query, this.hookService.list(query), HookMapper::toDto);
	}

	/**
	 * Gets a hook.
	 *
	 * @param id the hook ID
	 * @return the hook
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get a hook")
	public HookDto getHook(@PathParam("id") @Parameter(description = "Hook ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.hookService.get(id));
	}

	/**
	 * Creates a hook.
	 *
	 * @param hookRequest the definition of the hook
	 * @return the new hook
	 */
	@POST
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Create a hook", description = "Creates a hook: Netshot runs its action when one of its triggers "
		+ "occurs (a Web hook posts the event to a URL).")
	public HookDto createHook(HookRequest hookRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Hook hook = this.hookService.create(HookMapper.toHook(hookRequest));
		V1Response.created(this.request, V1Links.of(HookMapper.PATH, hook.id()));
		return this.respond(hook);
	}

	/**
	 * Replaces a hook.
	 *
	 * @param id the hook ID
	 * @param hookRequest the new definition of the hook
	 * @return the hook
	 */
	@PUT
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Replace a hook", description = "Replaces the definition of a hook (its type can't change). "
		+ "Hooks have no version: there is no ETag.")
	public HookDto replaceHook(@PathParam("id") @Parameter(description = "Hook ID") long id, HookRequest hookRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		this.hookService.get(id);
		return this.respond(this.hookService.update(id, HookMapper.toHook(hookRequest)));
	}

	/**
	 * Updates a hook with a merge patch.
	 *
	 * @param id the hook ID
	 * @param patch the merge patch
	 * @return the hook
	 * @throws JsonProcessingException if the patched hook isn't a valid definition
	 */
	@PATCH
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({V1Mappers.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Update a hook", description = "Changes some properties of a hook (JSON merge patch, RFC 7396; "
		+ "the triggers are replaced as a whole).")
	public HookDto updateHook(@PathParam("id") @Parameter(description = "Hook ID") long id,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The properties to change") JsonNode patch)
		throws JsonProcessingException {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		HookRequest patched = MergePatch.apply(HookMapper.toRequest(this.hookService.get(id)), patch, this.headers.getMediaType(),
			HookRequest.class);
		return this.respond(this.hookService.update(id, HookMapper.toHook(patched)));
	}

	/**
	 * Deletes a hook.
	 *
	 * @param id the hook ID
	 */
	@DELETE
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Operation(summary = "Delete a hook")
	public void deleteHook(@PathParam("id") @Parameter(description = "Hook ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		if (!this.hookService.delete(id)) {
			throw new NetshotException(ErrorCode.NETSHOT_HOOK_NOT_FOUND, "The hook doesn't exist.");
		}
		V1Response.status(this.request, Response.Status.NO_CONTENT);
	}

	private HookDto respond(Hook hook) {
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), HookDto.class, Selection.View.FULL),
			HookDto.class);
		return HookMapper.toDto(hook);
	}
}
