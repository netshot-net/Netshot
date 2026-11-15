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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.netshot.netshot.api.rest.v0.device.DeviceMapper;
import net.netshot.netshot.compliance.DeviceRuleResult;
import net.netshot.netshot.compliance.ExemptedDevice;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.RuleDefinition;
import net.netshot.netshot.compliance.RuleService.Replacement;
import net.netshot.netshot.compliance.RuleSummary;
import net.netshot.netshot.compliance.RuleTestResult;
import net.netshot.netshot.compliance.JavaScriptRule;
import net.netshot.netshot.compliance.PythonRule;
import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

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
	 * Builds the rule to add from a request to add a rule, as the v0 API always did: the rule is disabled, its name and
	 * script are trimmed (the template script of the kind if there is none), the properties of a text rule which are not
	 * given keep their default values, and the exemptions are ignored.
	 *
	 * @param definition the request (its name must not be blank)
	 * @return the rule, or null if the type of rule is unknown
	 */
	public static Rule toNewRule(RuleDefinition definition) {
		String name = definition.name().trim();
		long policyId = definition.policyId();
		return switch (definition.type()) {
			case "TextRule" -> applyTextRule(TextRule.builder(name, policyId), definition).build();
			case "JavaScriptRule" -> definition.script() == null ? JavaScriptRule.of(name, policyId)
				: new JavaScriptRule(0, 0, null, name, false, policyId, definition.script().trim());
			case "PythonRule" -> definition.script() == null ? PythonRule.of(name, policyId)
				: new PythonRule(0, 0, null, name, false, policyId, definition.script().trim());
			case null, default -> null;
		};
	}

	/**
	 * Builds the replacement of a rule from a request to edit it, as the v0 API always did: the name (trimmed), the
	 * script (trimmed) and the properties of a text rule are changed when they are given, the enabled flag and all the
	 * exemptions are replaced; the policy and the kind of the rule are kept (the posted ones are ignored).
	 *
	 * @param existing the current rule
	 * @param definition the request
	 * @return the replacement
	 * @throws NetshotException if the given name is blank
	 */
	public static Replacement toReplacement(Rule existing, RuleDefinition definition) {
		String name = existing.name();
		if (definition.name() != null) {
			name = definition.name().trim();
			if (name.isEmpty()) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE_NAME, "Invalid rule name.");
			}
		}
		boolean enabled = definition.enabled();
		String script = definition.script() == null ? null : definition.script().trim();
		Rule edited = switch (existing) {
			case JavaScriptRule jsRule -> new JavaScriptRule(0, 0, null, name, enabled, jsRule.policyId(),
				script != null ? script : jsRule.script());
			case PythonRule pyRule -> new PythonRule(0, 0, null, name, enabled, pyRule.policyId(),
				script != null ? script : pyRule.script());
			case TextRule textRule -> applyTextRule(textRule.toBuilder().name(name).enabled(enabled), definition).build();
		};
		Map<Long, Instant> exemptions = new HashMap<>();
		for (Map.Entry<Long, Date> exemption : definition.exemptions().entrySet()) {
			exemptions.put(exemption.getKey(), exemption.getValue() == null ? null : exemption.getValue().toInstant());
		}
		return new Replacement(edited, exemptions);
	}

	private static TextRule.TextRuleBuilder applyTextRule(TextRule.TextRuleBuilder textRule, RuleDefinition definition) {
		if (definition.text() != null) {
			textRule.text(definition.text());
		}
		if (definition.regExp() != null) {
			textRule.regExp(definition.regExp());
		}
		if (definition.context() != null) {
			textRule.context(definition.context());
		}
		if (definition.field() != null) {
			textRule.field(definition.field());
		}
		if (definition.driver() != null) {
			textRule.deviceDriver(definition.driver());
		}
		if (definition.invert() != null) {
			textRule.invert(definition.invert());
		}
		if (definition.matchAll() != null) {
			textRule.matchAll(definition.matchAll());
		}
		if (definition.anyBlock() != null) {
			textRule.anyBlock(definition.anyBlock());
		}
		if (definition.normalize() != null) {
			textRule.normalize(definition.normalize());
		}
		return textRule;
	}

	/**
	 * Builds the rule to test from a request to test a rule, as the v0 API always did: an enabled rule, the
	 * properties of a text rule which are not given keeping their default values.
	 *
	 * @param definition the request
	 * @return the rule
	 * @throws NetshotException if the type of rule is unknown
	 */
	public static Rule toTestRule(RuleDefinition definition) {
		return switch (definition.type()) {
			case "TextRule" -> applyTextRule(TextRule.builder("TEST", 0), definition).enabled(true).build();
			case "JavaScriptRule" -> new JavaScriptRule(0, 0, null, "TEST", true, 0, definition.script());
			case "PythonRule" -> new PythonRule(0, 0, null, "TEST", true, 0, definition.script());
			case null, default -> throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE, "Invalid rule type");
		};
	}

	/**
	 * Gives the result of a rule test as the v0 API always did: a device which doesn't exist or has no
	 * configuration makes the rule "not applicable".
	 *
	 * @param test runs the test
	 * @return the result
	 */
	public static RuleTestResult toTestResult(Supplier<RuleTestResult> test) {
		RuleTestResult result;
		try {
			result = test.get();
		}
		catch (NetshotException e) {
			if (e.getErrorCode() != ErrorCode.NETSHOT_DEVICE_NOT_FOUND) {
				throw e;
			}
			result = null;
		}
		// Without a configuration, the rule is not evaluated (no log)
		if (result == null || result.result() == CheckResult.ResultOption.NOTAPPLICABLE && result.log() == null) {
			return new RuleTestResult(CheckResult.ResultOption.NOTAPPLICABLE, null, "Unable to find the device");
		}
		return result;
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
