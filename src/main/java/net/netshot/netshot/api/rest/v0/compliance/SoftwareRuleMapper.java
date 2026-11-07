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
package net.netshot.netshot.api.rest.v0.compliance;

import java.util.ArrayList;
import java.util.List;

import net.netshot.netshot.api.rest.v0.group.GroupMapper;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRuleDefinition;
import net.netshot.netshot.compliance.SoftwareRuleView;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;

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

	// The description of the driver of the rule, empty when there is no such driver
	private static String deviceType(DriverRegistry driverRegistry, SoftwareRule rule) {
		DeviceDriver deviceDriver = driverRegistry.getDriverByName(rule.driver());
		if (deviceDriver == null) {
			return "";
		}
		return deviceDriver.getDescription();
	}

	/**
	 * Maps a software rule to its representation.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param view the rule, with its target group
	 * @return the DTO
	 */
	public static SoftwareRuleDto toDto(DriverRegistry driverRegistry, SoftwareRuleView view) {
		SoftwareRule rule = view.rule();
		return new SoftwareRuleDto(
			deviceType(driverRegistry, rule),
			rule.driver(),
			rule.family(),
			rule.familyRegExp(),
			rule.id(),
			rule.level(),
			rule.partNumber(),
			rule.partNumberRegExp(),
			view.targetGroup() == null ? null : GroupMapper.toDto(view.targetGroup()),
			rule.softwareVersion(),
			rule.softwareVersionRegExp());
	}

	/**
	 * Maps the software rules to their representation.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param rules the rules, with their target group loaded
	 * @return the DTOs, in the same order
	 */
	public static List<SoftwareRuleDto> toDtos(DriverRegistry driverRegistry, List<SoftwareRuleView> rules) {
		List<SoftwareRuleDto> dtos = new ArrayList<>(rules.size());
		rules.forEach(rule -> dtos.add(toDto(driverRegistry, rule)));
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
