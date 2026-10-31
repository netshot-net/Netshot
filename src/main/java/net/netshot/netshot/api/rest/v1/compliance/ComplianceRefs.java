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
package net.netshot.netshot.api.rest.v1.compliance;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongFunction;

import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.task.TaskMapper;
import net.netshot.netshot.api.rest.v1.device.DeviceDto;
import net.netshot.netshot.api.rest.v1.device.DeviceMapper;
import net.netshot.netshot.api.rest.v1.group.GroupDto;
import net.netshot.netshot.api.rest.v1.group.GroupMapper;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * Loads, in one query per kind of object, the objects the compliance representations refer to (only for the
 * references the response writes, in their full representation only when expanded). A reference to an object which
 * isn't loaded is given by ID only.
 */
final class ComplianceRefs {

	private final GroupService groupService;
	private final PolicyService policyService;
	private final RuleService ruleService;
	private final SearchService searchService;
	private final DriverRegistry driverRegistry;

	ComplianceRefs(GroupService groupService, PolicyService policyService, RuleService ruleService, SearchService searchService,
		DriverRegistry driverRegistry) {
		this.groupService = groupService;
		this.policyService = policyService;
		this.ruleService = ruleService;
		this.searchService = searchService;
		this.driverRegistry = driverRegistry;
	}

	private static Set<Long> nonNull(Collection<Long> ids) {
		Set<Long> set = new HashSet<>(ids.size());
		ids.stream().filter(Objects::nonNull).forEach(set::add);
		return set;
	}

	// The loaded object, or a reference by ID only
	private static <T> LongFunction<T> orIds(Map<Long, T> loaded, LongFunction<T> byId) {
		return id -> {
			T found = loaded.get(id);
			return found != null ? found : byId.apply(id);
		};
	}

	/**
	 * Resolves groups.
	 *
	 * @param selection the selection of the response
	 * @param property the name of the property holding the references
	 * @param ids the IDs
	 * @return the groups by ID
	 */
	LongFunction<GroupDto> groups(Selection selection, String property, Collection<Long> ids) {
		Map<Long, GroupDto> groups = new HashMap<>();
		Set<Long> wanted = nonNull(ids);
		if (!wanted.isEmpty() && selection.includes(property)) {
			this.groupService.list(ListQuery.byIds(wanted)).items().forEach(g -> groups.put(g.id(), GroupMapper.toDto(g)));
		}
		return orIds(groups, PolicyMapper::groupRef);
	}

	/**
	 * Resolves policies.
	 *
	 * @param selection the selection of the response
	 * @param property the name of the property holding the references
	 * @param ids the IDs
	 * @return the policies by ID
	 */
	LongFunction<PolicyDto> policies(Selection selection, String property, Collection<Long> ids) {
		return selection.includes(property) ? this.loadPolicies(ids, selection.expands(property)) : this.loadPolicies(List.of(), false);
	}

	private LongFunction<PolicyDto> loadPolicies(Collection<Long> ids, boolean expand) {
		Map<Long, PolicyDto> policies = new HashMap<>();
		Set<Long> wanted = nonNull(ids);
		if (!wanted.isEmpty()) {
			List<Policy> found = this.policyService.list(ListQuery.byIds(wanted)).items();
			LongFunction<GroupDto> groups = expand
				? this.groups(Selection.full(PolicyDto.class), "targetGroups",
					found.stream().flatMap(p -> p.targetGroupIds().stream()).toList())
				: PolicyMapper::groupRef;
			found.forEach(p -> policies.put(p.id(), PolicyMapper.toDto(p, groups)));
		}
		return orIds(policies, id -> new PolicyDto(id, V1Links.of(PolicyMapper.PATH, id), null, null, null));
	}

	/**
	 * Resolves rules.
	 *
	 * @param selection the selection of the response
	 * @param property the name of the property holding the references
	 * @param ids the IDs
	 * @return the rules by ID (null for a rule which couldn't be loaded)
	 */
	LongFunction<RuleDto> rules(Selection selection, String property, Collection<Long> ids) {
		Map<Long, RuleDto> rules = new HashMap<>();
		Set<Long> wanted = nonNull(ids);
		if (!wanted.isEmpty() && selection.includes(property)) {
			List<Rule> found = this.ruleService.list(ListQuery.byIds(wanted)).items();
			LongFunction<PolicyDto> policies = this.loadPolicies(found.stream().map(Rule::policyId).toList(), false);
			found.forEach(r -> rules.put(r.id(), RuleMapper.toDto(r, policies)));
		}
		return id -> rules.get(id);
	}

	/**
	 * Resolves devices.
	 *
	 * @param selection the selection of the response
	 * @param property the name of the property holding the references
	 * @param ids the IDs
	 * @return the devices by ID
	 */
	LongFunction<DeviceDto> devices(Selection selection, String property, Collection<Long> ids) {
		Map<Long, DeviceDto> devices = new HashMap<>();
		Set<Long> wanted = nonNull(ids);
		if (!wanted.isEmpty() && selection.includes(property)) {
			ListQuery query = ListQuery.byIds(wanted);
			if (selection.expands(property)) {
				this.searchService.listDevices(query, null).items()
					.forEach(d -> devices.put(d.id(), DeviceMapper.toDto(this.driverRegistry, d)));
			}
			else {
				this.searchService.listDeviceSummaries(query, null).items()
					.forEach(d -> devices.put(d.id(), DeviceMapper.toDto(this.driverRegistry, d)));
			}
		}
		return orIds(devices, TaskMapper.Refs.IDS_ONLY.devices());
	}
}
