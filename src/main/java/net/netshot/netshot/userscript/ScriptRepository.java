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
package net.netshot.netshot.userscript;

import java.util.List;

import org.hibernate.Session;

import net.netshot.netshot.platform.database.Page;

/**
 * The queries on the command scripts. No method opens a transaction or catches an exception.
 */
public class ScriptRepository {

	/**
	 * Finds the scripts. The entities are attached to the session: callers must not change them.
	 *
	 * @param session the session
	 * @param page the window on the result
	 * @return the scripts
	 */
	public List<DeviceJsScript> findAll(Session session, Page page) {
		var query = session.createQuery("from DeviceJsScript s", DeviceJsScript.class);
		page.apply(query);
		return query.list();
	}

	/**
	 * Finds a script.
	 *
	 * @param session the session
	 * @param id the script ID
	 * @return the script, or null if there is no such script
	 */
	public DeviceJsScript find(Session session, long id) {
		return session.get(DeviceJsScript.class, id);
	}
}
