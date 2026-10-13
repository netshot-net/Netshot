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

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.platform.database.Database;

/**
 * Filter to authenticate requests based on API token.
 */
@Priority(Priorities.AUTHENTICATION)
@PreMatching
@Slf4j
public class ApiTokenAuthFilter implements ContainerRequestFilter {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/** Name of the HTTP header used for API token. */
	public static final String HTTP_API_TOKEN_HEADER = "X-Netshot-API-Token";

	/** Name of the attribute attached to the the request to carry the token. */
	public static final String ATTRIBUTE = "apiToken";

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		String token = requestContext.getHeaderString(ApiTokenAuthFilter.HTTP_API_TOKEN_HEADER);
		if (token == null) {
			return;
		}
		AAA_LOG.debug("Received request with API token.");
		token = token.trim();
		String hash = ApiToken.hashToken(token);
		Session session = Database.getSession();
		try {
			ApiToken apiToken = session
				.createQuery("from ApiToken t where t.hashedToken = :hash", ApiToken.class)
				.setParameter("hash", hash).uniqueResult();
			if (apiToken == null) {
				AAA_LOG.warn("Invalid API token received.");
			}
			else {
				AAA_LOG.info("Successful API token usage {}.", apiToken);
				requestContext.setProperty(ApiTokenAuthFilter.ATTRIBUTE, apiToken);
			}
		}
		catch (HibernateException e) {
			log.error("Database error while looking for API token", e);
		}
		finally {
			session.close();
		}
	}

}
