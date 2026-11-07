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
package net.netshot.netshot.api.rest.v1.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.aaa.user.UserService;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The principal of the current request, whatever the way it was authenticated (session, API token, JWT access
 * token), and the change of its own password.
 */
@Path("/" + AuthMapper.AUTH_PATH + "/me")
@Tag(name = "Authentication", description = "Sessions, current user and own password")
public class MeResource {

	private final UserService userService;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	@Context
	private SecurityContext securityContext;

	/**
	 * Instantiates the resource.
	 *
	 * @param userService the user use cases
	 */
	@Inject
	public MeResource(UserService userService) {
		this.userService = userService;
	}

	/**
	 * Gets the current user.
	 *
	 * @return the principal of the request
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get the current user", description = "Gets the principal the request is authenticated as: "
		+ "kind USER for the user of a session or of a JWT access token, API_TOKEN for an API token (which has no user "
		+ "name), with the authentication method, the permission level and its role.")
	public CurrentUserDto getMe() {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond();
	}

	/**
	 * Changes the password of the current user.
	 *
	 * @param change the current and the new passwords
	 * @return the current user
	 */
	@PATCH
	@RolesAllowed(User.ROLE_READONLY)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Change own password", description = "Changes the password of the current user, who must be a "
		+ "local user (not an API token, nor a user authenticated by an identity provider). The current password must be "
		+ "given, and the new one must comply with the password policy (400 otherwise).")
	public CurrentUserDto changePassword(PasswordChangeRequest change) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		if (!(this.securityContext.getUserPrincipal() instanceof UiUser user) || !user.local()) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_USER, "Only a local user has a password to change.");
		}
		this.userService.changeOwnPassword(user.username(), user.username(), change.currentPassword(), change.newPassword());
		return this.respond();
	}

	private CurrentUserDto respond() {
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), CurrentUserDto.class, Selection.View.FULL),
			CurrentUserDto.class);
		return AuthMapper.toDto((User) this.securityContext.getUserPrincipal(), this.securityContext.getAuthenticationScheme());
	}
}
