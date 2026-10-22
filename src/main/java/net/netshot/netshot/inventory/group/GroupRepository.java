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
package net.netshot.netshot.inventory.group;

import java.util.List;

import org.hibernate.Session;
import org.hibernate.query.Query;

import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.platform.database.Page;

/**
 * The queries on device groups. The {@link Session} is always the first parameter: the caller
 * (the service) owns the session and the transaction.
 *
 * <p>The groups are returned without their members: a group representation only reads its own
 * attributes.</p>
 */
public class GroupRepository {

	/**
	 * Lists the groups.
	 *
	 * @param session the session
	 * @param page the window on the result
	 * @return the groups
	 */
	public List<DeviceGroup> findAll(Session session, Page page) {
		Query<DeviceGroup> query = session.createQuery("select g from DeviceGroup g order by g.id", DeviceGroup.class);
		page.apply(query);
		return query.list();
	}

	/**
	 * Finds a group by query, so as to get the real group (never a proxy).
	 *
	 * @param session the session
	 * @param id the group ID
	 * @return the group, or null if there is no such group
	 */
	public DeviceGroup findById(Session session, long id) {
		return session
			.createQuery("select g from DeviceGroup g where g.id = :id", DeviceGroup.class)
			.setParameter("id", id)
			.uniqueResult();
	}

	/**
	 * Finds a group, to change it.
	 *
	 * @param session the session
	 * @param id the group ID
	 * @return the group, or null if there is no such group
	 */
	public DeviceGroup find(Session session, long id) {
		return session.get(DeviceGroup.class, id);
	}

	/**
	 * Finds a device, to put it into a static group.
	 * (Lookup of a related aggregate: to move to the device repository when it is the one to be used.)
	 *
	 * @param session the session
	 * @param id the device ID
	 * @return the device, or null if there is no such device
	 */
	public Device findDevice(Session session, long id) {
		return session.get(Device.class, id);
	}

	/**
	 * Stores a new group.
	 *
	 * @param session the session
	 * @param group the group
	 */
	public void add(Session session, DeviceGroup group) {
		session.persist(group);
	}

	/**
	 * Removes a group, with its memberships.
	 *
	 * @param session the session
	 * @param group the group
	 */
	public void remove(Session session, DeviceGroup group) {
		session.remove(group);
	}
}
