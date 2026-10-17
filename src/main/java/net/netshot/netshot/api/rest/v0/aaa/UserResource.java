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
package net.netshot.netshot.api.rest.v0.aaa;

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
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.aaa.user.UserService;
import net.netshot.netshot.api.rest.PaginationParams;
import net.netshot.netshot.api.rest.ResponseCodeFilter;

/**
 * The REST resource of the users (administrators only): list, add, edit and delete.
 *
 * <p>It only translates between HTTP and the {@link UserService}. The class is mounted on
 * {@code /} with the full path on each method.</p>
 */
@Path("/")
@Slf4j
public class UserResource {

	private final UserService userService;

	@Context
	private HttpServletRequest request;

	@Context
	private SecurityContext securityContext;

	/**
	 * Instantiates the resource.
	 *
	 * @param userService the user use cases
	 */
	@Inject
	public UserResource(UserService userService) {
		this.userService = userService;
	}

	/**
	 * Gets the users.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the users
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/users")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the users",
		description = "Returns the list of Netshot users."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public List<UserDto> getUsers(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, get user list.");
		return UserMapper.toUserDtos(this.userService.list(paginationParams.toPage()));
	}

	/**
	 * Adds the user.
	 *
	 * @param rsUser the rs user
	 * @return the user
	 */
	@POST
	@Path("/users")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Add a user to Netshot",
		description = "Create a Netshot user."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public UserDto addUser(UserRequest rsUser) {
		log.debug("REST request, add user");
		UserDto user = UserMapper.toDto(this.userService.create(UserMapper.toCreation(rsUser)));
		ResponseCodeFilter.suggest(this.request, Response.Status.CREATED);
		return user;
	}

	/**
	 * Sets the user.
	 *
	 * @param id the id
	 * @param rsUser the rs user
	 * @return the user
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/users/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Update a Netshot user",
		description = "Edits a Netshot user, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public UserDto setUser(
		@PathParam("id") @Parameter(description = "User ID") Long id,
		UserRequest rsUser) throws WebApplicationException {
		log.debug("REST request, edit user {}.", id);
		User currentUser = (User) this.securityContext.getUserPrincipal();
		return UserMapper.toDto(this.userService.update(id, currentUser.getUsername(), UserMapper.toUpdate(rsUser)));
	}

	/**
	 * Delete user.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/users/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Remove a Netshot user.",
		description = "Removes a user from the Netshot database."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public void deleteUser(
		@PathParam("id") @Parameter(description = "User ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete user {}.", id);
		User currentUser = (User) this.securityContext.getUserPrincipal();
		if (this.userService.delete(id, currentUser.getUsername())) {
			ResponseCodeFilter.suggest(this.request, Response.Status.NO_CONTENT);
		}
		else {
			ResponseCodeFilter.suggest(this.request, Response.Status.NOT_FOUND);
		}
	}
}
