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
import net.netshot.netshot.api.rest.v1.device.DeviceDto;
import net.netshot.netshot.api.rest.v1.device.DeviceRefs;
import net.netshot.netshot.api.rest.v1.group.GroupDto;
import net.netshot.netshot.api.rest.v1.group.GroupMapper;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.inventory.group.GroupService;
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
	private final DeviceRefs deviceRefs;

	ComplianceRefs(GroupService groupService, PolicyService policyService, RuleService ruleService, DeviceRefs deviceRefs) {
		this.groupService = groupService;
		this.policyService = policyService;
		this.ruleService = ruleService;
		this.deviceRefs = deviceRefs;
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
		return policies(this.loadedPolicies(selection, property, ids));
	}

	/**
	 * Loads policies, to be shared by several resolutions of the same response.
	 *
	 * @param selection the selection of the response
	 * @param property the name of the property holding the references
	 * @param ids the IDs
	 * @return the loaded policies by ID (empty when the response doesn't write the property)
	 */
	Map<Long, PolicyDto> loadedPolicies(Selection selection, String property, Collection<Long> ids) {
		return selection.includes(property) ? this.loadPolicies(ids, selection.expands(property)) : Map.of();
	}

	/**
	 * Resolves policies from the loaded ones.
	 *
	 * @param loaded the loaded policies by ID
	 * @return the policies by ID
	 */
	static LongFunction<PolicyDto> policies(Map<Long, PolicyDto> loaded) {
		return orIds(loaded, id -> new PolicyDto(id, V1Links.of(PolicyMapper.PATH, id), null, 0, null, null, null));
	}

	private Map<Long, PolicyDto> loadPolicies(Collection<Long> ids, boolean expand) {
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
		return policies;
	}

	/**
	 * Resolves rules.
	 *
	 * @param selection the selection of the response
	 * @param property the name of the property holding the references
	 * @param ids the IDs
	 * @param loadedPolicies the policies already loaded for the response, not loaded again for the rules
	 * @return the rules by ID (null for a rule which couldn't be loaded)
	 */
	LongFunction<RuleDto> rules(Selection selection, String property, Collection<Long> ids, Map<Long, PolicyDto> loadedPolicies) {
		Map<Long, RuleDto> rules = new HashMap<>();
		Set<Long> wanted = nonNull(ids);
		if (!wanted.isEmpty() && selection.includes(property)) {
			List<Rule> found = this.ruleService.list(ListQuery.byIds(wanted)).items();
			Map<Long, PolicyDto> policies = new HashMap<>(loadedPolicies);
			policies.putAll(this.loadPolicies(found.stream().map(Rule::policyId).filter(id -> !loadedPolicies.containsKey(id)).toList(), false));
			LongFunction<PolicyDto> rulePolicies = policies(policies);
			found.forEach(r -> rules.put(r.id(), RuleMapper.toDto(r, rulePolicies)));
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
		return this.deviceRefs.byIds(selection, property, ids);
	}
}
