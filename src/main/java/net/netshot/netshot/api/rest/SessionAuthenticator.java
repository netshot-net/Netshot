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
import jakarta.servlet.http.HttpSession;
import net.netshot.netshot.aaa.user.User;

/**
 * Authentication by the session (cookie) of a user logged in through the login endpoint (password, RADIUS,
 * TACACS+ or OIDC login of the web UI).
 */
public final class SessionAuthenticator implements RequestAuthenticator {

	/** Name of the session attribute holding the logged in user. */
	public static final String USER_ATTRIBUTE = "user";

	/** The authentication scheme. */
	public static final String SCHEME = "SESSION";

	@Override
	public Result authenticate(HttpServletRequest request, String clientAddress) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			return null;
		}
		return new Result(SCHEME, session.getAttribute(USER_ATTRIBUTE) instanceof User user ? user : null);
	}
}
