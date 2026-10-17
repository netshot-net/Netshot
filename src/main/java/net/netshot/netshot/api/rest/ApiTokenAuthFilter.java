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
package net.netshot.netshot.api.rest;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.ApiTokenService;

/**
 * Filter to authenticate requests based on API token.
 */
@Priority(Priorities.AUTHENTICATION)
@PreMatching
public class ApiTokenAuthFilter implements ContainerRequestFilter {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/** Name of the HTTP header used for API token. */
	public static final String HTTP_API_TOKEN_HEADER = "X-Netshot-API-Token";

	/** Name of the attribute attached to the the request to carry the token. */
	public static final String ATTRIBUTE = "apiToken";

	@Inject
	private ApiTokenService apiTokenService;

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		String token = requestContext.getHeaderString(ApiTokenAuthFilter.HTTP_API_TOKEN_HEADER);
		if (token == null) {
			return;
		}
		AAA_LOG.debug("Received request with API token.");
		ApiToken apiToken = this.apiTokenService.authenticate(token);
		if (apiToken != null) {
			requestContext.setProperty(ApiTokenAuthFilter.ATTRIBUTE, apiToken);
		}
	}

}
