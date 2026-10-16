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
package net.netshot.netshot.inventory.domain;

import java.util.List;

import org.hibernate.Session;
import org.hibernate.query.Query;

import net.netshot.netshot.platform.database.Page;

/**
 * The queries on domains. The {@link Session} is always the first parameter: the caller
 * (the service) owns the session and the transaction.
 */
public class DomainRepository {

	/**
	 * Lists the domains, by ID.
	 *
	 * @param session the session
	 * @param page the window on the result
	 * @return the domains (a domain has no association to load for its representation)
	 */
	public List<Domain> findAll(Session session, Page page) {
		Query<Domain> query = session.createQuery("from Domain d order by d.id asc", Domain.class);
		page.apply(query);
		return query.list();
	}

	/**
	 * Finds a domain.
	 *
	 * @param session the session
	 * @param id the domain ID
	 * @return the domain, or null if there is no such domain
	 */
	public Domain find(Session session, long id) {
		return session.get(Domain.class, id);
	}

	/**
	 * Stores a new domain.
	 *
	 * @param session the session
	 * @param domain the domain
	 */
	public void add(Session session, Domain domain) {
		session.persist(domain);
	}

	/**
	 * Removes a domain.
	 *
	 * @param session the session
	 * @param domain the domain
	 */
	public void remove(Session session, Domain domain) {
		session.remove(domain);
	}
}
