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
package net.netshot.netshot.compliance.check;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.compliance.Exemption;
import net.netshot.netshot.compliance.JavaScriptRule;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.PythonRule;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;
import net.netshot.netshot.testing.fake.FakeTaskContext;

/**
 * Tests of the compliance check components: the registry of the rule evaluators
 * and the checker looping over the rules of a policy.
 */
@ScriptEngineTest
public class ComplianceCheckTest {

	private static RuleEvaluators evaluators;

	@BeforeAll
	static void init() throws Exception {
		evaluators = RuleEvaluators.standard(new JsRuleRuntime(Netshot.config()), new PyRuleRuntime(Netshot.config()), TestDrivers.registry(), null);
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
		Assertions.assertEquals(Set.of(Rule.class.getPermittedSubclasses()), new HashSet<Class<?>>(ruleClasses),
			"Rule doesn't permit all the rule types");
		for (Rule rule : List.<Rule>of(TextRule.builder("Text", 0).build(), JavaScriptRule.of("JS", 0), PythonRule.of("Python", 0))) {
			RuleEvaluator<Rule> evaluator = evaluators.forRule(rule);
			Assertions.assertEquals(rule.getClass(), evaluator.ruleType(), "Wrong evaluator for " + rule.getClass().getSimpleName());
		}
	}

	@Test
	@DisplayName("The registry refuses to be built without the evaluator of a rule type")
	void missingEvaluator() {
		Assertions.assertThrows(IllegalStateException.class, () -> new RuleEvaluators(List.of(new TextRuleEvaluator(TestDrivers.registry(), null))));
	}

	@Test
	@DisplayName("The registry refuses two evaluators for the same type")
	void duplicateEvaluator() {
		Assertions.assertThrows(IllegalStateException.class,
			() -> new RuleEvaluators(List.of(new TextRuleEvaluator(TestDrivers.registry(), null), new TextRuleEvaluator(TestDrivers.registry(), null))));
	}

	@Test
	@DisplayName("The checker evaluates all the rules of the policy, one result per rule")
	void checkerLoopsOverRules() {
		Policy policy = new Policy(0, 0, null, "Fake policy", null);
		TextRule conforming = TextRule.builder("Conforming", 0)
			.id(1)
			.enabled(true)
			.deviceDriver("CiscoIOS12")
			.field("runningConfig")
			.text("no service pad")
			.build();
		TextRule nonConforming = TextRule.builder("Non conforming", 0)
			.id(2)
			.enabled(true)
			.deviceDriver("CiscoIOS12")
			.field("runningConfig")
			.text("no service mlqksd")
			.build();
		TextRule disabled = TextRule.builder("Disabled", 0)
			.id(3)
			.field("runningConfig")
			.text("no service pad")
			.build();

		DeviceDraft device = FakeDeviceFactory.getFakeCiscoIosDraft();
		FakeTaskContext taskContext = new FakeTaskContext();
		List<CheckResult> results = new ComplianceChecker(evaluators).check(policy, List.of(conforming, nonConforming, disabled),
			device, List.of(), taskContext);

		Assertions.assertEquals(3, results.size());
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, resultOf(results, conforming));
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, resultOf(results, nonConforming));
		Assertions.assertEquals(CheckResult.ResultOption.DISABLED, resultOf(results, disabled));
		Assertions.assertTrue(taskContext.getLog().contains("Evaluating rule Conforming (policy Fake policy)..."),
			"The evaluation of each rule is logged");
	}

	@Test
	@DisplayName("A running exemption of the device gives an exempted result, an expired one doesn't")
	void checkerExemptions() {
		Policy policy = new Policy(0, 0, null, "Fake policy", null);
		TextRule rule = TextRule.builder("Conforming", 0)
			.id(1)
			.enabled(true)
			.deviceDriver("CiscoIOS12")
			.field("runningConfig")
			.text("no service pad")
			.build();
		DeviceDraft device = FakeDeviceFactory.getFakeCiscoIosDraft();
		ComplianceChecker checker = new ComplianceChecker(evaluators);
		Instant now = Instant.now();
		Assertions.assertEquals(CheckResult.ResultOption.EXEMPTED, checker.check(policy, List.of(rule), device,
			List.of(new Exemption(1, device.getId(), now.plusSeconds(3600))), new FakeTaskContext()).get(0).result());
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, checker.check(policy, List.of(rule), device,
			List.of(new Exemption(1, device.getId(), now.minusSeconds(3600))), new FakeTaskContext()).get(0).result());
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, checker.check(policy, List.of(rule), device,
			List.of(new Exemption(2, device.getId(), now.plusSeconds(3600))), new FakeTaskContext()).get(0).result());
	}

	private static CheckResult.ResultOption resultOf(List<CheckResult> results, Rule rule) {
		return results.stream()
			.filter(r -> r.ruleId() == rule.id())
			.findFirst()
			.orElseThrow()
			.result();
	}

}
