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
package net.netshot.netshot.testing;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.HardwareRule;
import net.netshot.netshot.compliance.HardwareRuleRepository;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.PolicyRepository;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.RuleRepository;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRuleRepository;
import net.netshot.netshot.platform.database.Transactions;

/**
 * Stores compliance data (policies, rules, check results...) for the tests, each call in its
 * own transaction, through the repositories.
 */
public final class TestCompliance {

	private static final Transactions TRANSACTIONS = new Transactions();

	private TestCompliance() {
	}

	/**
	 * Stores a policy.
	 *
	 * @param name the name
	 * @param groupIds the IDs of the target groups
	 * @return the stored policy
	 */
	public static Policy policy(String name, Long... groupIds) {
		Policy policy = new Policy(0, name, Set.of(groupIds));
		return policy.withId(TRANSACTIONS.write(dsl -> new PolicyRepository().add(dsl, policy)));
	}

	/**
	 * Stores a rule.
	 *
	 * @param <R> the type of rule
	 * @param rule the rule (its ID is ignored)
	 * @return the stored rule
	 */
	@SuppressWarnings("unchecked")
	public static <R extends Rule> R rule(R rule) {
		return (R) rule.withId(TRANSACTIONS.write(dsl -> new RuleRepository().add(dsl, rule)));
	}

	/**
	 * Stores software rules.
	 *
	 * @param rules the rules (their IDs are ignored)
	 */
	public static void softwareRules(SoftwareRule... rules) {
		TRANSACTIONS.run(dsl -> {
			for (SoftwareRule rule : rules) {
				new SoftwareRuleRepository().add(dsl, rule);
			}
		});
	}

	/**
	 * Stores hardware rules.
	 *
	 * @param rules the rules (their IDs are ignored)
	 */
	public static void hardwareRules(HardwareRule... rules) {
		TRANSACTIONS.run(dsl -> {
			for (HardwareRule rule : rules) {
				new HardwareRuleRepository().add(dsl, rule);
			}
		});
	}

	/**
	 * Stores check results.
	 *
	 * @param results the results
	 */
	public static void checkResults(CheckResult... results) {
		TRANSACTIONS.run(dsl -> new RuleRepository().saveCheckResults(dsl, List.of(results)));
	}

	/**
	 * Replaces the exemptions of a rule.
	 *
	 * @param ruleId the ID of the rule
	 * @param expirations the expiration dates, by device ID
	 */
	public static void exemptions(long ruleId, Map<Long, Instant> expirations) {
		TRANSACTIONS.run(dsl -> new RuleRepository().replaceExemptions(dsl, ruleId, expirations));
	}
}
