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
package net.netshot.netshot.api.rest;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import net.netshot.netshot.aaa.auth.LoginService;
import net.netshot.netshot.aaa.user.ApiTokenService;
import net.netshot.netshot.aaa.user.User;

/**
 * Authenticates the REST requests, with the methods tried in turn: API token, JWT access token, session. The
 * first method whose credentials the request carries decides (see {@link RequestAuthenticator}); the user (or
 * none) is set in the security context, whose roles are checked by {@code @RolesAllowed}.
 */
@Priority(Priorities.AUTHENTICATION)
@PreMatching
public class AuthenticationFilter implements ContainerRequestFilter {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	@Context
	private HttpServletRequest httpRequest;

	/** The authentication methods, in order. */
	private final List<RequestAuthenticator> authenticators;

	/**
	 * Creates the filter.
	 *
	 * @param apiTokenService the API token use cases
	 * @param loginService the login use cases
	 */
	@Inject
	public AuthenticationFilter(ApiTokenService apiTokenService, LoginService loginService) {
		this.authenticators = List.of(
			new ApiTokenAuthenticator(apiTokenService),
			new JwtAuthenticator(loginService),
			new SessionAuthenticator());
	}

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		String clientAddress = LoggerFilter.getClientAddress(this.httpRequest);
		RequestAuthenticator.Result result = null;
		for (RequestAuthenticator authenticator : this.authenticators) {
			result = authenticator.authenticate(this.httpRequest, clientAddress);
			if (result != null) {
				break;
			}
		}
		if (result != null && result.user() == null) {
			AAA_LOG.debug("Invalid {} credentials from {}, the request is anonymous.", result.scheme(), clientAddress);
		}
		boolean secure = "https".equals(requestContext.getUriInfo().getRequestUri().getScheme());
		requestContext.setSecurityContext(result == null || result.user() == null
			? new Authorizer(null, null, secure)
			: new Authorizer(result.user(), result.scheme(), secure));
	}

	/**
	 * The security context of a request: the user, whose level gives the roles.
	 *
	 * @param user the authenticated user, or null
	 * @param scheme the authentication scheme, or null
	 * @param secure whether the request was made over HTTPS
	 */
	private record Authorizer(User user, String scheme, boolean secure) implements SecurityContext {

		@Override
		public boolean isUserInRole(String role) {
			boolean result = false;
			if (this.user != null) {
				for (User.Role knownRole : User.Role.values()) {
					if (knownRole.getName().equals(role)) {
						result = this.user.level() >= knownRole.getLevel();
						break;
					}
				}
			}
			AAA_LOG.debug("Role {} requested for user {}: result {}.", role,
				this.user == null ? "<null>" : this.user.username(), result);
			return result;
		}

		@Override
		public boolean isSecure() {
			return this.secure;
		}

		@Override
		public Principal getUserPrincipal() {
			return this.user;
		}

		@Override
		public String getAuthenticationScheme() {
			return this.scheme;
		}
	}
}
