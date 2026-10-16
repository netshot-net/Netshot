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
package net.netshot.netshot.inventory.search;

import java.util.List;

import org.hibernate.Session;
import org.hibernate.query.Query;

import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.platform.database.Page;

/**
 * The query running a device search. The {@link Session} is always the first parameter: the
 * caller (the service) owns the session and the transaction.
 */
public class SearchRepository {

	/**
	 * Finds the devices matching a parsed query, in their summary form.
	 *
	 * @param session the session
	 * @param finder the parsed query
	 * @param page the window on the result
	 * @return the device summaries
	 */
	public List<DeviceSummary> findDevices(Session session, Finder finder, Page page) {
		Query<DeviceSummary> query = session.createQuery(DeviceSummary.SELECT_HQL + finder.getHql(), DeviceSummary.class);
		finder.setVariables(query);
		query.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING);
		page.apply(query);
		return query.list();
	}
}
