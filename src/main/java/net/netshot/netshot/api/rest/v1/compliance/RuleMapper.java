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
import java.util.stream.Collectors;

import net.netshot.netshot.api.rest.v1.RefDto;
import net.netshot.netshot.api.rest.v1.RefId;
import net.netshot.netshot.api.rest.v1.StatsResponse;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.compliance.JavaScriptRule;
import net.netshot.netshot.compliance.PythonRule;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * Maps the compliance rules to their v1 representation, and the requests to the rules the service takes.
 */
public final class RuleMapper {

	/** The path of the rules. */
	public static final String PATH = "compliance/rules";

	private RuleMapper() {
	}

	/**
	 * Maps a rule.
	 *
	 * @param rule the rule (null gives null)
	 * @param policies the policies, already mapped, by ID
	 * @return the representation
	 */
	public static RuleDto toDto(Rule rule, LongFunction<PolicyDto> policies) {
		return switch (rule) {
			case null -> null;
			case TextRule r -> new TextRuleDto(r.id(), V1Links.of(PATH, r.id()), r.name(), r.version(), r.changeDate(), "TEXT", r.name(), r.enabled(),
				policies.apply(r.policyId()), r.field(), r.context(), r.text(), r.regExp(), r.invert(), r.anyBlock(), r.matchAll(),
				r.normalize(), r.deviceDriver());
			case JavaScriptRule r -> new JavaScriptRuleDto(r.id(), V1Links.of(PATH, r.id()), r.name(), r.version(), r.changeDate(), "JAVASCRIPT", r.name(), r.enabled(),
				policies.apply(r.policyId()), r.script());
			case PythonRule r -> new PythonRuleDto(r.id(), V1Links.of(PATH, r.id()), r.name(), r.version(), r.changeDate(), "PYTHON", r.name(), r.enabled(),
				policies.apply(r.policyId()), r.script());
		};
	}

	/**
	 * Maps a rule to the request which would define it as it is (the base of a merge patch).
	 *
	 * @param rule the rule
	 * @return the request
	 */
	public static RuleRequest toRequest(Rule rule) {
		return switch (rule) {
			case TextRule r -> new TextRuleRequest(r.name(), r.enabled(), new RefId(r.policyId()), r.field(), r.context(), r.text(), r.regExp(),
				r.invert(), r.anyBlock(), r.matchAll(), r.normalize(), r.deviceDriver());
			case JavaScriptRule r -> new JavaScriptRuleRequest(r.name(), r.enabled(), new RefId(r.policyId()), r.script());
			case PythonRule r -> new PythonRuleRequest(r.name(), r.enabled(), new RefId(r.policyId()), r.script());
		};
	}

	/**
	 * Maps a request to the rule the service takes (without ID; the policy is 0 if the request doesn't give it).
	 *
	 * @param request the request
	 * @return the rule
	 */
	public static Rule toRule(RuleRequest request) {
		return toRule(request, request.enabled());
	}

	/**
	 * Maps a request to the rule the service takes, forcing the enabled flag (to test a rule).
	 *
	 * @param request the request
	 * @param enabled whether the rule is enabled
	 * @return the rule
	 */
	public static Rule toRule(RuleRequest request, boolean enabled) {
		long policyId = request.policy() == null ? 0 : request.policy().id();
		String name = request.name().trim();
		return switch (request) {
			case TextRuleRequest r -> TextRule.builder(name, policyId)
				.enabled(enabled)
				.field(r.field())
				.context(r.context())
				.text(r.text())
				.regExp(r.regExp())
				.invert(r.invert())
				.anyBlock(r.anyBlock())
				.matchAll(r.matchAll())
				.normalize(r.normalize())
				.deviceDriver(r.deviceDriver() == null || r.deviceDriver().isEmpty() ? null : r.deviceDriver())
				.build();
			case JavaScriptRuleRequest r -> new JavaScriptRule(0, 0, null, name, enabled, policyId,
				r.script() == null ? JavaScriptRule.TEMPLATE : r.script().trim());
			case PythonRuleRequest r -> new PythonRule(0, 0, null, name, enabled, policyId,
				r.script() == null ? PythonRule.TEMPLATE : r.script().trim());
		};
	}

	/**
	 * Builds the resolver of rule IDs into references (statistics grouped by rule).
	 *
	 * @param service the rule use cases
	 * @return the resolver
	 */
	public static StatsResponse.Resolver resolver(RuleService service) {
		return ids -> service.list(ListQuery.byIds(ids)).items().stream()
			.collect(Collectors.toMap(Rule::id, o -> new RefDto(o.id(), V1Links.of(PATH, o.id()), o.name())));
	}
}
