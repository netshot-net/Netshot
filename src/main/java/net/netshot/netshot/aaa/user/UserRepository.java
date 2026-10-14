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
 * The queries on the users. The {@link Session} is always the first parameter: the caller
 * (the service) owns the session and the transaction.
 */
public class UserRepository {

	/**
	 * Lists the users.
	 *
	 * @param session the session
	 * @param page the window on the result
	 * @return the users (all their state is loaded: they hold no association)
	 */
	public List<UiUser> list(Session session, Page page) {
		Query<UiUser> query = session.createQuery("from net.netshot.netshot.aaa.user.UiUser", UiUser.class);
		page.apply(query);
		return query.list();
	}

	/**
	 * Finds a user by ID.
	 *
	 * @param session the session
	 * @param id the user ID
	 * @return the user, or null if there is no such user
	 */
	public UiUser find(Session session, long id) {
		return session.get(UiUser.class, id);
	}

	/**
	 * Finds a user by name (its natural ID).
	 *
	 * @param session the session
	 * @param username the user name
	 * @return the user, or null if there is no such user
	 */
	public UiUser findByUsername(Session session, String username) {
		return session.bySimpleNaturalId(UiUser.class).load(username);
	}

	/**
	 * Stores a new user.
	 *
	 * @param session the session
	 * @param user the user
	 */
	public void persist(Session session, UiUser user) {
		session.persist(user);
	}

	/**
	 * Removes a user.
	 *
	 * @param session the session
	 * @param user the (persistent) user
	 */
	public void remove(Session session, UiUser user) {
		session.remove(user);
	}
}
