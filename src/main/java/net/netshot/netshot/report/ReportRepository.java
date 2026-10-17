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
package net.netshot.netshot.report;

import java.util.Date;
import java.util.List;
import java.util.Set;

import org.hibernate.Session;
import org.hibernate.query.Query;

import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.task.Task;

/**
 * The queries of the reports (read-only). Every method takes the {@link Session} first, none
 * opens a transaction.
 *
 * <p>The device lists are projected into records ({@link DeviceSummary} and the records of
 * this package). The queries of the data export load entities, which the caller reads inside
 * the same session: their fetch plan is documented on each method.</p>
 */
public class ReportRepository {

	/** The constructor expression of a {@link DeviceSummary}, for a device aliased "d". */
	private static final String DEVICE_SUMMARY = "new " + DeviceSummary.class.getName() + "("
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
		+ "d.networkClass)";

	/**
	 * Counts the configuration changes in a period.
	 *
	 * @param session the session
	 * @param start the start of the period (included)
	 * @param end the end of the period (excluded)
	 * @return the number of changes
	 */
	public Long countConfigChanges(Session session, Date start, Date end) {
		return session
			.createQuery(
				"select count(*) from Config c where c.changeDate >= :dayStart and c.changeDate < :dayEnd",
				Long.class)
			.setParameter("dayStart", start)
			.setParameter("dayEnd", end)
			.uniqueResult();
	}

	/**
	 * Finds the configuration compliance of the groups not hidden from the reports.
	 *
	 * @param session the session
	 * @param domains the domains to count the devices of (empty: any)
	 * @param deviceGroups the groups to report (empty: any)
	 * @param policies the policies to take into account (empty: any)
	 * @return the statistics
	 */
	public List<GroupConfigComplianceStat> findGroupConfigComplianceStats(Session session, Set<Long> domains,
		Set<Long> deviceGroups, Set<Long> policies) {
		String domainFilter = "";
		if (domains.size() > 0) {
			domainFilter = " d.mgmtDomain.id in (:domainIds) and";
		}
		String ccrFilter = "";
		if (policies.size() > 0) {
			ccrFilter = " rule.policy.id in (:policyIds) and";
		}
		String groupFilter = "";
		if (deviceGroups.size() > 0) {
			groupFilter = " g.id in (:groupIds) and";
		}

		Query<GroupConfigComplianceStat> query = session
			.createQuery(
				"select new " + GroupConfigComplianceStat.class.getName() + "("
					+ "g.id as groupId, "
					+ "g.name as groupName, "
					+ "g.folder as groupFolder, "
					+ "(select count(d) from g.cachedMemberships dm join dm.key.device d where" + domainFilter + " d.status = :enabled and "
					+ "(select count(ccr.result) from d.complianceCheckResults ccr join ccr.key.rule rule where"
					+ ccrFilter + " ccr.result = :nonConforming) = 0) as compliantDeviceCount, "
					+ "(select count(d) from g.cachedMemberships dm join dm.key.device d where" + domainFilter + " d.status = :enabled) as deviceCount "
					+ ") from DeviceGroup g where" + groupFilter + " g.hiddenFromReports <> true",
				GroupConfigComplianceStat.class)
			.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING)
			.setParameter("enabled", Device.Status.INPRODUCTION);
		if (domains.size() > 0) {
			query.setParameterList("domainIds", domains);
		}
		if (policies.size() > 0) {
			query.setParameterList("policyIds", policies);
		}
		if (deviceGroups.size() > 0) {
			query.setParameterList("groupIds", deviceGroups);
		}
		return query.list();
	}

	/**
	 * Counts the devices in production by date of a hardware support milestone.
	 *
	 * @param session the session
	 * @param milestone the milestone
	 * @param domains the domains to count the devices of (empty: any)
	 * @param deviceGroups the groups to count the devices of (empty: any)
	 * @return the counts
	 */
	public List<HardwareSupportCount> findHardwareSupportCounts(Session session, EndOfSupport milestone,
		Set<Long> domains, Set<Long> deviceGroups) {
		String domainFilter = domains.size() > 0 ? " and d.mgmtDomain.id in (:domainIds)" : "";
		String groupFilter = deviceGroups.size() > 0
			? " and d.id in (select d.id from Device d left join d.groupMemberships gm where gm.key.group.id in (:groupIds))"
			: "";
		Query<HardwareSupportCount> query = session
			.createQuery(
				"select new " + HardwareSupportCount.class.getName() + "(d." + milestone.property() + " AS eoxDate, count(d) as deviceCount) "
					+ "from Device d where d.status = :enabled" + domainFilter + groupFilter + " group by d." + milestone.property(),
				HardwareSupportCount.class)
			.setParameter("enabled", Device.Status.INPRODUCTION);
		if (domains.size() > 0) {
			query.setParameterList("domainIds", domains);
		}
		if (deviceGroups.size() > 0) {
			query.setParameterList("groupIds", deviceGroups);
		}
		return query.list();
	}

	/**
	 * Finds the software compliance of the groups not hidden from the reports.
	 *
	 * @param session the session
	 * @param domains the domains to count the devices of (empty: any)
	 * @return the statistics
	 */
	public List<GroupSoftwareComplianceStat> findGroupSoftwareComplianceStats(Session session, Set<Long> domains) {
		String domainFilter = "";
		if (domains.size() > 0) {
			domainFilter = " and d.mgmtDomain.id in (:domainIds)";
		}
		Query<GroupSoftwareComplianceStat> query = session
			.createQuery("select new " + GroupSoftwareComplianceStat.class.getName() + "("
				+ "g.id as groupId, "
				+ "g.name as groupName, "
				+ "(select count(d) from g.cachedMemberships dm join dm.key.device d where "
				+ "d.status = :enabled and d.softwareLevel = :gold" + domainFilter + ") as goldDeviceCount, "
				+ "(select count(d) from g.cachedMemberships dm join dm.key.device d where "
				+ "d.status = :enabled and d.softwareLevel = :silver" + domainFilter + ") as silverDeviceCount, "
				+ "(select count(d) from g.cachedMemberships dm join dm.key.device d where "
				+ "d.status = :enabled and d.softwareLevel = :bronze" + domainFilter + ") as bronzeDeviceCount, "
				+ "(select count(d) from g.cachedMemberships dm join dm.key.device d where "
				+ "d.status = :enabled" + domainFilter + ") as deviceCount "
				+ ") from DeviceGroup g where g.hiddenFromReports <> true",
				GroupSoftwareComplianceStat.class)
			.setParameter("gold", ConformanceLevel.GOLD)
			.setParameter("silver", ConformanceLevel.SILVER)
			.setParameter("bronze", ConformanceLevel.BRONZE)
			.setParameter("enabled", Device.Status.INPRODUCTION);
		if (domains.size() > 0) {
			query.setParameterList("domainIds", domains);
		}
		return query.list();
	}

	/**
	 * Finds the compliance results of the devices in production.
	 *
	 * @param session the session
	 * @param domains the domains to filter on (empty: any)
	 * @param groups the groups to filter on (empty: any)
	 * @param policies the policies to filter on (empty: any)
	 * @param results the results to filter on (empty: any)
	 * @return the results
	 */
	public List<PolicyRuleDevice> findPolicyRuleDevices(Session session, Set<Long> domains, Set<Long> groups,
		Set<Long> policies, Set<CheckResult.ResultOption> results) {
		String hqlQuery = "select new " + PolicyRuleDevice.class.getName() + "("
			+ DEVICE_SUMMARY + ", "
			+ "p.id as policyId, "
			+ "p.name as policyName, "
			+ "r.id as ruleId, "
			+ "r.name as ruleName, "
			+ "ccr.checkDate as checkDate, "
			+ "ccr.result as result "
			+ ") from Device d "
			+ "join d.complianceCheckResults ccr join ccr.key.rule r join r.policy p "
			+ "where d.status = :enabled";
		if (domains.size() > 0) {
			hqlQuery += " and d.mgmtDomain.id in (:domainIds)";
		}
		if (groups.size() > 0) {
			// Subquery rather than a join, so that a device which is in several groups is listed once
			hqlQuery += " and d.id in (select gd.id from Device gd join gd.groupMemberships gm where gm.key.group.id in (:groupIds))";
		}
		if (policies.size() > 0) {
			hqlQuery += " and p.id in (:policyIds)";
		}
		if (results.size() > 0) {
			hqlQuery += " and ccr.result in (:results)";
		}
		Query<PolicyRuleDevice> query = session.createQuery(hqlQuery, PolicyRuleDevice.class);
		query.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING);
		query.setParameter("enabled", Device.Status.INPRODUCTION);
		if (domains.size() > 0) {
			query.setParameterList("domainIds", domains);
		}
		if (groups.size() > 0) {
			query.setParameterList("groupIds", groups);
		}
		if (policies.size() > 0) {
			query.setParameterList("policyIds", policies);
		}
		if (results.size() > 0) {
			query.setParameterList("results", results);
		}
		return query.list();
	}

	/**
	 * Finds the non conforming rules of the devices in production of a group.
	 *
	 * @param session the session
	 * @param groupId the group ID
	 * @param domains the domains to filter on (empty: any)
	 * @param policies the policies to filter on (empty: any)
	 * @return the non conforming results
	 */
	public List<PolicyRuleDevice> findNonCompliantDevices(Session session, Long groupId, Set<Long> domains,
		Set<Long> policies) {
		String domainFilter = "";
		if (domains.size() > 0) {
			domainFilter = " and d.mgmtDomain.id in (:domainIds)";
		}
		String policyFilter = "";
		if (policies.size() > 0) {
			policyFilter = " and p.id in (:policyIds)";
		}
		Query<PolicyRuleDevice> query = session
			.createQuery(
				"select new " + PolicyRuleDevice.class.getName() + "("
					+ DEVICE_SUMMARY + ", "
					+ "p.id as policyId, "
					+ "p.name as policyName, "
					+ "r.id as ruleId, "
					+ "r.name as ruleName, "
					+ "ccr.checkDate as checkDate, "
					+ "ccr.result as result"
					+ ") from Device d "
					+ "join d.groupMemberships gm join d.complianceCheckResults ccr join ccr.key.rule r join r.policy p "
					+ "where gm.key.group.id = :id and ccr.result = :nonConforming and d.status = :enabled" + domainFilter + policyFilter,
				PolicyRuleDevice.class)
			.setParameter("id", groupId)
			.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING)
			.setParameter("enabled", Device.Status.INPRODUCTION);
		if (domains.size() > 0) {
			query.setParameterList("domainIds", domains);
		}
		if (policies.size() > 0) {
			query.setParameterList("policyIds", policies);
		}
		return query.list();
	}

	/**
	 * Finds the devices in production whose hardware support milestone is on a given day (or has no date).
	 *
	 * @param session the session
	 * @param milestone the milestone
	 * @param date the day, or null to find the devices without date for the milestone
	 * @param domains the domains to filter on (empty: any)
	 * @param deviceGroups the groups to filter on (empty: any)
	 * @return the devices
	 */
	public List<DeviceSummary> findDevicesByEndOfSupport(Session session, EndOfSupport milestone, Date date,
		Set<Long> domains, Set<Long> deviceGroups) {
		String domainFilter = domains.size() > 0 ? " and d.mgmtDomain.id in (:domainIds)" : "";
		String groupFilter = deviceGroups.size() > 0
			? " and d.id in (select d.id from Device d left join d.groupMemberships gm where gm.key.group.id in (:groupIds))"
			: "";
		String condition = date == null
			? "d." + milestone.property() + " is null"
			: "date(d." + milestone.property() + ") = :eoxDate";
		Query<DeviceSummary> query = session
			.createQuery(
				"select " + DEVICE_SUMMARY
					+ " from Device d where " + condition + " and d.status = :enabled" + domainFilter + groupFilter,
				DeviceSummary.class);
		if (date != null) {
			query.setParameter("eoxDate", date);
		}
		query
			.setParameter("enabled", Device.Status.INPRODUCTION)
			.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING);
		if (domains.size() > 0) {
			query.setParameterList("domainIds", domains);
		}
		if (deviceGroups.size() > 0) {
			query.setParameterList("groupIds", deviceGroups);
		}
		return query.list();
	}

	/**
	 * Finds the devices in production of a group at a given software compliance level.
	 *
	 * @param session the session
	 * @param groupId the group ID
	 * @param level the software level
	 * @param domains the domains to filter on (empty: any)
	 * @return the devices
	 */
	public List<SoftwareLevelDevice> findDevicesBySoftwareLevel(Session session, Long groupId, ConformanceLevel level,
		Set<Long> domains) {
		String domainFilter = "";
		if (domains.size() > 0) {
			domainFilter = " and d.mgmtDomain.id in (:domainIds)";
		}
		Query<SoftwareLevelDevice> query = session
			.createQuery(
				"select new " + SoftwareLevelDevice.class.getName() + "("
					+ DEVICE_SUMMARY + ", "
					+ "d.softwareVersion "
					+ ") from Device d join d.groupMemberships gm where gm.key.group.id = :id and d.softwareLevel = :level and d.status = :enabled" + domainFilter,
				SoftwareLevelDevice.class)
			.setParameter("id", groupId)
			.setParameter("level", level)
			.setParameter("enabled", Device.Status.INPRODUCTION)
			.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING);
		if (domains.size() > 0) {
			query.setParameterList("domainIds", domains);
		}
		return query.list();
	}

	/**
	 * Finds the devices in production without successful snapshot since a date.
	 *
	 * @param session the session
	 * @param when the date
	 * @param domains the domains to filter on (empty: any)
	 * @param page the window on the result
	 * @return the devices, the most recent success first
	 */
	public List<AccessFailureDevice> findAccessFailureDevices(Session session, Date when, Set<Long> domains, Page page) {
		String domainFilter = "";
		if (domains.size() > 0) {
			domainFilter = "and d.mgmtDomain.id in (:domainIds) ";
		}

		Query<AccessFailureDevice> query = session
			.createQuery(
				"select new " + AccessFailureDevice.class.getName() + "("
				+ DEVICE_SUMMARY + ", "
				+ "max(case when t.status = :success then t.executionDate end), "
				+ "max(case when t.status = :failure then t.executionDate end) "
				+ ") from Device d "
				+ "left join TakeSnapshotTask t on t.device = d "
				+ "where d.status = :enabled " + domainFilter
				+ "group by d.id, d.name, d.family, d.mgmtAddress, d.status, d.driver, d.eolDate, d.eosDate, d.softwareLevel, d.networkClass "
				+ "having max(case when t.status = :success then t.executionDate end) < :when "
				+ "or max(case when t.status = :success then t.executionDate end) is null "
				+ "order by max(case when t.status = :success then t.executionDate end) desc nulls last",
				AccessFailureDevice.class)
			.setParameter("when", when)
			.setParameter("success", Task.Status.SUCCESS)
			.setParameter("failure", Task.Status.FAILURE)
			.setParameter("enabled", Device.Status.INPRODUCTION)
			.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING);
		if (domainFilter.length() > 0) {
			query.setParameterList("domainIds", domains);
		}
		page.apply(query);
		return query.list();
	}

	// ---------------------------------------------------------------------
	// Data export
	// ---------------------------------------------------------------------

	/**
	 * Finds domains by ID.
	 *
	 * @param session the session
	 * @param domainIds the IDs
	 * @return the domains (the unknown IDs are ignored)
	 */
	public List<Domain> findDomains(Session session, Set<Long> domainIds) {
		return session
			.createQuery("select d from Domain d where d.id in (:domainIds)", Domain.class)
			.setParameterList("domainIds", domainIds)
			.list();
	}

	/**
	 * Finds device groups by ID.
	 *
	 * @param session the session
	 * @param groupIds the IDs
	 * @return the groups (the unknown IDs are ignored)
	 */
	public List<DeviceGroup> findDeviceGroups(Session session, Set<Long> groupIds) {
		return session
			.createQuery("select g from DeviceGroup g where g.id in (:groupIds)", DeviceGroup.class)
			.setParameterList("groupIds", groupIds)
			.list();
	}

	/**
	 * Finds a page of the devices to export, by name. Fetch plan: the device and its management domain.
	 *
	 * @param session the session
	 * @param domains the domains to filter on (empty: any)
	 * @param groups the groups to filter on (empty: any)
	 * @param page the window on the result
	 * @return the devices
	 */
	public List<Device> findDevicesToExport(Session session, Set<Long> domains, Set<Long> groups, Page page) {
		StringBuilder deviceHqlQuery = new StringBuilder(
			"select d from Device d left join d.groupMemberships gm "
				+ "left join fetch d.mgmtDomain "
				+ "where 1 = 1");
		if (domains.size() > 0) {
			deviceHqlQuery.append(" and d.mgmtDomain.id in (:domainIds)");
		}
		if (groups.size() > 0) {
			deviceHqlQuery.append(" and gm.key.group.id in (:groupIds)");
		}
		deviceHqlQuery.append(" order by d.name asc");
		Query<Device> deviceQuery = session.createQuery(deviceHqlQuery.toString(), Device.class);
		if (domains.size() > 0) {
			deviceQuery.setParameterList("domainIds", domains);
		}
		if (groups.size() > 0) {
			deviceQuery.setParameterList("groupIds", groups);
		}
		page.apply(deviceQuery);
		return deviceQuery.list();
	}

	/**
	 * Finds all the interfaces to export. Fetch plan: the interface and its IPv4 and IPv6 addresses
	 * (the device is read lazily).
	 *
	 * @param session the session
	 * @param domains the domains to filter on (empty: any)
	 * @param groups the groups to filter on (empty: any)
	 * @return the interfaces
	 */
	public List<NetworkInterface> findInterfacesToExport(Session session, Set<Long> domains, Set<Long> groups) {
		StringBuilder interfaceHqlQuery = new StringBuilder(
			"select ni from NetworkInterface ni "
				+ "left join fetch ni.ip4Addresses left join fetch ni.ip6Addresses "
				+ "join ni.device d left join d.groupMemberships gm where 1 = 1");
		if (domains.size() > 0) {
			interfaceHqlQuery.append(" and d.mgmtDomain.id in (:domainIds)");
		}
		if (groups.size() > 0) {
			interfaceHqlQuery.append(" and gm.key.group.id in (:groupIds)");
		}
		interfaceHqlQuery.append(" order by d.name asc, ni.id asc");
		Query<NetworkInterface> interfaceQuery = session.createQuery(interfaceHqlQuery.toString(),
			NetworkInterface.class);
		if (domains.size() > 0) {
			interfaceQuery.setParameterList("domainIds", domains);
		}
		if (groups.size() > 0) {
			interfaceQuery.setParameterList("groupIds", groups);
		}
		return interfaceQuery.list();
	}

	/**
	 * Finds a page of the modules to export. Fetch plan: the module (the device is read lazily).
	 *
	 * @param session the session
	 * @param history whether to include the removed modules
	 * @param domains the domains to filter on (empty: any)
	 * @param groups the groups to filter on (empty: any)
	 * @param page the window on the result
	 * @return the modules
	 */
	public List<Module> findModulesToExport(Session session, boolean history, Set<Long> domains, Set<Long> groups,
		Page page) {
		StringBuilder moduleHqlQuery = new StringBuilder(
			"select m from Module m "
				+ "join m.device d left join d.groupMemberships gm where 1 = 1");
		if (!history) {
			moduleHqlQuery.append(" and m.removed is not true");
		}
		if (domains.size() > 0) {
			moduleHqlQuery.append(" and d.mgmtDomain.id in (:domainIds)");
		}
		if (groups.size() > 0) {
			moduleHqlQuery.append(" and gm.key.group.id in (:groupIds)");
		}
		moduleHqlQuery.append(" order by d.name asc, m.id asc");
		Query<Module> moduleQuery = session.createQuery(moduleHqlQuery.toString(), Module.class);
		if (domains.size() > 0) {
			moduleQuery.setParameterList("domainIds", domains);
		}
		if (groups.size() > 0) {
			moduleQuery.setParameterList("groupIds", groups);
		}
		page.apply(moduleQuery);
		return moduleQuery.list();
	}

	/**
	 * Finds a page of the devices whose group memberships are to be exported, by name.
	 * Fetch plan: the device (the group memberships are read lazily).
	 *
	 * @param session the session
	 * @param domains the domains to filter on (empty: any)
	 * @param groups the groups to filter on (empty: any)
	 * @param page the window on the result
	 * @return the devices
	 */
	public List<Device> findDevicesWithGroupsToExport(Session session, Set<Long> domains, Set<Long> groups, Page page) {
		StringBuilder deviceHqlQuery = new StringBuilder(
			"select d from Device d left join d.groupMemberships gm join gm.key.group g"
				+ "left join d.mgmtDomain "
				+ "where 1 = 1");
		if (domains.size() > 0) {
			deviceHqlQuery.append(" and d.mgmtDomain.id in (:domainIds)");
		}
		if (groups.size() > 0) {
			deviceHqlQuery.append(" and gm.key.group.id in (:groupIds)");
		}
		deviceHqlQuery.append(" order by d.name asc");
		Query<Device> deviceQuery = session.createQuery(deviceHqlQuery.toString(), Device.class);
		if (domains.size() > 0) {
			deviceQuery.setParameterList("domainIds", domains);
		}
		if (groups.size() > 0) {
			deviceQuery.setParameterList("groupIds", groups);
		}
		page.apply(deviceQuery);
		return deviceQuery.list();
	}

	/**
	 * Finds a page of the device attributes to export. Fetch plan: the attribute and its device.
	 *
	 * @param session the session
	 * @param domains the domains to filter on (empty: any)
	 * @param groups the groups to filter on (empty: any)
	 * @param page the window on the result
	 * @return the attributes
	 */
	public List<DeviceAttribute> findDeviceAttributesToExport(Session session, Set<Long> domains, Set<Long> groups,
		Page page) {
		StringBuilder attributeHqlQuery = new StringBuilder(
			"select da from DeviceAttribute da "
				+ "join fetch da.device d where 1 = 1");
		if (domains.size() > 0) {
			attributeHqlQuery.append(" and d.mgmtDomain.id in (:domainIds)");
		}
		if (groups.size() > 0) {
			attributeHqlQuery.append(" and d.id in (select d.id from Device d left join d.groupMemberships gm where gm.key.group.id in (:groupIds))");
		}
		attributeHqlQuery.append(" order by d.name asc, da.id asc");
		Query<DeviceAttribute> attributeQuery = session.createQuery(attributeHqlQuery.toString(),
			DeviceAttribute.class);
		if (domains.size() > 0) {
			attributeQuery.setParameterList("domainIds", domains);
		}
		if (groups.size() > 0) {
			attributeQuery.setParameterList("groupIds", groups);
		}
		page.apply(attributeQuery);
		return attributeQuery.list();
	}
}
