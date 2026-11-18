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
package net.netshot.netshot.boot;

import net.netshot.netshot.compliance.HardwareRuleService;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.compliance.SoftwareRuleService;
import net.netshot.netshot.compliance.check.ComplianceChecker;
import net.netshot.netshot.compliance.check.JsRuleRuntime;
import net.netshot.netshot.compliance.check.PyRuleRuntime;
import net.netshot.netshot.compliance.check.RuleEvaluators;
import net.netshot.netshot.platform.database.Transactions;

/**
 * The compliance: the rule runtimes and evaluators, the checker, and the use cases of the policies and rules.
 *
 * @param jsRuleRuntime the runtime of the JavaScript rules
 * @param pyRuleRuntime the runtime of the Python rules
 * @param ruleEvaluators the evaluators of the rules
 * @param complianceChecker the compliance checker
 * @param policyService the policy use cases
 * @param ruleService the rule use cases
 * @param softwareRuleService the software rule use cases
 * @param hardwareRuleService the hardware rule use cases
 */
public record ComplianceModule(
	JsRuleRuntime jsRuleRuntime,
	PyRuleRuntime pyRuleRuntime,
	RuleEvaluators ruleEvaluators,
	ComplianceChecker complianceChecker,
	PolicyService policyService,
	RuleService ruleService,
	SoftwareRuleService softwareRuleService,
	HardwareRuleService hardwareRuleService) {

	/**
	 * Builds the module.
	 *
	 * @param core the core module
	 * @return the module
	 */
	public static ComplianceModule create(CoreModule core) {
		Transactions transactions = core.transactions();
		Repositories r = core.repositories();
		JsRuleRuntime jsRuleRuntime = new JsRuleRuntime(core.config());
		PyRuleRuntime pyRuleRuntime = new PyRuleRuntime(core.config());
		RuleEvaluators ruleEvaluators = RuleEvaluators.standard(jsRuleRuntime, pyRuleRuntime, core.driverRegistry(), r.deviceReads());
		return new ComplianceModule(jsRuleRuntime, pyRuleRuntime, ruleEvaluators, new ComplianceChecker(ruleEvaluators),
			new PolicyService(transactions, r.policy(), r.group()),
			new RuleService(transactions, r.rule(), r.policy(), ruleEvaluators, r.device()),
			new SoftwareRuleService(transactions, r.softwareRule(), core.driverRegistry(), r.group()),
			new HardwareRuleService(transactions, r.hardwareRule(), core.driverRegistry(), r.group()));
	}
}
