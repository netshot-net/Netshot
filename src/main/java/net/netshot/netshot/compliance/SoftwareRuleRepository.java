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
package net.netshot.netshot.compliance;

import java.util.List;

import org.hibernate.Session;

import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.database.Page;

/**
 * The queries on software rules. The {@link Session} is always the first parameter: the caller
 * (the service) owns the session and the transaction. None of these methods catches an exception.
 */
public class SoftwareRuleRepository {

	/**
	 * Finds the software rules, by priority.
	 *
	 * <p>Fetch plan: the target group is fetched with the rule.</p>
	 *
	 * @param session the session
	 * @param page the window on the result
	 * @return the rules
	 */
	public List<SoftwareRule> findAllWithGroup(Session session, Page page) {
		var query = session.createQuery(
			"from SoftwareRule r left join fetch r.targetGroup g order by r.priority asc", SoftwareRule.class);
		page.apply(query);
		return query.list();
	}

	/**
	 * Finds all the software rules, by priority, without any association.
	 *
	 * @param session the session
	 * @return the rules
	 */
	public List<SoftwareRule> findAllByPriority(Session session) {
		return session.createQuery("from SoftwareRule r order by r.priority asc", SoftwareRule.class).list();
	}

	/**
	 * Finds a software rule, without any of its associations.
	 *
	 * @param session the session
	 * @param id the rule ID
	 * @return the rule, or null if there is no such rule
	 */
	public SoftwareRule find(Session session, long id) {
		return session.get(SoftwareRule.class, id);
	}

	/**
	 * Finds a device group, to be the target of a rule.
	 *
	 * @param session the session
	 * @param id the group ID
	 * @return the group, or null if there is no such group
	 */
	public DeviceGroup findGroup(Session session, long id) {
		return session.get(DeviceGroup.class, id);
	}
}
