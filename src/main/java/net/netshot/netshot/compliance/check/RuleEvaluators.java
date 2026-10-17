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
package net.netshot.netshot.compliance.check;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Hibernate;
import org.hibernate.Session;

import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.engine.TaskContext;

/**
 * The registry of the rule evaluators, by type of rule. It refuses to be built if
 * a type of rule has no evaluator.
 */
public final class RuleEvaluators {

	private final Map<Class<? extends Rule>, RuleEvaluator<? extends Rule>> evaluators = new HashMap<>();

	/**
	 * Builds the registry.
	 *
	 * @param evaluators the evaluators, one per type of rule
	 * @throws IllegalStateException if two evaluators handle the same type, or if a type of rule has no evaluator
	 */
	public RuleEvaluators(List<RuleEvaluator<? extends Rule>> evaluators) {
		for (RuleEvaluator<? extends Rule> evaluator : evaluators) {
			if (this.evaluators.put(evaluator.ruleType(), evaluator) != null) {
				throw new IllegalStateException("Several evaluators for the rule type " + evaluator.ruleType().getSimpleName());
			}
		}
		for (Class<? extends Rule> ruleClass : Rule.getRuleClasses()) {
			if (!this.evaluators.containsKey(ruleClass)) {
				throw new IllegalStateException("No evaluator for the rule type " + ruleClass.getSimpleName());
			}
		}
	}

	/**
	 * Builds the registry with the evaluators of all the rule types.
	 *
	 * @param jsRuntime the JavaScript runtime of the rules
	 * @param pyRuntime the Python runtime of the rules
	 * @return the registry
	 */
	public static RuleEvaluators standard(JsRuleRuntime jsRuntime, PyRuleRuntime pyRuntime) {
		return new RuleEvaluators(List.of(
			new TextRuleEvaluator(),
			new JavaScriptRuleEvaluator(jsRuntime),
			new PythonRuleEvaluator(pyRuntime)));
	}

	/**
	 * Gets the evaluator of a rule.
	 *
	 * @param <R> the type of rule
	 * @param rule the rule (a proxy is accepted)
	 * @return the evaluator for the type of the rule
	 * @throws IllegalStateException if the type of the rule has no evaluator
	 */
	@SuppressWarnings("unchecked")
	public <R extends Rule> RuleEvaluator<R> forRule(R rule) {
		Class<?> ruleClass = Hibernate.unproxy(rule).getClass();
		RuleEvaluator<? extends Rule> evaluator = this.evaluators.get(ruleClass);
		if (evaluator == null) {
			throw new IllegalStateException("No evaluator for the rule type " + ruleClass.getSimpleName());
		}
		return (RuleEvaluator<R>) evaluator;
	}

	/**
	 * Checks the device against the rule, with the evaluator of the type of the rule.
	 *
	 * @param rule the rule (a proxy is accepted)
	 * @param device the device
	 * @param session the session
	 * @param taskContext the task context
	 * @return the result of the check
	 * @throws IllegalStateException if the type of the rule has no evaluator
	 */
	public CheckResult evaluate(Rule rule, Device device, Session session, TaskContext taskContext) {
		Rule realRule = (Rule) Hibernate.unproxy(rule);
		return this.forRule(realRule).evaluate(realRule, device, session, taskContext);
	}

}
