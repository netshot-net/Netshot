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
import net.netshot.netshot.compliance.RankedSoftwareRule;
import net.netshot.netshot.compliance.SoftwareRule;

/**
 * Maps the software rules to their v1 representation, and the requests to the rules the service takes.
 */
public final class SoftwareRuleMapper {

	/** The path of the software rules. */
	public static final String PATH = "compliance/software-rules";

	private SoftwareRuleMapper() {
	}

	/**
	 * Maps a rule.
	 *
	 * @param ranked the rule with its position (null gives null)
	 * @param groups the groups, already mapped, by ID
	 * @return the representation
	 */
	public static SoftwareRuleDto toDto(RankedSoftwareRule ranked, LongFunction<GroupDto> groups) {
		if (ranked == null) {
			return null;
		}
		SoftwareRule r = ranked.rule();
		StringBuilder display = new StringBuilder(String.valueOf(r.level()));
		for (String part : new String[] {r.driver(), r.family(), r.version(), r.partNumber()}) {
			if (part != null && !part.isEmpty()) {
				display.append(' ').append(part);
			}
		}
		return new SoftwareRuleDto(r.id(), V1Links.of(PATH, r.id()), display.toString(), ranked.rank(), r.level(),
			r.targetGroupId() == null ? null : groups.apply(r.targetGroupId()), r.driver(), r.family(), r.familyRegExp(), r.version(),
			r.versionRegExp(), r.partNumber(), r.partNumberRegExp());
	}

	/**
	 * Maps a rule to the request which would define it as it is (the base of a merge patch).
	 *
	 * @param ranked the rule with its position
	 * @return the request
	 */
	public static SoftwareRuleRequest toRequest(RankedSoftwareRule ranked) {
		SoftwareRule r = ranked.rule();
		return new SoftwareRuleRequest(ranked.rank(), r.level(), r.targetGroupId() == null ? null : new RefId(r.targetGroupId()), r.driver(), r.family(), r.familyRegExp(), r.version(),
			r.versionRegExp(), r.partNumber(), r.partNumberRegExp());
	}

	private static String orEmpty(String value) {
		return value == null ? "" : value;
	}

	/**
	 * Maps a request to the rule the service takes (its ID and priority are not set).
	 *
	 * @param request the request
	 * @return the rule
	 */
	public static SoftwareRule toRule(SoftwareRuleRequest request) {
		return SoftwareRule.builder()
			.targetGroupId(request.group() == null ? null : request.group().id())
			.driver(request.driver() == null || request.driver().isEmpty() ? null : request.driver())
			.family(orEmpty(request.family()))
			.familyRegExp(request.familyRegExp())
			.version(orEmpty(request.version()))
			.versionRegExp(request.versionRegExp())
			.partNumber(orEmpty(request.partNumber()))
			.partNumberRegExp(request.partNumberRegExp())
			.level(request.level())
			.build();
	}
}
