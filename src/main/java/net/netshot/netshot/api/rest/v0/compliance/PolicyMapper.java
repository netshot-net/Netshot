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
package net.netshot.netshot.api.rest.v0.compliance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.netshot.netshot.api.rest.v0.group.DeviceGroupDto;
import net.netshot.netshot.api.rest.v0.group.GroupMapper;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.PolicyDefinition;
import net.netshot.netshot.inventory.group.DeviceGroup;

/**
 * Maps the compliance policies between the entities and their REST representation. All the
 * methods are pure functions.
 *
 * <p>{@link #toDto(Policy)} reads a <em>detached</em> policy whose target groups are loaded.</p>
 */
public final class PolicyMapper {

	private PolicyMapper() {
		// Static helpers only
	}

	/**
	 * Maps a policy to its representation.
	 *
	 * @param policy the policy, with its target groups loaded
	 * @return the DTO
	 */
	public static PolicyDto toDto(Policy policy) {
		List<DeviceGroupDto> groups = new ArrayList<>();
		// The target groups are a set: they are listed by ID
		policy.getTargetGroups().stream().sorted(Comparator.comparingLong(DeviceGroup::getId))
			.forEach(group -> groups.add(GroupMapper.toDto(group)));
		return new PolicyDto(policy.getId(), policy.getName(), groups);
	}

	/**
	 * Maps the policies to their representation.
	 *
	 * @param policies the policies, with their target groups loaded
	 * @return the DTOs, in the same order
	 */
	public static List<PolicyDto> toDtos(List<Policy> policies) {
		List<PolicyDto> dtos = new ArrayList<>(policies.size());
		policies.forEach(policy -> dtos.add(toDto(policy)));
		return dtos;
	}

	/**
	 * Maps a request to add or edit a policy to the command of the service.
	 *
	 * @param request the request
	 * @return the command
	 */
	public static PolicyDefinition toDefinition(PolicyRequest request) {
		return new PolicyDefinition(request.name(), request.targetGroups());
	}
}
