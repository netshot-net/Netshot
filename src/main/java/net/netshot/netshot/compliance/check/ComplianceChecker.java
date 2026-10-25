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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.Exemption;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.platform.log.TaskContext;

/**
 * Checks devices against the rules of the policies.
 */
public class ComplianceChecker {

	private final RuleEvaluators evaluators;

	/**
	 * Builds the checker.
	 *
	 * @param evaluators the rule evaluators
	 */
	public ComplianceChecker(RuleEvaluators evaluators) {
		this.evaluators = evaluators;
	}

	/**
	 * Checks a given device against the rules of a policy.
	 *
	 * @param policy the policy
	 * @param rules the rules of the policy
	 * @param device the device
	 * @param exemptions the exemptions of the device
	 * @param taskContext the task context
	 * @return the results, one per rule
	 */
	public List<CheckResult> check(Policy policy, List<Rule> rules, DeviceDraft device, Collection<Exemption> exemptions,
		TaskContext taskContext) {
		Instant now = Instant.now();
		List<CheckResult> results = new ArrayList<>();
		for (Rule rule : rules) {
			taskContext.info("Evaluating rule {} (policy {})...", rule.name(), policy.name());
			boolean exempted = exemptions.stream().anyMatch(e -> e.ruleId() == rule.id() && e.isActive(now));
			results.add(this.evaluators.evaluate(rule, device, exempted, taskContext));
		}
		return results;
	}

}
