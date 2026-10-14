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

import org.hibernate.Hibernate;

import net.netshot.netshot.api.rest.v0.device.DeviceMapper;
import net.netshot.netshot.compliance.DeviceRuleResult;
import net.netshot.netshot.compliance.ExemptedDevice;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.RuleDefinition;
import net.netshot.netshot.compliance.RuleSummary;
import net.netshot.netshot.compliance.RuleTestResult;
import net.netshot.netshot.compliance.rules.JavaScriptRule;
import net.netshot.netshot.compliance.rules.PythonRule;
import net.netshot.netshot.compliance.rules.TextRule;

/**
 * Maps the compliance rules (and what is read about them) between the entities, the projections
 * and their REST representation. All the methods are pure functions.
 */
public final class RuleMapper {

	private RuleMapper() {
		// Static helpers only
	}

	/**
	 * Maps a rule to its representation.
	 *
	 * @param ruleOrProxy the rule (or a proxy of it)
	 * @return the DTO, of the kind of the rule
	 */
	public static RuleDto toDto(Rule ruleOrProxy) {
		Rule rule = (Rule) Hibernate.unproxy(ruleOrProxy);
		if (rule instanceof TextRule text) {
			return new RuleDto.Text(text.isAnyBlock(), text.getContext(), text.getDeviceDriver(),
				text.getDeviceDriverDescription(), text.isEnabled(), text.getField(), text.getId(), text.isInvert(),
				text.isMatchAll(), text.getName(), text.isNormalize(), text.isRegExp(), text.getText());
		}
		else if (rule instanceof JavaScriptRule js) {
			return new RuleDto.JavaScript(js.isEnabled(), js.getId(), js.getName(), js.getScript());
		}
		else if (rule instanceof PythonRule python) {
			return new RuleDto.Python(python.isEnabled(), python.getId(), python.getName(), python.getScript());
		}
		throw new IllegalArgumentException("Unknown kind of rule: " + rule.getClass().getName());
	}

	/**
	 * Maps the rules to their representation.
	 *
	 * @param rules the rules
	 * @return the DTOs, in the same order
	 */
	public static List<RuleDto> toDtos(List<Rule> rules) {
		List<RuleDto> dtos = new ArrayList<>(rules.size());
		rules.forEach(rule -> dtos.add(toDto(rule)));
		return dtos;
	}

	/**
	 * Maps the rule summaries to their representation.
	 *
	 * @param summaries the summaries
	 * @return the DTOs, in the same order
	 */
	public static List<RuleSummaryDto> toSummaryDtos(List<RuleSummary> summaries) {
		List<RuleSummaryDto> dtos = new ArrayList<>(summaries.size());
		summaries.forEach(summary -> dtos.add(
			new RuleSummaryDto(summary.id(), summary.name(), summary.type(), summary.enabled(), summary.policyId())));
		return dtos;
	}

	/**
	 * Maps the exempted devices to their representation.
	 *
	 * @param devices the exempted devices
	 * @return the DTOs, in the same order
	 */
	public static List<ExemptedDeviceDto> toExemptedDtos(List<ExemptedDevice> devices) {
		List<ExemptedDeviceDto> dtos = new ArrayList<>(devices.size());
		devices.forEach(device -> dtos.add(
			new ExemptedDeviceDto(DeviceMapper.toDto(device.device()), device.expirationDate())));
		return dtos;
	}

	/**
	 * Maps the compliance states of a device to their representation.
	 *
	 * @param results the states
	 * @return the DTOs, in the same order
	 */
	public static List<DeviceRuleResultDto> toResultDtos(List<DeviceRuleResult> results) {
		List<DeviceRuleResultDto> dtos = new ArrayList<>(results.size());
		results.forEach(result -> dtos.add(new DeviceRuleResultDto(result.ruleId(), result.ruleName(),
			result.policyId(), result.policyName(), result.result(), result.comment(), result.checkDate(),
			result.expirationDate())));
		return dtos;
	}

	/**
	 * Maps the outcome of a rule test to its representation.
	 *
	 * @param result the outcome
	 * @return the DTO
	 */
	public static RuleTestResultDto toDto(RuleTestResult result) {
		return new RuleTestResultDto(result.result(), result.log(), result.comment());
	}

	/**
	 * Maps a request to add or edit a rule to the command of the service.
	 *
	 * @param request the request
	 * @return the command
	 */
	public static RuleDefinition toDefinition(RuleRequest request) {
		return new RuleDefinition(request.type(), request.name(), request.policy(), request.enabled(),
			request.exemptions(), request.script(), request.text(), request.regExp(), request.context(),
			request.driver(), request.field(), request.anyBlock(), request.matchAll(), request.invert(),
			request.normalize());
	}

	/**
	 * Maps a request to test a rule to the command of the service.
	 *
	 * @param request the request
	 * @return the command (the device to check is not part of it)
	 */
	public static RuleDefinition toDefinition(RuleTestRequest request) {
		return new RuleDefinition(request.type(), request.name(), request.policy(), request.enabled(),
			request.exemptions(), request.script(), request.text(), request.regExp(), request.context(),
			request.driver(), request.field(), request.anyBlock(), request.matchAll(), request.invert(),
			request.normalize());
	}
}
