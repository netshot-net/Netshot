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

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import net.netshot.netshot.FakeDeviceFactory;
import net.netshot.netshot.FakeTaskContext;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.TestDrivers;
import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.rules.TextRule;
import net.netshot.netshot.inventory.device.Device;

/**
 * Tests of the compliance check components: the registry of the rule evaluators
 * and the checker looping over the rules of a policy.
 */
public class ComplianceCheckTest {

	private static RuleEvaluators evaluators;

	@BeforeAll
	static void init() throws Exception {
		TestConfig.init(new Properties());
		TestDrivers.reload();
		evaluators = RuleEvaluators.standard(new JsRuleRuntime(), new PyRuleRuntime(), TestDrivers.registry());
	}

	@SuppressWarnings("unchecked")
	private static <T> Set<Class<? extends T>> concreteSubclasses(Class<T> base) {
		Set<Class<? extends T>> found = new HashSet<>();
		for (JavaClass javaClass : new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("net.netshot.netshot")) {
			if (javaClass.isAssignableTo(base) && !javaClass.isEquivalentTo(base)
				&& !javaClass.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT)) {
				found.add((Class<? extends T>) javaClass.reflect());
			}
		}
		return found;
	}

	@Test
	@DisplayName("Every concrete Rule subclass has an evaluator")
	void everyRuleHasAnEvaluator() throws Exception {
		Set<Class<? extends Rule>> ruleClasses = concreteSubclasses(Rule.class);
		Assertions.assertFalse(ruleClasses.isEmpty(), "No rule class found, the scan is broken");
		Assertions.assertEquals(Rule.getRuleClasses(), ruleClasses, "Rule.getRuleClasses() doesn't list all the rule types");
		for (Class<? extends Rule> ruleClass : ruleClasses) {
			Assertions.assertFalse(Modifier.isAbstract(ruleClass.getModifiers()));
			Constructor<? extends Rule> constructor = ruleClass.getDeclaredConstructor();
			constructor.setAccessible(true);
			Rule rule = constructor.newInstance();
			RuleEvaluator<Rule> evaluator = evaluators.forRule(rule);
			Assertions.assertEquals(ruleClass, evaluator.ruleType(), "Wrong evaluator for " + ruleClass.getSimpleName());
		}
	}

	@Test
	@DisplayName("The registry refuses to be built without the evaluator of a rule type")
	void missingEvaluator() {
		Assertions.assertThrows(IllegalStateException.class, () -> new RuleEvaluators(List.of(new TextRuleEvaluator(TestDrivers.registry()))));
	}

	@Test
	@DisplayName("The registry refuses two evaluators for the same type")
	void duplicateEvaluator() {
		Assertions.assertThrows(IllegalStateException.class,
			() -> new RuleEvaluators(List.of(new TextRuleEvaluator(TestDrivers.registry()), new TextRuleEvaluator(TestDrivers.registry()))));
	}

	@Test
	@DisplayName("The checker evaluates all the rules of the policy and adds the results to the device")
	void checkerLoopsOverRules() {
		Policy policy = new Policy("Fake policy", null);
		TextRule conforming = new TextRule("Conforming", policy);
		conforming.setId(1);
		conforming.setEnabled(true);
		conforming.setDeviceDriver("CiscoIOS12");
		conforming.setField("runningConfig");
		conforming.setText("no service pad");
		policy.addRule(conforming);
		TextRule nonConforming = new TextRule("Non conforming", policy);
		nonConforming.setId(2);
		nonConforming.setEnabled(true);
		nonConforming.setDeviceDriver("CiscoIOS12");
		nonConforming.setField("runningConfig");
		nonConforming.setText("no service mlqksd");
		policy.addRule(nonConforming);
		TextRule disabled = new TextRule("Disabled", policy);
		disabled.setId(3);
		disabled.setField("runningConfig");
		disabled.setText("no service pad");
		policy.addRule(disabled);

		Device device = FakeDeviceFactory.getFakeCiscoIosDevice();
		FakeTaskContext taskContext = new FakeTaskContext();
		new ComplianceChecker(evaluators).check(policy, device, null, taskContext);

		Set<CheckResult> results = device.getComplianceCheckResults();
		Assertions.assertEquals(3, results.size());
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, resultOf(results, conforming));
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, resultOf(results, nonConforming));
		Assertions.assertEquals(CheckResult.ResultOption.DISABLED, resultOf(results, disabled));
		Assertions.assertTrue(taskContext.getLog().contains("Evaluating rule Conforming (policy Fake policy)..."),
			"The evaluation of each rule is logged");
	}

	@Test
	@DisplayName("The checker does nothing for a policy without target group when checking all its devices")
	void checkerWithoutTargetGroups() {
		Policy policy = new Policy("Fake policy", null);
		new ComplianceChecker(evaluators).check(policy, null, new FakeTaskContext());
	}

	@Test
	@DisplayName("A proxied rule is evaluated with the evaluator of its real type")
	void unproxy() {
		TextRule rule = new TextRule("Rule", null);
		Assertions.assertSame(rule, Hibernate.unproxy(rule));
		Assertions.assertEquals(TextRule.class, evaluators.forRule(rule).ruleType());
	}

	private static CheckResult.ResultOption resultOf(Set<CheckResult> results, Rule rule) {
		return results.stream()
			.filter(r -> r.getRule() == rule)
			.findFirst()
			.orElseThrow()
			.getResult();
	}

}
