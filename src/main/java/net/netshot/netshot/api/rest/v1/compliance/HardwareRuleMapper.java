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

import java.util.function.LongFunction;

import net.netshot.netshot.api.rest.v1.RefId;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.group.GroupDto;
import net.netshot.netshot.compliance.HardwareRule;

/**
 * Maps the hardware rules to their v1 representation, and the requests to the rules the service takes.
 */
public final class HardwareRuleMapper {

	/** The path of the hardware rules. */
	public static final String PATH = "compliance/hardware-rules";

	private HardwareRuleMapper() {
	}

	/**
	 * Maps a rule.
	 *
	 * @param rule the rule (null gives null)
	 * @param groups the groups, already mapped, by ID
	 * @return the representation
	 */
	public static HardwareRuleDto toDto(HardwareRule rule, LongFunction<GroupDto> groups) {
		if (rule == null) {
			return null;
		}
		StringBuilder display = new StringBuilder("Hardware rule");
		for (String part : new String[] {rule.driver(), rule.family(), rule.partNumber()}) {
			if (part != null && !part.isEmpty()) {
				display.append(' ').append(part);
			}
		}
		return new HardwareRuleDto(rule.id(), V1Links.of(PATH, rule.id()), display.toString(), rule.version(), rule.changeDate(),
			rule.partNumber(), rule.endOfSale(),
			rule.endOfLife(), rule.targetGroupId() == null ? null : groups.apply(rule.targetGroupId()), rule.driver(), rule.family(),
			rule.familyRegExp(), rule.partNumberRegExp());
	}

	/**
	 * Maps a rule to the request which would define it as it is (the base of a merge patch).
	 *
	 * @param rule the rule
	 * @return the request
	 */
	public static HardwareRuleRequest toRequest(HardwareRule rule) {
		return new HardwareRuleRequest(rule.partNumber(), rule.endOfSale(), rule.endOfLife(), rule.targetGroupId() == null ? null : new RefId(rule.targetGroupId()), rule.driver(),
			rule.family(), rule.familyRegExp(), rule.partNumberRegExp());
	}

	private static String orEmpty(String value) {
		return value == null ? "" : value;
	}

	/**
	 * Maps a request to the rule the service takes (without ID).
	 *
	 * @param request the request
	 * @return the rule
	 */
	public static HardwareRule toRule(HardwareRuleRequest request) {
		return HardwareRule.builder()
			.targetGroupId(request.group() == null ? null : request.group().id())
			.driver(request.driver() == null || request.driver().isEmpty() ? null : request.driver())
			.family(orEmpty(request.family()))
			.familyRegExp(request.familyRegExp())
			.partNumber(orEmpty(request.partNumber()))
			.partNumberRegExp(request.partNumberRegExp())
			.endOfSale(request.endOfSale())
			.endOfLife(request.endOfLife())
			.build();
	}
}
