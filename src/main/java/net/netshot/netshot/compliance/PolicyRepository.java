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
 * The queries on compliance policies. The {@link Session} is always the first parameter: the
 * caller (the service) owns the session and the transaction. None of these methods catches an
 * exception.
 */
public class PolicyRepository {

	/**
	 * Finds the policies with their target groups.
	 *
	 * <p>Fetch plan: the target groups are fetched with the policies (the page is applied by
	 * the database to the joined rows, not to the policies).</p>
	 *
	 * @param session the session
	 * @param page the window on the result
	 * @return the policies
	 */
	public List<Policy> findAllWithGroups(Session session, Page page) {
		var query = session.createQuery("select distinct p from Policy p left join fetch p.targetGroups", Policy.class);
		page.apply(query);
		return query.list();
	}

	/**
	 * Finds a policy, without any of its associations.
	 *
	 * @param session the session
	 * @param id the policy ID
	 * @return the policy, or null if there is no such policy
	 */
	public Policy find(Session session, long id) {
		return session.get(Policy.class, id);
	}

	/**
	 * Finds a device group, to be a target of a policy.
	 *
	 * @param session the session
	 * @param id the group ID
	 * @return the group, or null if there is no such group
	 */
	public DeviceGroup findGroup(Session session, long id) {
		return session.get(DeviceGroup.class, id);
	}

	/**
	 * Deletes the compliance results of the devices of a group, for all the rules of a policy.
	 *
	 * @param session the session
	 * @param policyId the policy ID
	 * @param groupId the group ID
	 */
	public void deleteCheckResults(Session session, long policyId, long groupId) {
		session.createMutationQuery(
			"delete CheckResult cr where cr.key.rule in (select r from Rule r where r.policy.id = :policyId) "
				+ "and cr.key.device in (select dm.key.device from DeviceGroup g join g.cachedMemberships dm where g.id = :groupId)")
			.setParameter("policyId", policyId)
			.setParameter("groupId", groupId)
			.executeUpdate();
	}
}
