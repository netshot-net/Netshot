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
package net.netshot.netshot.api.rest.v0.hook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonView;

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
import net.netshot.netshot.hook.HookService;
import net.netshot.netshot.platform.rest.RestViews.RestApiView;

/**
 * The REST resource of the hook definitions: list, add, edit and delete. (The payload a hook sends
 * is a different thing, which belongs to the webhook channel.)
 *
 * <p>It only translates between HTTP and the {@link HookService}. The class is mounted on
 * {@code /} with the full path on each method, like the other resources.</p>
 */
@Path("/")
@Slf4j
public class HookResource {

	private final HookService hookService;

	@Context
	private HttpServletRequest request;

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
	 * Gets the hooks.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the current hooks
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/hooks")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the hooks",
		description = "Returns the current list of hooks."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public List<HookDto> getHooks(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, hooks.");
		return HookMapper.toDtos(this.hookService.list(paginationParams.toPage()));
	}

	/**
	 * Adds the hook.
	 *
	 * @param hook the hook
	 * @return the new hook
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/hooks")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Add a hook",
		description = "Creates a hook. Based on given criteria, Netshot will run the given action when specific events occur."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public HookDto addHook(HookRequest hook) throws WebApplicationException {
		log.debug("REST request, add hook.");
		HookDto created = HookMapper.toDto(this.hookService.create(HookMapper.toEntity(hook)));
		ResponseCodeFilter.suggest(this.request, Response.Status.CREATED);
		return created;
	}

	/**
	 * Delete hook.
	 *
	 * @param id the id of the hook
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/hooks/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Remove a hook",
		description = "Removes the hook, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public void deleteHook(@PathParam("id") @Parameter(description = "Hook ID") Long id) throws WebApplicationException {
		log.debug("REST request, delete hook {}", id);
		if (this.hookService.delete(id)) {
			ResponseCodeFilter.suggest(this.request, Response.Status.NO_CONTENT);
		}
		else {
			ResponseCodeFilter.suggest(this.request, Response.Status.NOT_FOUND);
		}
	}

	/**
	 * Sets the hook.
	 *
	 * @param id the id
	 * @param rsHook the hook
	 * @return the updated hook
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/hooks/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Update a hook",
		description = "Edits a hook, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public HookDto setHook(@PathParam("id") @Parameter(description = "Hook ID") Long id, HookRequest rsHook) throws WebApplicationException {
		log.debug("REST request, edit hook {}", id);
		return HookMapper.toDto(this.hookService.update(id, HookMapper.toEntity(rsHook)));
	}
}
