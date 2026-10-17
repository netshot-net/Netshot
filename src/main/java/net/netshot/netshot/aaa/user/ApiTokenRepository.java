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
package net.netshot.netshot.aaa.user;

import java.util.List;

import org.hibernate.Session;
import org.hibernate.query.Query;

import net.netshot.netshot.platform.database.Page;

/**
 * The queries on the API tokens. The {@link Session} is always the first parameter: the caller
 * (the service) owns the session and the transaction.
 */
public class ApiTokenRepository {

	/**
	 * Lists the API tokens.
	 *
	 * @param session the session
	 * @param page the window on the result
	 * @return the API tokens (all their state is loaded: they hold no association)
	 */
	public List<ApiToken> list(Session session, Page page) {
		Query<ApiToken> query = session.createQuery("from ApiToken", ApiToken.class);
		page.apply(query);
		return query.list();
	}

	/**
	 * Finds an API token by ID.
	 *
	 * @param session the session
	 * @param id the token ID
	 * @return the API token, or null if there is no such token
	 */
	public ApiToken find(Session session, long id) {
		return session.get(ApiToken.class, id);
	}

	/**
	 * Finds an API token by the hash of its value.
	 *
	 * @param session the session
	 * @param hash the hash of the token value
	 * @return the API token, or null if there is no such token
	 */
	public ApiToken findByHash(Session session, String hash) {
		return session
			.createQuery("from ApiToken t where t.hashedToken = :hash", ApiToken.class)
			.setParameter("hash", hash).uniqueResult();
	}

	/**
	 * Stores a new API token.
	 *
	 * @param session the session
	 * @param apiToken the API token
	 */
	public void persist(Session session, ApiToken apiToken) {
		session.persist(apiToken);
	}

	/**
	 * Removes an API token.
	 *
	 * @param session the session
	 * @param apiToken the (persistent) API token
	 */
	public void remove(Session session, ApiToken apiToken) {
		session.remove(apiToken);
	}
}
