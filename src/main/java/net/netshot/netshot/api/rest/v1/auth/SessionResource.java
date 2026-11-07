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
package net.netshot.netshot.api.rest.v1.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.Operation;
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
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.auth.LoginAttempt;
import net.netshot.netshot.aaa.auth.LoginService;
import net.netshot.netshot.aaa.auth.Oidc;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.LoggerFilter;
import net.netshot.netshot.api.rest.SessionAuthenticator;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The session of a user: log in (creates the session, identified by the session cookie), get the current session,
 * log out.
 */
@Path("/" + AuthMapper.AUTH_PATH + "/session")
@Tag(name = "Authentication", description = "Sessions, current user and own password")
@Slf4j
public class SessionResource {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/** The header telling the client where to end the session at the OIDC IdP. */
	private static final String END_SESSION_HEADER = "X-OIDC-EndSessionEndpoint";

	private final LoginService loginService;
	private final Oidc oidc;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	@Context
	private SecurityContext securityContext;

	/**
	 * Instantiates the resource.
	 *
	 * @param loginService the login use case
	 * @param oidc the OIDC authentication (for the end session endpoint)
	 */
	@Inject
	public SessionResource(LoginService loginService, Oidc oidc) {
		this.loginService = loginService;
		this.oidc = oidc;
	}

	/**
	 * Logs in.
	 *
	 * @param credentials the credentials
	 * @return the new session
	 */
	@POST
	@PermitAll
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Log in", description = "Logs in with a username and a password, or an OIDC authorization code with "
		+ "its redirect URI, and creates a session: the session cookie is set in the response, and the previous session of "
		+ "the client (if any) is dropped. Answers 201 with the session. A wrong user name, password or code is a 401. "
		+ "An expired password is a 403 (give a newPassword to change it while logging in); a new password which does not "
		+ "comply with the password policy is a 400. Useless when using API tokens.")
	public SessionDto login(SessionRequest credentials) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		LoginAttempt attempt = toAttempt(credentials, LoggerFilter.getClientAddress(this.request));
		V1Response.noStore(this.request);
		// Any exception thrown by the service (expired password...) leaves the existing session untouched
		UiUser user = this.loginService.authenticate(attempt);
		// Then always drop the existing session (if any), as v0 does: a failed attempt ends it, and a new session
		// (with a new ID) is created on successful authentication, to prevent session fixation
		HttpSession previousSession = this.request.getSession(false);
		if (previousSession != null) {
			previousSession.invalidate();
		}
		if (user == null) {
			throw new NetshotException(ErrorCode.NETSHOT_AUTHENTICATION_FAILED, "Authentication failed.");
		}
		HttpSession httpSession = this.request.getSession(true);
		// The current max idle time (the default timeout of the sessions is set when the server starts)
		int maxIdleTime = UiUser.settings().maxIdleTime();
		httpSession.setMaxInactiveInterval(maxIdleTime);
		httpSession.setAttribute(SessionAuthenticator.USER_ATTRIBUTE, user);
		V1Response.created(this.request, V1Links.of(AuthMapper.AUTH_PATH, "session"));
		return AuthMapper.toSessionDto(user, httpSession.getCreationTime(), maxIdleTime);
	}

	/**
	 * Gets the current session.
	 *
	 * @return the session
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get the current session", description = "Gets the session identified by the session cookie: "
		+ "its user, when it was created and its idle timeout. Answers 401 if the request is not authenticated, and 404 if "
		+ "it is authenticated otherwise than by a session (API token, JWT access token): use GET /auth/me for the "
		+ "principal of any request.")
	public SessionDto getSession() {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		HttpSession httpSession = this.request.getSession(false);
		if (httpSession == null || !SessionAuthenticator.SCHEME.equals(this.securityContext.getAuthenticationScheme())
			|| !(httpSession.getAttribute(SessionAuthenticator.USER_ATTRIBUTE) instanceof UiUser user)) {
			throw new NetshotException(ErrorCode.NETSHOT_SESSION_NOT_FOUND, "The request is not made in a session.");
		}
		return AuthMapper.toSessionDto(user, httpSession.getCreationTime(), httpSession.getMaxInactiveInterval());
	}

	/**
	 * Logs out.
	 *
	 * @return the empty answer
	 */
	@DELETE
	@PermitAll
	@Operation(summary = "Log out", description = "Terminates the session identified by the session cookie. Always answers "
		+ "204, even without a session (the operation is idempotent). For a user who logged in through an identity "
		+ "provider (not a local user), when OIDC is available, the X-OIDC-EndSessionEndpoint header gives the IdP "
		+ "endpoint the client can send the user to, to end the session there too.")
	public Response logout() {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		HttpSession httpSession = this.request.getSession(false);
		UiUser user = null;
		if (httpSession != null) {
			user = httpSession.getAttribute(SessionAuthenticator.USER_ATTRIBUTE) instanceof UiUser sessionUser ? sessionUser : null;
			httpSession.invalidate();
		}
		if (user != null) {
			AAA_LOG.warn("User {} has logged out.", user.username());
		}
		Response.ResponseBuilder builder = Response.noContent();
		if (user != null && !user.local() && this.oidc.isAvailable()) {
			builder.header(END_SESSION_HEADER, this.oidc.getEndSessionEndpointURI());
		}
		return builder.build();
	}

	private static LoginAttempt toAttempt(SessionRequest credentials, String remoteAddress) {
		boolean password = credentials.username() != null || credentials.password() != null;
		boolean code = credentials.authorizationCode() != null || credentials.redirectUri() != null;
		if (password == code) {
			throw NetshotException.invalidParameter(null,
				"Give either a username and a password, or an authorization code and a redirect URI");
		}
		if (password) {
			require(credentials.username(), "username");
			require(credentials.password(), "password");
		}
		else {
			require(credentials.authorizationCode(), "authorizationCode");
			require(credentials.redirectUri(), "redirectUri");
			if (credentials.newPassword() != null) {
				throw NetshotException.invalidParameter("newPassword", "A new password can only be set when logging in with a password");
			}
		}
		return new LoginAttempt(credentials.username(), credentials.password(), credentials.newPassword(),
			credentials.authorizationCode(), credentials.redirectUri(), remoteAddress);
	}

	private static void require(String value, String name) {
		if (value == null || value.isEmpty()) {
			throw NetshotException.invalidParameter(name, "The property '%s' is required".formatted(name));
		}
	}
}
