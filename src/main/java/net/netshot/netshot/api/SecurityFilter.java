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
package net.netshot.netshot.api;

import java.io.IOException;
import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.aaa.user.User;

/**
 * Filter to authorize based on user and role.
 */
@Priority(Priorities.AUTHORIZATION)
@PreMatching
public class SecurityFilter implements ContainerRequestFilter {

		/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	public static final String USER_ATTRIBUTE = "user";

	@Context
	private HttpServletRequest httpRequest;

	@Inject
	Provider<UriInfo> uriInfo;

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		// Try API token
		User user = (User) httpRequest.getAttribute(ApiTokenAuthFilter.ATTRIBUTE);
		if (user == null) {
			// Try to retrieve User from existing session
			HttpSession session = httpRequest.getSession(false);
			if (session != null) {
				user = (User) session.getAttribute(SecurityFilter.USER_ATTRIBUTE);
			}
		}
		requestContext.setSecurityContext(new Authorizer(user));
	}

	/**
	 * Custom authorizer class based on known Netshot roles.
	 */
	private class Authorizer implements SecurityContext {

		private final User user;

		Authorizer(User user) {
			this.user = user;
		}

		@Override
		public boolean isUserInRole(String role) {
			boolean result = false;
			if (user != null) {
				for (User.Role knownRole : User.Role.values()) {
					if (knownRole.getName().equals(role)) {
						result = user.getLevel() >= knownRole.getLevel();
						break;
					}
				}
			}
			AAA_LOG.debug("Role {} requested for user {}: result {}.", role,
				user == null ? "<null>" : user.getUsername(), result);
			return result;
		}

		@Override
		public boolean isSecure() {
			return "https".equals(uriInfo.get().getRequestUri().getScheme());
		}

		@Override
		public Principal getUserPrincipal() {
			return user;
		}

		@Override
		public String getAuthenticationScheme() {
			return SecurityContext.FORM_AUTH;
		}
	}
}
