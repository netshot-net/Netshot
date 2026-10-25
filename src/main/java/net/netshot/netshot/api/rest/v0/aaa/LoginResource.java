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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
import net.netshot.netshot.aaa.auth.LoginService;
import net.netshot.netshot.aaa.auth.Oidc;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.aaa.user.UserService;
import net.netshot.netshot.api.rest.LoggerFilter;
import net.netshot.netshot.api.rest.NetshotAuthenticationRequiredException;
import net.netshot.netshot.api.rest.SecurityFilter;

/**
 * The REST resource of the current user: log in, log out, read the current user and change its
 * own password.
 *
 * <p>Everything which touches the HTTP session (creating, renewing and dropping it, the user
 * stored in it), the client address and the response headers stays here: the services only get
 * plain values. The class is mounted on {@code /} with the full path on each method.</p>
 */
@Path("/")
@Slf4j
public class LoginResource {

	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final LoginService loginService;
	private final UserService userService;

	@Context
	private HttpServletRequest request;

	@Context
	private SecurityContext securityContext;

	/** The OIDC authentication. */
	private final Oidc oidc;

	/**
	 * Instantiates the resource.
	 *
	 * @param loginService the login use case
	 * @param userService the user use cases
	 * @param oidc the OIDC authentication (for the end session endpoint)
	 */
	@Inject
	public LoginResource(LoginService loginService, UserService userService, Oidc oidc) {
		this.loginService = loginService;
		this.userService = userService;
		this.oidc = oidc;
	}

	/**
	 * Logout.
	 *
	 * @param id the ID of the user (ignored)
	 * @return the boolean
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/user/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "User log out",
		description = "Terminates the current user session (useless when using API tokens)."
	)
	@Tag(name = "Login", description = "Login and password management for standard user")
	public Response logout(@PathParam("id") @Parameter(description = "User ID - not used") Long id)
		throws WebApplicationException {
		log.debug("REST logout request.");
		HttpSession httpSession = this.request.getSession(false);
		User user = (User) this.securityContext.getUserPrincipal();
		String username = user == null ? "<none>" : user.username();
		if (httpSession != null) {
			httpSession.invalidate();
		}
		AAA_LOG.warn("User {} has logged out.", username);
		Response.ResponseBuilder builder = Response.noContent();
		builder.status(Response.Status.NO_CONTENT);
		if (this.oidc.isAvailable()) {
			builder.header("X-OIDC-EndSessionEndpoint", this.oidc.getEndSessionEndpointURI());
		}
		return builder.build();
	}

	/**
	 * Sets the password.
	 *
	 * @param rsLogin the rs login
	 * @param id the user ID (ignored)
	 * @return the user
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/user/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Update self user",
		description = "Edits current user, especially password."
	)
	@Tag(name = "Login", description = "Login and password management for standard user")
	public UserDto setSelfUser(
		LoginRequest rsLogin,
		@PathParam("id") @Parameter(description = "User ID (ignored)") Long id)
		throws WebApplicationException {
		log.debug("REST password change request, username {}.", rsLogin.username());
		User currentUser = (User) this.securityContext.getUserPrincipal();
		UiUser user = this.userService.changeOwnPassword(
			currentUser.username(), rsLogin.username(), rsLogin.password(), rsLogin.newPassword());
		return UserMapper.toDto(user);
	}

	/**
	 * Login.
	 *
	 * @param rsLogin the rs login
	 * @return the user
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@PermitAll
	@Path("/user")
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Log in",
		description = "Logs in (create session) by username and password or OIDC authorization code (useless when using API tokens)."
	)
	@Tag(name = "Login", description = "Login and password management for standard user")
	public UserDto login(LoginRequest rsLogin) throws WebApplicationException {
		log.debug("REST authentication request, username {}.", rsLogin.username());

		String remoteAddress = LoggerFilter.getClientAddress(this.request);
		// Any exception thrown by the service leaves the existing session untouched
		UiUser user = this.loginService.authenticate(UserMapper.toAttempt(rsLogin, remoteAddress));

		// Always drop the existing session (if any): a new session (with new ID) is created
		// on successful authentication, to prevent session fixation
		HttpSession previousSession = this.request.getSession(false);
		if (previousSession != null) {
			previousSession.invalidate();
		}
		if (user != null) {
			HttpSession httpSession = this.request.getSession(true);
			// The current max idle time (the default timeout of the sessions is set when the server starts)
			httpSession.setMaxInactiveInterval(UiUser.settings().maxIdleTime());
			httpSession.setAttribute(SecurityFilter.USER_ATTRIBUTE, user);
			return UserMapper.toDto(user);
		}
		throw new NetshotAuthenticationRequiredException();
	}

	/**
	 * Gets the user.
	 *
	 * @return the user
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Path("/user")
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the current user",
		description = "Returns the current logged in user."
	)
	@Tag(name = "Login", description = "Login and password management for standard user")
	public CurrentUserDto getUser() throws WebApplicationException {
		User user = (User) this.securityContext.getUserPrincipal();
		return UserMapper.toCurrentUserDto(user);
	}
}
