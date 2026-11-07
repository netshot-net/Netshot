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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import net.netshot.netshot.aaa.user.User;

/**
 * A method of authentication of the REST requests (API token, JWT access token, session), tried in turn by the
 * {@link AuthenticationFilter}. A method first tells whether the request carries its kind of credentials; the
 * first method whose credentials are present decides: the request is then authenticated by these credentials
 * or, if they are invalid, anonymous (without falling back to another method).
 */
public interface RequestAuthenticator {

	/**
	 * The result of the authentication of a request carrying credentials of the method.
	 *
	 * @param scheme the authentication scheme (as reported by the security context)
	 * @param user the authenticated user, or null if the credentials are invalid
	 */
	record Result(String scheme, User user) {
	}

	/**
	 * Authenticates a request.
	 *
	 * @param request the HTTP request
	 * @param clientAddress the address of the client (for the AAA logs)
	 * @return the result, or null if the request carries no credentials of this method
	 */
	Result authenticate(HttpServletRequest request, String clientAddress);

	/**
	 * Gets the token of the {@code Authorization: Bearer <token>} header of a request.
	 *
	 * @param request the HTTP request
	 * @return the token, or null if there is none
	 */
	static String bearerToken(HttpServletRequest request) {
		String prefix = "Bearer ";
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
			return null;
		}
		String token = authorization.substring(prefix.length()).trim();
		return token.isEmpty() ? null : token;
	}
}
