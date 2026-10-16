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
package net.netshot.netshot.vault;

import java.util.List;

import org.hibernate.Session;
import org.hibernate.query.Query;

import net.netshot.netshot.platform.database.Page;

/**
 * The queries on Vault instances. The {@link Session} is always the first parameter: the
 * caller (the service) owns the session and the transaction.
 */
public class VaultInstanceRepository {

	/**
	 * Lists the Vault instances, in the order of the database.
	 *
	 * @param session the session
	 * @param page the window on the result
	 * @return the Vault instances (they have no association to load)
	 */
	public List<VaultInstance> findAll(Session session, Page page) {
		Query<VaultInstance> query = session.createQuery("select v from VaultInstance v", VaultInstance.class);
		page.apply(query);
		return query.list();
	}

	/**
	 * Finds a Vault instance.
	 *
	 * @param session the session
	 * @param id the Vault instance ID
	 * @return the Vault instance, or null if there is no such Vault instance
	 */
	public VaultInstance find(Session session, long id) {
		return session.get(VaultInstance.class, id);
	}

	/**
	 * Finds a Vault instance of a given kind.
	 *
	 * @param session the session
	 * @param type the class of Vault instance
	 * @param id the Vault instance ID
	 * @return the Vault instance, or null if there is none of this ID and kind
	 */
	public VaultInstance find(Session session, Class<? extends VaultInstance> type, long id) {
		return session.get(type, id);
	}

	/**
	 * Stores a new Vault instance.
	 *
	 * @param session the session
	 * @param vaultInstance the Vault instance
	 */
	public void add(Session session, VaultInstance vaultInstance) {
		session.persist(vaultInstance);
	}

	/**
	 * Reloads a Vault instance from the database.
	 *
	 * @param session the session
	 * @param vaultInstance the Vault instance
	 */
	public void refresh(Session session, VaultInstance vaultInstance) {
		session.refresh(vaultInstance);
	}

	/**
	 * Saves the changes of a Vault instance.
	 *
	 * @param session the session
	 * @param vaultInstance the Vault instance
	 */
	public void save(Session session, VaultInstance vaultInstance) {
		session.merge(vaultInstance);
	}

	/**
	 * Removes a Vault instance.
	 *
	 * @param session the session
	 * @param vaultInstance the Vault instance
	 */
	public void remove(Session session, VaultInstance vaultInstance) {
		session.remove(vaultInstance);
	}
}
