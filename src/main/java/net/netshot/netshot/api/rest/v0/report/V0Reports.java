/**
 * Copyright 2013-present Netshot
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
package net.netshot.netshot.api.rest.v0.report;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import net.netshot.netshot.api.rest.v0.V0Lists;
import net.netshot.netshot.compliance.DeviceCheckResult;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.inventory.compliance.CheckResult.ResultOption;
import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.config.ConfigService;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.Lookup;
import net.netshot.netshot.platform.query.SortOrder;
import net.netshot.netshot.platform.query.StatsQuery;
import net.netshot.netshot.platform.query.StatsRow;
import net.netshot.netshot.report.GroupConfigComplianceStat;
import net.netshot.netshot.report.PolicyRuleDevice;

/**
 * The reports of the v0 API, computed with the list and statistics use cases of the v1 API. As the v0 reports always
 * did, they only count the devices in production, and leave out the groups hidden from the reports.
 */
final class V0Reports {

	private static final String IN_PRODUCTION = Device.Status.INPRODUCTION.name();

	private final ConfigService configService;
	private final SearchService searchService;
	private final RuleService ruleService;
	private final PolicyService policyService;
	private final GroupService groupService;

	/**
	 * Instantiates the reports.
	 *
	 * @param configService the configuration use cases
	 * @param searchService the device search use cases
	 * @param ruleService the rule use cases
	 * @param policyService the policy use cases
	 * @param groupService the group use cases
	 */
	V0Reports(ConfigService configService, SearchService searchService, RuleService ruleService, PolicyService policyService,
		GroupService groupService) {
		this.configService = configService;
		this.searchService = searchService;
		this.ruleService = ruleService;
		this.policyService = policyService;
		this.groupService = groupService;
	}

	private static Criterion is(String field, Lookup lookup, Collection<?> values) {
		return new Criterion(field, lookup, List.copyOf(values));
	}

	private static Criterion is(String field, Object value) {
		return new Criterion(field, Lookup.EXACT, List.of(value));
	}

	// The filters of the devices in production, of the domains and groups (any if empty)
	private static List<Criterion> devices(Set<Long> domains, Set<Long> groups) {
		List<Criterion> criteria = new ArrayList<>();
		criteria.add(is("status", IN_PRODUCTION));
		if (!domains.isEmpty()) {
			criteria.add(is("mgmtDomain", Lookup.EXACT, domains));
		}
		if (!groups.isEmpty()) {
			criteria.add(is("group", Lookup.EXACT, groups));
		}
		return criteria;
	}

	private static ListQuery all(List<Criterion> criteria) {
		return V0Lists.query(Page.ALL, criteria.toArray(Criterion[]::new));
	}

	private static Date date(Instant instant) {
		return instant == null ? null : Date.from(instant);
	}

	// The groups of the reports: the ones not hidden from the reports (among the given ones, if any), by ID
	private List<DeviceGroup> reportGroups(Set<Long> groups) {
		return this.groupService.list(all(groups.isEmpty() ? List.of() : List.of(is("id", Lookup.EXACT, groups)))).items().stream()
			.filter(group -> !group.hiddenFromReports())
			.sorted(Comparator.comparingLong(DeviceGroup::id))
			.toList();
	}

	// Counts the devices matching filters, by the values of a field
	private Map<Object, Long> countDevices(List<Criterion> criteria, String groupBy) {
		Map<Object, Long> counts = new HashMap<>();
		for (StatsRow row : this.searchService.deviceStats(all(criteria), new StatsQuery(List.of(groupBy), null, null, null, null, null),
			null).items()) {
			counts.merge(row.keys().get(0), row.count(), Long::sum);
		}
		return counts;
	}

	// The devices which are not in production, to leave out of the compliance results
	private List<Long> notInProduction() {
		return this.searchService.listDeviceSummaries(all(List.of(new Criterion("status", Lookup.N, List.of(IN_PRODUCTION)))), null)
			.items().stream().map(DeviceSummary::id).toList();
	}

	private Map<Long, DeviceSummary> summaries(Collection<Long> ids) {
		Map<Long, DeviceSummary> devices = new HashMap<>();
		if (!ids.isEmpty()) {
			this.searchService.listDeviceSummaries(ListQuery.byIds(Set.copyOf(ids)), null).items()
				.forEach(device -> devices.put(device.id(), device));
		}
		return devices;
	}

	/**
	 * Counts the configuration changes of each of the last 7 days, today included.
	 *
	 * @param jsTimeZone the time zone of the days (an unknown one is UTC)
	 * @return the count of each day, the oldest first
	 */
	List<DailyChangeCount> last7DaysChanges(String jsTimeZone) {
		Calendar today = Calendar.getInstance(jsTimeZone == null ? TimeZone.getDefault() : TimeZone.getTimeZone(jsTimeZone));
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		List<DailyChangeCount> stats = new ArrayList<>();
		for (int d = 7; d > 0; d--) {
			Calendar dayStart = (Calendar) today.clone();
			Calendar dayEnd = (Calendar) today.clone();
			dayStart.add(Calendar.DATE, -d + 1);
			dayEnd.add(Calendar.DATE, -d + 2);
			long count = this.configService.stats(all(List.of(is("changeDate", Lookup.GTE, List.of(dayStart.toInstant())),
					is("changeDate", Lookup.LT, List.of(dayEnd.toInstant())))), new StatsQuery(List.of(), null, null, null, null, null))
				.items().stream().mapToLong(StatsRow::count).sum();
			stats.add(new DailyChangeCount(count, dayStart.getTime()));
		}
		return stats;
	}

	/**
	 * Counts the devices of each group, and the compliant ones: without non-conforming result (of the given policies).
	 *
	 * @param domains the domains of the devices (any if empty)
	 * @param groups the groups (all if empty)
	 * @param policies the policies (all if empty)
	 * @return the counts of each group
	 */
	List<GroupConfigComplianceStat> groupConfigComplianceStats(Set<Long> domains, Set<Long> groups, Set<Long> policies) {
		Map<Object, Long> deviceCounts = this.countDevices(devices(domains, Set.of()), "group");
		// The non-compliant devices of each group (one row per group and device)
		List<Criterion> criteria = new ArrayList<>(List.of(is("result", ResultOption.NONCONFORMING.name())));
		if (!policies.isEmpty()) {
			criteria.add(is("policy", Lookup.EXACT, policies));
		}
		if (!domains.isEmpty()) {
			criteria.add(is("domain", Lookup.EXACT, domains));
		}
		List<Long> excluded = this.notInProduction();
		if (!excluded.isEmpty()) {
			criteria.add(is("device", Lookup.N, excluded));
		}
		Map<Object, Long> nonCompliant = new HashMap<>();
		for (StatsRow row : this.ruleService.resultStats(all(criteria), new StatsQuery(List.of("group", "device"), null, null, null, null,
			null)).items()) {
			nonCompliant.merge(row.keys().get(0), 1L, Long::sum);
		}
		return this.reportGroups(groups).stream().map(group -> {
			long count = deviceCounts.getOrDefault(group.id(), 0L);
			return new GroupConfigComplianceStat(group.id(), group.name(), group.folder(), count - nonCompliant.getOrDefault(group.id(), 0L),
				count);
		}).toList();
	}

	/**
	 * Counts the devices by date of end of sale and of end of life.
	 *
	 * @param domains the domains of the devices (any if empty)
	 * @param groups the groups of the devices (any if empty)
	 * @return the counts
	 */
	HardwareSupportStats hardwareSupportStats(Set<Long> domains, Set<Long> groups) {
		return new HardwareSupportStats(this.supportCounts(EndOfSupport.SALE, domains, groups),
			this.supportCounts(EndOfSupport.LIFE, domains, groups));
	}

	private List<HardwareSupportCount> supportCounts(EndOfSupport milestone, Set<Long> domains, Set<Long> groups) {
		// By date, the devices without date last
		return this.countDevices(devices(domains, groups), milestone.property()).entrySet().stream()
			.map(entry -> new HardwareSupportCount(date((Instant) entry.getKey()), entry.getValue()))
			.sorted(Comparator.comparing(HardwareSupportCount::eoxDate, Comparator.nullsLast(Comparator.naturalOrder())))
			.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * Counts the devices of each group by software level.
	 *
	 * @param domains the domains of the devices (any if empty)
	 * @return the counts of each group
	 */
	List<GroupSoftwareComplianceStat> groupSoftwareComplianceStats(Set<Long> domains) {
		Map<Long, Map<Object, Long>> counts = new HashMap<>();
		for (StatsRow row : this.searchService.deviceStats(all(devices(domains, Set.of())),
			new StatsQuery(List.of("group", "softwareLevel"), null, null, null, null, null), null).items()) {
			if (row.keys().get(0) instanceof Long groupId) {
				counts.computeIfAbsent(groupId, _ -> new HashMap<>()).merge(String.valueOf(row.keys().get(1)), row.count(), Long::sum);
			}
		}
		return this.reportGroups(Set.of()).stream().map(group -> {
			Map<Object, Long> levels = counts.getOrDefault(group.id(), Map.of());
			return new GroupSoftwareComplianceStat(group.id(), group.name(), levels.getOrDefault("GOLD", 0L), levels.getOrDefault("SILVER", 0L),
				levels.getOrDefault("BRONZE", 0L), levels.values().stream().mapToLong(Long::longValue).sum());
		}).toList();
	}

	/**
	 * Lists the compliance results of the devices in production, by device, policy and rule.
	 *
	 * @param domains the domains of the devices (any if empty)
	 * @param groups the groups of the devices (any if empty)
	 * @param policies the policies (all if empty)
	 * @param results the results (all if empty)
	 * @return the results, with their device, policy and rule
	 */
	List<PolicyRuleDevice> complianceDeviceStatuses(Set<Long> domains, Set<Long> groups, Set<Long> policies, Set<ResultOption> results) {
		List<Criterion> criteria = new ArrayList<>();
		if (!domains.isEmpty()) {
			criteria.add(is("domain", Lookup.EXACT, domains));
		}
		if (!groups.isEmpty()) {
			criteria.add(is("group", Lookup.EXACT, groups));
		}
		if (!results.isEmpty()) {
			criteria.add(is("result", Lookup.EXACT, results.stream().map(Enum::name).toList()));
		}
		return this.policyRuleDevices(criteria, policies);
	}

	/**
	 * Lists the non-conforming results of the devices of a group, by device, policy and rule.
	 *
	 * @param groupId the group
	 * @param domains the domains of the devices (any if empty)
	 * @param policies the policies (all if empty)
	 * @return the results, with their device, policy and rule
	 */
	List<PolicyRuleDevice> groupNonCompliantDevices(long groupId, Set<Long> domains, Set<Long> policies) {
		List<Criterion> criteria = new ArrayList<>(List.of(is("group", groupId), is("result", ResultOption.NONCONFORMING.name())));
		if (!domains.isEmpty()) {
			criteria.add(is("domain", Lookup.EXACT, domains));
		}
		return this.policyRuleDevices(criteria, policies);
	}

	private List<PolicyRuleDevice> policyRuleDevices(List<Criterion> filters, Set<Long> policies) {
		List<Criterion> criteria = new ArrayList<>(filters);
		if (!policies.isEmpty()) {
			criteria.add(is("policy", Lookup.EXACT, policies));
		}
		List<Long> excluded = this.notInProduction();
		if (!excluded.isEmpty()) {
			criteria.add(is("device", Lookup.N, excluded));
		}
		List<DeviceCheckResult> results = this.ruleService.listResults(V0Lists.sorted(Page.ALL,
			List.of(SortOrder.asc("device"), SortOrder.asc("policy"), SortOrder.asc("rule")), criteria.toArray(Criterion[]::new))).items();
		if (results.isEmpty()) {
			return new ArrayList<>();
		}
		Map<Long, DeviceSummary> devices = this.summaries(results.stream().map(DeviceCheckResult::deviceId).toList());
		Map<Long, String> ruleNames = new HashMap<>();
		this.ruleService.list(ListQuery.byIds(results.stream().map(DeviceCheckResult::ruleId).collect(Collectors.toSet()))).items()
			.forEach(rule -> ruleNames.put(rule.id(), rule.name()));
		Map<Long, String> policyNames = new HashMap<>();
		this.policyService.list(ListQuery.byIds(results.stream().map(DeviceCheckResult::policyId).collect(Collectors.toSet()))).items()
			.forEach(policy -> policyNames.put(policy.id(), policy.name()));
		return results.stream().map(result -> new PolicyRuleDevice(devices.get(result.deviceId()), result.policyId(),
			policyNames.get(result.policyId()), result.ruleId(), ruleNames.get(result.ruleId()), date(result.checkDate()), result.result()))
			.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * Lists the devices of a date of end of sale or of end of life.
	 *
	 * @param type eos or eol
	 * @param date the day (epoch milliseconds, any time of the day in UTC), or 0 for the devices without date
	 * @param domains the domains of the devices (any if empty)
	 * @param groups the groups of the devices (any if empty)
	 * @return the devices
	 * @throws NetshotException if the type is invalid
	 */
	List<DeviceSummary> hardwareSupportDevices(String type, long date, Set<Long> domains, Set<Long> groups) {
		EndOfSupport milestone = EndOfSupport.fromCode(type);
		if (milestone == null) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, "Invalid type, expected eos or eol.");
		}
		List<Criterion> criteria = devices(domains, groups);
		if (date == 0) {
			criteria.add(is(milestone.property(), Lookup.EMPTY, List.of(true)));
		}
		else {
			Instant day = Instant.ofEpochMilli(date).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
			criteria.add(is(milestone.property(), Lookup.GTE, List.of(day)));
			criteria.add(is(milestone.property(), Lookup.LT, List.of(day.plusSeconds(24 * 3600))));
		}
		return new ArrayList<>(this.searchService.listDeviceSummaries(all(criteria), null).items());
	}

	/**
	 * Lists the devices of a group at a software level.
	 *
	 * @param groupId the group
	 * @param level the software level (an unknown one is UNKNOWN)
	 * @param domains the domains of the devices (any if empty)
	 * @return the devices, with their software version
	 */
	List<SoftwareLevelDevice> groupDevicesBySoftwareLevel(long groupId, String level, Set<Long> domains) {
		String filterLevel = "UNKNOWN";
		for (ConformanceLevel l : ConformanceLevel.values()) {
			if (l.toString().equalsIgnoreCase(level)) {
				filterLevel = l.name();
				break;
			}
		}
		List<Criterion> criteria = devices(domains, Set.of(groupId));
		criteria.add(is("softwareLevel", filterLevel));
		Map<Long, String> versions = new HashMap<>();
		this.searchService.listDevices(all(criteria), null, Set.of()).items().forEach(device -> versions.put(device.id(), device.softwareVersion()));
		return this.searchService.listDeviceSummaries(all(criteria), null).items().stream()
			.map(device -> new SoftwareLevelDevice(device, versions.get(device.id())))
			.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * Lists the devices in production without successful snapshot over a period, the most recently successful first.
	 *
	 * @param days the period (3 days if not given or invalid)
	 * @param domains the domains of the devices (any if empty)
	 * @param page the window
	 * @return the devices, with the dates of their last successful and failed snapshots
	 */
	List<AccessFailureDevice> accessFailureDevices(Integer days, Set<Long> domains, Page page) {
		int period = days == null || days < 1 ? 3 : days;
		Calendar when = Calendar.getInstance();
		when.add(Calendar.DATE, -period);
		// Last successful snapshot before the period, then never successful
		List<Device> found = new ArrayList<>();
		List<Criterion> before = devices(domains, Set.of());
		before.add(is("lastSnapshotSuccessDate", Lookup.LT, List.of(when.toInstant())));
		found.addAll(this.searchService.listDevices(V0Lists.sorted(Page.ALL, List.of(SortOrder.desc("lastSnapshotSuccessDate")),
			before.toArray(Criterion[]::new)), null, Set.of()).items());
		List<Criterion> never = devices(domains, Set.of());
		never.add(is("lastSnapshotSuccessDate", Lookup.EMPTY, List.of(true)));
		found.addAll(this.searchService.listDevices(all(never), null, Set.of()).items());
		int from = page.offset() == null ? 0 : Math.min(page.offset(), found.size());
		int to = page.limit() == null ? found.size() : Math.min(found.size(), from + page.limit());
		List<Device> window = found.subList(from, to);
		Map<Long, DeviceSummary> summaries = this.summaries(window.stream().map(Device::id).toList());
		return window.stream().map(device -> new AccessFailureDevice(summaries.get(device.id()), date(device.lastSnapshotSuccessDate()),
			date(device.lastSnapshotFailureDate()))).collect(Collectors.toCollection(ArrayList::new));
	}
}
