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

import net.netshot.netshot.api.rest.v0.device.DeviceMapper;
import net.netshot.netshot.compliance.DeviceRuleResult;
import net.netshot.netshot.compliance.ExemptedDevice;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.RuleDefinition;
import net.netshot.netshot.compliance.RuleSummary;
import net.netshot.netshot.compliance.RuleTestResult;
import net.netshot.netshot.compliance.JavaScriptRule;
import net.netshot.netshot.compliance.PythonRule;
import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;

/**
 * Maps the compliance rules (and what is read about them) between the entities, the projections
 * and their REST representation. All the methods are pure functions.
 */
public final class RuleMapper {

	private RuleMapper() {
		// Static helpers only
	}

	// The description of the driver of the rule: empty for any driver, "Unknown driver" when it isn't loaded
	private static String deviceDriverDescription(DriverRegistry driverRegistry, TextRule rule) {
		if ("".equals(rule.deviceDriver())) {
			return "";
		}
		DeviceDriver deviceDriver = driverRegistry.getDriverByName(rule.deviceDriver());
		if (deviceDriver == null) {
			return "Unknown driver";
		}
		return deviceDriver.getDescription();
	}

	/**
	 * Maps a rule to its representation.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param rule the rule
	 * @return the DTO, of the kind of the rule
	 */
	public static RuleDto toDto(DriverRegistry driverRegistry, Rule rule) {
		return switch (rule) {
			case TextRule text -> new RuleDto.Text(text.anyBlock(), text.context(), text.deviceDriver(),
				deviceDriverDescription(driverRegistry, text), text.enabled(), text.field(), text.id(), text.invert(),
				text.matchAll(), text.name(), text.normalize(), text.regExp(), text.text());
			case JavaScriptRule js -> new RuleDto.JavaScript(js.enabled(), js.id(), js.name(), js.script());
			case PythonRule python -> new RuleDto.Python(python.enabled(), python.id(), python.name(), python.script());
		};
	}

	/**
	 * Maps the rules to their representation.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param rules the rules
	 * @return the DTOs, in the same order
	 */
	public static List<RuleDto> toDtos(DriverRegistry driverRegistry, List<Rule> rules) {
		List<RuleDto> dtos = new ArrayList<>(rules.size());
		rules.forEach(rule -> dtos.add(toDto(driverRegistry, rule)));
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
