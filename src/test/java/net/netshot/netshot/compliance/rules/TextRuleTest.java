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
package net.netshot.netshot.compliance.rules;

import java.util.Properties;

import org.hibernate.Session;

import net.netshot.netshot.FakeDeviceFactory;
import net.netshot.netshot.FakeTaskContext;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.TestDrivers;
import net.netshot.netshot.access.Ssh;
import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.check.JsRuleRuntime;
import net.netshot.netshot.compliance.check.PyRuleRuntime;
import net.netshot.netshot.compliance.check.RuleEvaluators;
import net.netshot.netshot.devicescript.helper.PythonFileSystem;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.engine.TaskContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Simple Text Rule")
public class TextRuleTest {

	static RuleEvaluators evaluators;

	@BeforeAll
	static void initNetshot() throws Exception {
		TestConfig.init(new Properties());
		Ssh.loadConfig();
		PythonFileSystem.loadConfig();
		evaluators = RuleEvaluators.standard(new JsRuleRuntime(), new PyRuleRuntime(), TestDrivers.registry());
		TestDrivers.reload();
	}

	Policy policy = new Policy("Fake policy", null);
	Device device = FakeDeviceFactory.getFakeCiscoIosDevice();
	TaskContext taskContext = new FakeTaskContext();
	Session nullSession;
	TextRule rule;

	TextRuleTest() {
		this.rule = new TextRule("Testing rule", policy);
		rule.setEnabled(true);
		rule.setDeviceDriver("CiscoIOS12");
		rule.setField("runningConfig");
		rule.setRegExp(false);
		rule.setContext(null);
		rule.setAnyBlock(false);
		rule.setMatchAll(false);
		rule.setNormalize(false);
	}

	@Test
	@DisplayName("Conforming rule")
	void matchAll() {
		rule.setText("no service pad");
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Non-conforming rule")
	void matchAllFail() {
		rule.setText("no service mlqksd");
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.getResult(),
			"The result is not NONCONFORMING");
	}

	@Test
	@DisplayName("Disabled rule")
	void disabled() {
		rule.setEnabled(false);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.DISABLED, result.getResult(),
			"The result is not DISABLED");
	}

	@Test
	@DisplayName("Unknown field rule")
	void unknownField() {
		rule.setField("doesNotExist");
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.INVALIDRULE, result.getResult(),
			"The result is not INVALIDRULE");
	}

	@Test
	@DisplayName("Conforming rule with context, all blocks")
	void contextAllBlocks() {
		rule.setContext("^interface .*");
		rule.setText(" description ");
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Non-conforming rule with context, all blocks")
	void contextAllBlocksFail() {
		rule.setContext("^interface .*");
		rule.setText(" no shutdown");
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.getResult(),
			"The result is not NONCONFORMING");
	}

	@Test
	@DisplayName("Conforming rule with context, any block")
	void contextAnyBlock() {
		rule.setContext("^interface .*");
		rule.setText(" no shutdown");
		rule.setAnyBlock(true);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Non-conforming rule with context, any block")
	void contextAnyBlockFail() {
		rule.setContext("^interface .*");
		rule.setText(" no description");
		rule.setAnyBlock(true);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.getResult(),
			"The result is not NONCONFORMING");
	}

	@Test
	@DisplayName("Conforming rule with context, any block, match all")
	void contextAnyBlockMatchAll() {
		rule.setContext("^line .*");
		rule.setText(" password something");
		rule.setAnyBlock(true);
		rule.setMatchAll(true);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Multiline Regexp")
	void multilineRegexp() {
		rule.setText("(?m)^hostname router[0-9]+$");
		rule.setRegExp(true);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Regexp fail")
	void regexpFail() {
		rule.setText("^hostname router[0-9]+");
		rule.setRegExp(true);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.getResult(),
			"The result is not NONCONFORMING");
	}

	@Test
	@DisplayName("Invalid Regexp")
	void invalidRegexp() {
		rule.setText("^(h");
		rule.setRegExp(true);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.INVALIDRULE, result.getResult(),
			"The result is not INVALIDRULE");
	}

	@Test
	@DisplayName("Without normalization")
	void withoutNormalization() {
		rule.setText("(?m)^ipv6 unicast-routing$");
		rule.setRegExp(true);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.getResult(),
			"The result is not NONCONFORMING");
	}

	@Test
	@DisplayName("With normalization")
	void withNormalization() {
		rule.setText("(?m)^ipv6 unicast-routing$");
		rule.setRegExp(true);
		rule.setNormalize(true);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}
}
