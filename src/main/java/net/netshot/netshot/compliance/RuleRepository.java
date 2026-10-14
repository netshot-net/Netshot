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

import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.platform.database.Page;

/**
 * The queries on compliance rules (script and text rules, the rules of the policies) and on
 * their results. The {@link Session} is always the first parameter: the caller (the service)
 * owns the session and the transaction. None of these methods catches an exception.
 */
public class RuleRepository {

	/**
	 * Finds all the rules, across all the policies, with minimal details.
	 *
	 * @param session the session
	 * @param page the window on the result
	 * @return the rule summaries
	 */
	public List<RuleSummary> findSummaries(Session session, Page page) {
		var query = session.createQuery(
			"select new " + RuleSummary.class.getName() + "(r.id, r.name, type(r), r.enabled, r.policy.id) from Rule r",
			RuleSummary.class);
		page.apply(query);
		return query.list();
	}

	/**
	 * Finds the rules of a policy.
	 *
	 * <p>Fetch plan: the rules are loaded with all their own properties, and nothing else
	 * (not the policy, not the exemptions).</p>
	 *
	 * @param session the session
	 * @param policyId the policy ID
	 * @param page the window on the result
	 * @return the rules
	 */
	public List<Rule> findByPolicy(Session session, long policyId, Page page) {
		var query = session
			.createQuery("from Rule r where r.policy.id = :pid", Rule.class)
			.setParameter("pid", policyId);
		page.apply(query);
		return query.list();
	}

	/**
	 * Finds a rule of a given policy (same fetch plan as {@link #findByPolicy}).
	 *
	 * @param session the session
	 * @param policyId the policy ID
	 * @param id the rule ID
	 * @return the rule, or null if the policy has no such rule
	 */
	public Rule findOfPolicy(Session session, long policyId, long id) {
		return session
			.createQuery("from Rule r where r.policy.id = :pid and r.id = :rid", Rule.class)
			.setParameter("pid", policyId)
			.setParameter("rid", id)
			.uniqueResult();
	}

	/**
	 * Finds a rule, without any of its associations.
	 *
	 * @param session the session
	 * @param id the rule ID
	 * @return the rule, or null if there is no such rule
	 */
	public Rule find(Session session, long id) {
		return session.get(Rule.class, id);
	}

	/**
	 * Finds a device (to be exempted from a rule).
	 * (Lookup of a related aggregate: to move to the device repository when the compliance needs it elsewhere.)
	 *
	 * @param session the session
	 * @param id the device ID
	 * @return the device, or null if there is no such device
	 */
	public Device findDevice(Session session, long id) {
		return session.get(Device.class, id);
	}

	/**
	 * Finds a device along with its last configuration, to check a rule against it.
	 *
	 * @param session the session
	 * @param id the device ID
	 * @return the device, or null if there is no such device (or it has no configuration)
	 */
	public Device findDeviceWithLastConfig(Session session, long id) {
		return session
			.createQuery("from Device d join fetch d.lastConfig where d.id = :id", Device.class)
			.setParameter("id", id)
			.uniqueResult();
	}

	/**
	 * Finds the devices exempted from a rule.
	 *
	 * @param session the session
	 * @param ruleId the rule ID
	 * @param page the window on the result
	 * @return the exempted devices
	 */
	public List<ExemptedDevice> findExemptedDevices(Session session, long ruleId, Page page) {
		var query = session.createQuery(
			"select new " + ExemptedDevice.class.getName() + "(new " + DeviceSummary.class.getName() + "("
				+ "d.id, "
				+ "d.name, "
				+ "d.family, "
				+ "d.mgmtAddress, "
				+ "d.status, "
				+ "d.driver, "
				+ "case when (d.eolDate < current_date()) then true else false end,  "
				+ "case when (d.eosDate < current_date()) then true else false end, "
				+ "case when (select count(cr) from CheckResult cr where cr.key.device = d and cr.result = :nonConforming) = 0 then true else false end, "
				+ "d.softwareLevel, "
				+ "d.networkClass), "
				+ "e.expirationDate"
				+ ") from Exemption e join e.key.device d where e.key.rule.id = :id",
			ExemptedDevice.class)
			.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING)
			.setParameter("id", ruleId);
		page.apply(query);
		return query.list();
	}

	/**
	 * Finds the compliance state of a device: for each rule of the policies applying to the groups
	 * of the device, the last result and the exemption of the device.
	 *
	 * @param session the session
	 * @param deviceId the device ID
	 * @param page the window on the result
	 * @return the compliance states
	 */
	public List<DeviceRuleResult> findDeviceResults(Session session, long deviceId, Page page) {
		var query = session.createQuery(
			"select new " + DeviceRuleResult.class.getName() + "("
				+ "r.id as ruleId, "
				+ "r.name as ruleName, "
				+ "p.id as policyId, "
				+ "p.name as policyName, "
				+ "cr.result as result, "
				+ "cr.comment as comment, "
				+ "cr.checkDate as checkDate, "
				+ "e.expirationDate as expirationDate "
				+ ") from Rule r "
				+ "join r.policy p join p.targetGroups g join g.cachedMemberships dm with dm.key.device.id = :id "
				+ "left join CheckResult cr with cr.key.rule.id = r.id and cr.key.device.id = :id "
				+ "left join r.exemptions e with e.key.device.id = :id",
			DeviceRuleResult.class)
			.setParameter("id", deviceId);
		page.apply(query);
		return query.list();
	}
}
