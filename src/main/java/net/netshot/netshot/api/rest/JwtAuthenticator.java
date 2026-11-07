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

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import net.netshot.netshot.aaa.auth.LoginService;

/**
 * Authentication by a JWT access token issued by the OIDC IdP, in the {@code Authorization: Bearer} header
 * (when {@code netshot.aaa.oidc.jwt.enabled} is set).
 */
public final class JwtAuthenticator implements RequestAuthenticator {

	/** The authentication scheme. */
	public static final String SCHEME = "JWT";

	/** A JWT: three base64url parts (API tokens have no dot). */
	private static final Pattern JWT = Pattern.compile("^[A-Za-z0-9_=-]+\\.[A-Za-z0-9_=-]+\\.[A-Za-z0-9_=-]*$");

	private final LoginService loginService;

	/**
	 * Creates the authenticator.
	 *
	 * @param loginService the login use cases
	 */
	public JwtAuthenticator(LoginService loginService) {
		this.loginService = loginService;
	}

	/**
	 * Tells whether a bearer token is a JWT.
	 *
	 * @param token the token
	 * @return true if it has the form of a JWT
	 */
	static boolean isJwt(String token) {
		return JWT.matcher(token).matches();
	}

	@Override
	public Result authenticate(HttpServletRequest request, String clientAddress) {
		String token = RequestAuthenticator.bearerToken(request);
		if (token == null || !isJwt(token)) {
			return null;
		}
		return new Result(SCHEME, this.loginService.authenticateBearer(token, clientAddress));
	}
}
