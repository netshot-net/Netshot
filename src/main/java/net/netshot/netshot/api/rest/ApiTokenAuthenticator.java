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
import net.netshot.netshot.aaa.user.ApiTokenService;

/**
 * Authentication by an API token, in the {@code X-Netshot-API-Token} header, or in the
 * {@code Authorization: Bearer} header when it isn't a JWT (see {@link JwtAuthenticator}).
 */
public final class ApiTokenAuthenticator implements RequestAuthenticator {

	/** Name of the HTTP header carrying the API token. */
	public static final String HTTP_API_TOKEN_HEADER = "X-Netshot-API-Token";

	/** The authentication scheme. */
	public static final String SCHEME = "API_TOKEN";

	private final ApiTokenService apiTokenService;

	/**
	 * Creates the authenticator.
	 *
	 * @param apiTokenService the API token use cases
	 */
	public ApiTokenAuthenticator(ApiTokenService apiTokenService) {
		this.apiTokenService = apiTokenService;
	}

	@Override
	public Result authenticate(HttpServletRequest request, String clientAddress) {
		String token = request.getHeader(HTTP_API_TOKEN_HEADER);
		if (token == null || token.isBlank()) {
			token = RequestAuthenticator.bearerToken(request);
			if (token == null || JwtAuthenticator.isJwt(token)) {
				return null;
			}
		}
		return new Result(SCHEME, this.apiTokenService.authenticate(token, clientAddress));
	}
}
