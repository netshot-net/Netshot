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
import java.util.List;

import net.netshot.netshot.api.rest.v0.group.GroupMapper;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRuleDefinition;

/**
 * Maps the software compliance rules between the entities and their REST representation. All
 * the methods are pure functions.
 *
 * <p>{@link #toDto(SoftwareRule)} reads a <em>detached</em> rule whose target group is loaded.</p>
 */
public final class SoftwareRuleMapper {

	private SoftwareRuleMapper() {
		// Static helpers only
	}

	/**
	 * Maps a software rule to its representation.
	 *
	 * @param rule the rule, with its target group loaded
	 * @return the DTO
	 */
	public static SoftwareRuleDto toDto(SoftwareRule rule) {
		return new SoftwareRuleDto(
			rule.getDeviceType(),
			rule.getDriver(),
			rule.getFamily(),
			rule.isFamilyRegExp(),
			rule.getId(),
			rule.getLevel(),
			rule.getPartNumber(),
			rule.isPartNumberRegExp(),
			rule.getTargetGroup() == null ? null : GroupMapper.toDto(rule.getTargetGroup()),
			rule.getVersion(),
			rule.isVersionRegExp());
	}

	/**
	 * Maps the software rules to their representation.
	 *
	 * @param rules the rules, with their target group loaded
	 * @return the DTOs, in the same order
	 */
	public static List<SoftwareRuleDto> toDtos(List<SoftwareRule> rules) {
		List<SoftwareRuleDto> dtos = new ArrayList<>(rules.size());
		rules.forEach(rule -> dtos.add(toDto(rule)));
		return dtos;
	}

	/**
	 * Maps a request to add or edit a software rule to the command of the service.
	 *
	 * @param request the request
	 * @return the command
	 */
	public static SoftwareRuleDefinition toDefinition(SoftwareRuleRequest request) {
		return new SoftwareRuleDefinition(
			request.group() == -1 ? null : request.group(),
			request.driver(),
			request.family(),
			request.familyRegExp(),
			request.version(),
			request.versionRegExp(),
			request.partNumber(),
			request.partNumberRegExp(),
			request.level());
	}
}
