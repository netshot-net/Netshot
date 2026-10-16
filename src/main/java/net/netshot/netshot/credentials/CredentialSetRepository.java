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
package net.netshot.netshot.credentials;

import java.util.List;

import org.hibernate.Session;
import org.hibernate.query.Query;

import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.database.Page;

/**
 * The queries on the global credential sets. The {@link Session} is always the first
 * parameter: the caller (the service) owns the session and the transaction.
 */
public class CredentialSetRepository {

	/**
	 * Lists the global credential sets (not the ones specific to a device access), in the order
	 * of the database.
	 *
	 * @param session the session
	 * @param page the window on the result
	 * @return the credential sets, with everything their representation reads: the associations
	 *         to the domain and to the Vault instances are eager
	 */
	public List<DeviceCredentialSet> findGlobal(Session session, Page page) {
		Query<DeviceCredentialSet> query = session
			.createQuery("select cs from DeviceCredentialSet cs where not (cs.deviceSpecific)", DeviceCredentialSet.class);
		page.apply(query);
		return query.list();
	}

	/**
	 * Finds a credential set of any kind, global or specific to a device access.
	 *
	 * @param session the session
	 * @param id the credential set ID
	 * @return the credential set, or null if there is no such credential set
	 */
	public DeviceCredentialSet find(Session session, long id) {
		return session.get(DeviceCredentialSet.class, id);
	}

	/**
	 * Finds a credential set of a given kind.
	 *
	 * @param session the session
	 * @param type the class of credential set
	 * @param id the credential set ID
	 * @return the credential set, or null if there is no credential set of this ID and kind
	 */
	public DeviceCredentialSet find(Session session, Class<? extends DeviceCredentialSet> type, long id) {
		return session.get(type, id);
	}

	/**
	 * Finds the domain a credential set is restricted to.
	 *
	 * @param session the session
	 * @param id the domain ID
	 * @return the domain, or null if there is no such domain
	 */
	public Domain findDomain(Session session, long id) {
		return session.get(Domain.class, id);
	}

	/**
	 * Stores a new credential set.
	 *
	 * @param session the session
	 * @param credentialSet the credential set
	 */
	public void add(Session session, DeviceCredentialSet credentialSet) {
		session.persist(credentialSet);
	}

	/**
	 * Reloads a credential set from the database.
	 *
	 * @param session the session
	 * @param credentialSet the credential set
	 */
	public void refresh(Session session, DeviceCredentialSet credentialSet) {
		session.refresh(credentialSet);
	}

	/**
	 * Saves the changes of a credential set.
	 *
	 * @param session the session
	 * @param credentialSet the credential set
	 */
	public void save(Session session, DeviceCredentialSet credentialSet) {
		session.merge(credentialSet);
	}

	/**
	 * Removes a credential set.
	 *
	 * @param session the session
	 * @param credentialSet the credential set
	 */
	public void remove(Session session, DeviceCredentialSet credentialSet) {
		session.remove(credentialSet);
	}
}
