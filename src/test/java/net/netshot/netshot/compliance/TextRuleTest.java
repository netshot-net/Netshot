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
package net.netshot.netshot.compliance;


import net.netshot.netshot.Netshot;
import net.netshot.netshot.compliance.check.JsRuleRuntime;
import net.netshot.netshot.compliance.check.PyRuleRuntime;
import net.netshot.netshot.compliance.check.RuleEvaluators;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.task.engine.TaskContext;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;
import net.netshot.netshot.testing.fake.FakeTaskContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ScriptEngineTest
@DisplayName("Simple Text Rule")
public class TextRuleTest {

	static RuleEvaluators evaluators;

	@BeforeAll
	static void initNetshot() throws Exception {
		evaluators = RuleEvaluators.standard(new JsRuleRuntime(Netshot.config()), new PyRuleRuntime(Netshot.config()), TestDrivers.registry());
	}

	Policy policy = new Policy(0, "Fake policy", null);
	DeviceDraft device = FakeDeviceFactory.getFakeCiscoIosDraft();
	TaskContext taskContext = new FakeTaskContext();
	TextRule rule;

	TextRuleTest() {
		this.rule = TextRule.builder("Testing rule", policy.id()).build();
		rule = rule.toBuilder().enabled(true).build();
		rule = rule.toBuilder().deviceDriver("CiscoIOS12").build();
		rule = rule.toBuilder().field("runningConfig").build();
		rule = rule.toBuilder().regExp(false).build();
		rule = rule.toBuilder().context(null).build();
		rule = rule.toBuilder().anyBlock(false).build();
		rule = rule.toBuilder().matchAll(false).build();
		rule = rule.toBuilder().normalize(false).build();
	}

	@Test
	@DisplayName("Conforming rule")
	void matchAll() {
		rule = rule.toBuilder().text("no service pad").build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Non-conforming rule")
	void matchAllFail() {
		rule = rule.toBuilder().text("no service mlqksd").build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.result(),
			"The result is not NONCONFORMING");
	}

	@Test
	@DisplayName("Disabled rule")
	void disabled() {
		rule = rule.toBuilder().enabled(false).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.DISABLED, result.result(),
			"The result is not DISABLED");
	}

	@Test
	@DisplayName("Unknown field rule")
	void unknownField() {
		rule = rule.toBuilder().field("doesNotExist").build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.INVALIDRULE, result.result(),
			"The result is not INVALIDRULE");
	}

	@Test
	@DisplayName("Conforming rule with context, all blocks")
	void contextAllBlocks() {
		rule = rule.toBuilder().context("^interface .*").build();
		rule = rule.toBuilder().text(" description ").build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Non-conforming rule with context, all blocks")
	void contextAllBlocksFail() {
		rule = rule.toBuilder().context("^interface .*").build();
		rule = rule.toBuilder().text(" no shutdown").build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.result(),
			"The result is not NONCONFORMING");
	}

	@Test
	@DisplayName("Conforming rule with context, any block")
	void contextAnyBlock() {
		rule = rule.toBuilder().context("^interface .*").build();
		rule = rule.toBuilder().text(" no shutdown").build();
		rule = rule.toBuilder().anyBlock(true).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Non-conforming rule with context, any block")
	void contextAnyBlockFail() {
		rule = rule.toBuilder().context("^interface .*").build();
		rule = rule.toBuilder().text(" no description").build();
		rule = rule.toBuilder().anyBlock(true).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.result(),
			"The result is not NONCONFORMING");
	}

	@Test
	@DisplayName("Conforming rule with context, any block, match all")
	void contextAnyBlockMatchAll() {
		rule = rule.toBuilder().context("^line .*").build();
		rule = rule.toBuilder().text(" password something").build();
		rule = rule.toBuilder().anyBlock(true).build();
		rule = rule.toBuilder().matchAll(true).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Multiline Regexp")
	void multilineRegexp() {
		rule = rule.toBuilder().text("(?m)^hostname router[0-9]+$").build();
		rule = rule.toBuilder().regExp(true).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Regexp fail")
	void regexpFail() {
		rule = rule.toBuilder().text("^hostname router[0-9]+").build();
		rule = rule.toBuilder().regExp(true).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.result(),
			"The result is not NONCONFORMING");
	}

	@Test
	@DisplayName("Invalid Regexp")
	void invalidRegexp() {
		rule = rule.toBuilder().text("^(h").build();
		rule = rule.toBuilder().regExp(true).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.INVALIDRULE, result.result(),
			"The result is not INVALIDRULE");
	}

	@Test
	@DisplayName("Without normalization")
	void withoutNormalization() {
		rule = rule.toBuilder().text("(?m)^ipv6 unicast-routing$").build();
		rule = rule.toBuilder().regExp(true).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.result(),
			"The result is not NONCONFORMING");
	}

	@Test
	@DisplayName("With normalization")
	void withNormalization() {
		rule = rule.toBuilder().text("(?m)^ipv6 unicast-routing$").build();
		rule = rule.toBuilder().regExp(true).build();
		rule = rule.toBuilder().normalize(true).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}
}
