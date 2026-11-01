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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

import net.netshot.netshot.api.rest.v1.RefDto;
import net.netshot.netshot.api.rest.v1.RefId;
import net.netshot.netshot.api.rest.v1.StatsResponse;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.group.GroupDto;
import net.netshot.netshot.api.rest.v1.group.GroupMapper;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.PolicyDefinition;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * Maps the compliance policies to their v1 representation, and the requests to the definitions the service takes.
 */
public final class PolicyMapper {

	/** The path of the policies. */
	public static final String PATH = "compliance/policies";

	private PolicyMapper() {
	}

	/**
	 * Maps a policy.
	 *
	 * @param policy the policy (null gives null)
	 * @param groups the target groups, already mapped, by ID
	 * @return the representation
	 */
	public static PolicyDto toDto(Policy policy, LongFunction<GroupDto> groups) {
		if (policy == null) {
			return null;
		}
		return new PolicyDto(policy.id(), V1Links.of(PATH, policy.id()), policy.name(), policy.name(),
			policy.targetGroupIds().stream().sorted().map(groups::apply).toList());
	}

	/**
	 * Gets the representation of a policy referring to its groups by ID only.
	 *
	 * @param policy the policy
	 * @return the representation
	 */
	public static PolicyDto toDto(Policy policy) {
		return toDto(policy, PolicyMapper::groupRef);
	}

	/**
	 * Builds the reference to a group by its ID only.
	 *
	 * @param id the group ID
	 * @return the reference
	 */
	static GroupDto groupRef(long id) {
		return new GroupDto(id, V1Links.of(GroupMapper.PATH, id), null, 0, null, null, null, null, false, null);
	}

	/**
	 * Maps a policy to the request which would define it as it is (the base of a merge patch).
	 *
	 * @param policy the policy
	 * @return the request
	 */
	public static PolicyRequest toRequest(Policy policy) {
		return new PolicyRequest(policy.name(), policy.targetGroupIds().stream().sorted().map(RefId::new).toList());
	}

	/**
	 * Maps a request to the definition the service takes.
	 *
	 * @param request the request
	 * @return the definition
	 */
	public static PolicyDefinition toDefinition(PolicyRequest request) {
		List<Long> groups = request.targetGroups() == null ? List.of() : request.targetGroups().stream().map(RefId::id).toList();
		Set<Long> ids = new LinkedHashSet<>(groups);
		return new PolicyDefinition(request.name(), ids);
	}

	/**
	 * Builds the resolver of policy IDs into references (statistics grouped by policy).
	 *
	 * @param service the policy use cases
	 * @return the resolver
	 */
	public static StatsResponse.Resolver resolver(PolicyService service) {
		return ids -> service.list(ListQuery.byIds(ids)).items().stream()
			.collect(Collectors.toMap(Policy::id, o -> new RefDto(o.id(), V1Links.of(PATH, o.id()), o.name())));
	}
}
