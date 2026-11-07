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
package net.netshot.netshot.api.rest.v1.user;

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
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.aaa.user.UserService;
import net.netshot.netshot.aaa.user.UserUpdate;
import net.netshot.netshot.api.rest.v1.ListFilters;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.ListSchema;
import net.netshot.netshot.api.rest.v1.MergePatch;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.api.rest.v1.auth.AuthMapper;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * The Netshot users (administration).
 */
@Path("/" + AuthMapper.USERS_PATH)
@Tag(name = "Users", description = "Netshot users")
public class UserResource {

	private final UserService userService;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	@Context
	private HttpHeaders headers;

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
	 * Lists the users.
	 *
	 * @return the page of users
	 */
	@GET
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the users", description = "Lists the Netshot users, in their brief view by default.")
	@ListFilters(ListSchema.USERS)
	public ListResponse<UserDto> listUsers() {
		ListQuery query = ListParams.parse(this.uriInfo.getQueryParameters(), ListSchema.USERS.schema());
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), UserDto.class, Selection.View.BRIEF),
			UserDto.class);
		return ListResponse.of(this.uriInfo, query, this.userService.list(query), UserMapper::toDto);
	}

	/**
	 * Gets a user.
	 *
	 * @param id the user ID
	 * @return the user
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get a user")
	public UserDto getUser(@PathParam("id") @Parameter(description = "User ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.userService.get(id));
	}

	/**
	 * Creates a user.
	 *
	 * @param userRequest the definition of the user
	 * @return the new user
	 */
	@POST
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Create a user", description = "Creates a local user (the password is required, and must comply "
		+ "with the password policy) or a remote one (no password: it authenticates through RADIUS, TACACS+ or OIDC, and "
		+ "the user only gives it its permission level).")
	public UserDto createUser(UserRequest userRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		UiUser user = this.userService.create(UserMapper.toCreation(userRequest));
		V1Response.created(this.request, V1Links.of(AuthMapper.USERS_PATH, user.id()));
		return this.respond(user);
	}

	/**
	 * Replaces a user.
	 *
	 * @param id the user ID
	 * @param userRequest the new definition of the user
	 * @return the user
	 */
	@PUT
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Replace a user", description = "Replaces the definition of a user. An absent password is "
		+ "unchanged (it is required to make a remote user a local one). A user can't edit itself (403). With If-Match, "
		+ "the user must still be at the given version (ETag).")
	public UserDto replaceUser(@PathParam("id") @Parameter(description = "User ID") long id, UserRequest userRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		UserUpdate update = UserMapper.toUpdate(userRequest);
		return this.respond(this.userService.update(id, this.currentUsername(), _ -> update, V1Response.ifMatch(this.headers)));
	}

	/**
	 * Updates a user with a merge patch.
	 *
	 * @param id the user ID
	 * @param patch the merge patch
	 * @return the user
	 */
	@PATCH
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({V1Mappers.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Update a user", description = "Changes some properties of a user (JSON merge patch, RFC 7396). "
		+ "The password is changed by giving one. A user can't edit itself (403). With If-Match, the user must still be at the "
		+ "given version (ETag).")
	public UserDto updateUser(@PathParam("id") @Parameter(description = "User ID") long id,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The properties to change") JsonNode patch) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.userService.update(id, this.currentUsername(), MergePatch.of(patch, this.headers.getMediaType(),
			UserRequest.class, UserMapper::toRequest, UserMapper::toUpdate), V1Response.ifMatch(this.headers)));
	}

	/**
	 * Deletes a user.
	 *
	 * @param id the user ID
	 */
	@DELETE
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Operation(summary = "Delete a user", description = "Deletes a user. A user can't delete itself (403). With If-Match, the "
		+ "user must still be at the given version (ETag).")
	public void deleteUser(@PathParam("id") @Parameter(description = "User ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		if (!this.userService.delete(id, this.currentUsername(), V1Response.ifMatch(this.headers))) {
			throw new NetshotException(ErrorCode.NETSHOT_USER_NOT_FOUND, "The user doesn't exist.");
		}
		V1Response.status(this.request, Response.Status.NO_CONTENT);
	}

	private String currentUsername() {
		return ((User) this.securityContext.getUserPrincipal()).username();
	}

	private UserDto respond(UiUser user) {
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), UserDto.class, Selection.View.FULL),
			UserDto.class);
		V1Response.etag(this.request, user.version());
		return UserMapper.toDto(user);
	}
}
