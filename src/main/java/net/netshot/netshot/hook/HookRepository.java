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
package net.netshot.netshot.hook;

import java.util.List;

import org.hibernate.Session;
import org.hibernate.query.Query;

import net.netshot.netshot.platform.database.Page;

/**
 * The queries on hooks. The {@link Session} is always the first parameter: the caller
 * (the service) owns the session and the transaction.
 */
public class HookRepository {

	/**
	 * Lists the hooks, in the order of the database.
	 *
	 * @param session the session
	 * @param page the window on the result
	 * @return the hooks, with their triggers loaded (a page window is then applied in memory by
	 *         Hibernate, which answers a sub list of the result)
	 */
	public List<Hook> findAll(Session session, Page page) {
		Query<Hook> query = session.createQuery("select distinct h from Hook h left join fetch h.triggers", Hook.class);
		page.apply(query);
		return query.list();
	}

	/**
	 * Lists the enabled hooks having a given trigger.
	 *
	 * @param session the session
	 * @param type the type of trigger
	 * @param item the item the trigger has to match (for a post-task trigger, the simple name of the task class)
	 * @return the hooks
	 */
	public List<Hook> findEnabledByTrigger(Session session, HookTrigger.TriggerType type, String item) {
		return session
			.createQuery("select h from Hook h join h.triggers t where t.type = :type and t.item = :item and h.enabled", Hook.class)
			.setParameter("type", type)
			.setParameter("item", item)
			.list();
	}

	/**
	 * Finds a hook.
	 *
	 * @param session the session
	 * @param id the hook ID
	 * @return the hook, or null if there is no such hook
	 */
	public Hook find(Session session, long id) {
		return session.get(Hook.class, id);
	}

	/**
	 * Finds a hook of a given kind.
	 *
	 * @param session the session
	 * @param type the class of hook
	 * @param id the hook ID
	 * @return the hook, or null if there is none of this ID and kind
	 */
	public Hook find(Session session, Class<? extends Hook> type, long id) {
		return session.get(type, id);
	}

	/**
	 * Stores a new hook.
	 *
	 * @param session the session
	 * @param hook the hook
	 */
	public void add(Session session, Hook hook) {
		session.persist(hook);
	}

	/**
	 * Saves the changes of a hook.
	 *
	 * @param session the session
	 * @param hook the hook
	 */
	public void save(Session session, Hook hook) {
		session.merge(hook);
	}

	/**
	 * Removes a hook.
	 *
	 * @param session the session
	 * @param hook the hook
	 */
	public void remove(Session session, Hook hook) {
		session.remove(hook);
	}
}
