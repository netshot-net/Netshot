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
package net.netshot.netshot.compliance;

import java.util.Properties;
import java.util.Set;


import net.netshot.netshot.Netshot;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.compliance.check.JsRuleRuntime;
import net.netshot.netshot.compliance.check.PyRuleRuntime;
import net.netshot.netshot.compliance.check.RuleEvaluators;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;
import net.netshot.netshot.testing.fake.FakeTaskContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ScriptEngineTest
@DisplayName("JavaScript Rule")
public class JsRuleTest {

	static JsRuleRuntime jsRuntime;
	static RuleEvaluators evaluators;

	/** The configuration before the tests, which some tests change (max execution time). */
	static Properties originalConfig;

	@BeforeAll
	static void initNetshot() throws Exception {
		originalConfig = TestConfig.properties();
		jsRuntime = new JsRuleRuntime(Netshot.config());
		evaluators = RuleEvaluators.standard(jsRuntime, new PyRuleRuntime(Netshot.config()), TestDrivers.registry(), null);
	}

	@AfterEach
	void restoreConfig() {
		TestConfig.init(originalConfig);
	}

	Policy policy = new Policy(0, 0, null, "Fake policy", null);
	DeviceDraft device = FakeDeviceFactory.getFakeCiscoIosDraft();
	FakeTaskContext taskContext = new FakeTaskContext();
	JavaScriptRule rule;

	JsRuleTest() {
		this.rule = JavaScriptRule.of("Testing rule", policy.id());
		rule = rule.toBuilder().enabled(true).build();
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  const type = device.get('type');"
				+ "  if (type !== 'Cisco IOS and IOS-XE') {"
				+ "    return {"
				+ "      result: NOTAPPLICABLE,"
				+ "      comment: 'Type is ' + type,"
				+ "     };"
				+ "  }"
				+ "  const name = device.get('name');"
				+ "  if (name.match(/router00100/)) {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
	}

	@Test
	@DisplayName("Conforming rule")
	void conformingRule() {
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Not applicable rule")
	void notApplicableRule() {
		device = new DeviceDraft(device.getOriginal().toBuilder().driver("None").build());
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NOTAPPLICABLE, result.result(),
			"The result is not NOTAPPLICABLE");
	}

	@Test
	@DisplayName("Non conforming rule")
	void nonConformingRule() {
		device.setName("router2");
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.result(),
			"The result is not NONCONFORMING");
	}

	@Test
	@DisplayName("Name-based JS rule")
	void nameRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  if (device.get('name') === 'router00100') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Location-based JS rule")
	void locationRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  if (device.get('location') === 'Test Location') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Contact-based JS rule")
	void contactRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  if (device.get('contact') === 'Test Contact') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Family-based JS rule")
	void familyRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  if (device.get('family') === 'Unknown IOS device') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Domain-based JS rule")
	void domainRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  if (device.get('managementDomain') === 'Test domain') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Management IP-based JS rule")
	void managementIpRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  if (device.get('managementIpAddress').startsWith('172.16.')) {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Class-based JS rule")
	void classRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  if (device.get('networkClass') === 'ROUTER') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Software version-based JS rule")
	void softwareVersionRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  if (device.get('softwareVersion') === '16.1.6') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Serial-based JS rule")
	void serialRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  if (device.get('serialNumber') === '16161616TEST16') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Comments-based JS rule")
	void commentRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  if (device.get('comments').match(/testing/)) {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("VRF-based JS rule")
	void vrfRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  const vrfs = device.get('vrfs');"
				+ "  if (vrfs.includes('VRF1')) {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Tag-based JS rule, with tag and comment changes")
	void tagRule() {
		device.addTag("core");
		device.addTag("legacy");
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  const tags = device.get('tags');"
				+ "  if (!tags.includes('core')) {"
				+ "    return NONCONFORMING;"
				+ "  }"
				+ "  device.remove('tag', 'legacy');"
				+ "  device.add('tag', 'checked');"
				+ "  device.add('tag', '   ');"
				+ "  device.set('comments', 'Checked by rule');"
				+ "  return CONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
		Assertions.assertEquals(Set.of("core", "checked"), device.getTags(), "Unexpected device tags");
		Assertions.assertEquals("Checked by rule", device.getComments(), "Unexpected device comments");
	}

	@Test
	@DisplayName("JS rule sets comments up to 4096 characters, longer ones are a script error")
	void commentsLength() {
		String original = device.getComments();
		rule = rule.toBuilder().script("function check(device) { device.set('comments', 'x'.repeat(4097)); return CONFORMING; }").build();
		Assertions.assertEquals(CheckResult.ResultOption.INVALIDRULE, evaluators.evaluate(rule, device, false, taskContext).result());
		Assertions.assertEquals(original, device.getComments(), "The device comments were changed");
		rule = rule.toBuilder().script("function check(device) { device.set('comments', 'x'.repeat(4096)); return CONFORMING; }").build();
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, evaluators.evaluate(rule, device, false, taskContext).result());
		Assertions.assertEquals(4096, device.getComments().length());
	}

	@Test
	@DisplayName("JS rule can't set other device fields")
	void readOnlyDeviceRule() {
		String originalName = device.getName();
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  device.set('name', 'hacked');"
				+ "  return CONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.INVALIDRULE, result.result(),
			"The result is not INVALIDRULE");
		Assertions.assertEquals(originalName, device.getName(), "The device name was changed");
	}

	@Test
	@DisplayName("Module-based JS rule")
	void moduleRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  const modules = device.get('modules');"
				+ "  if (modules[1].serialNumber === '29038POSD203') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Interface-based JS rule")
	void interfaceRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  const interfaces = device.get('interfaces');"
				+ "  if (interfaces[1].name === 'GigabitEthernet0/1' && interfaces[1].ip[0].ip === '10.0.100.129') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Running config-based JS rule")
	void runningConfigRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  const runningConfig = device.get('runningConfig');"
				+ "  if (runningConfig.match(/^enable secret .+/m)) {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Diagnostic result-based JS rule")
	void diagnosticResultRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  const reloadReason = device.get('Reload reason');"
				+ "  if (reloadReason === 'Reload Command') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("JS rule with debug")
	void debugRule() {
		rule = rule.toBuilder().script(
			"function check(device, debug) {"
				+ "  debug('Debug message');"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertTrue(taskContext.getLog().contains("[DEBUG] Debug message\n"),
			"Debug message is not correct");
	}

	@Test
	@DisplayName("JS rule with direct DNS resolution")
	void directNsRule() {
		// Resolved locally (hosts file): no dependency on the network
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  const record = device.nslookup('localhost');"
				+ "  return { result: CONFORMING, comment: record.address };"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals("127.0.0.1", result.comment(), "The resolved IP is not 127.0.0.1");
	}

	@Test
	@DisplayName("JS rule with reverse DNS resolution")
	void reverseNsRule() {
		// The loopback address, resolved locally (hosts file): no dependency on the network
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  const record = device.nslookup('127.0.0.1');"
				+ "  return { result: CONFORMING, comment: record.name };"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals("localhost", result.comment(), "The resolved name is not localhost");
	}

	@Test
	@DisplayName("Endless JS rule")
	void endlessRule() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ " while (true) {}"
				+ " return CONFORMING;"
				+ "}"
		).build();
		// Set max execution time
		Properties config = new Properties();
		config.setProperty("netshot.javascript.maxexecutiontime", "5000");
		TestConfig.init(config);
		// Check
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.INVALIDRULE, result.result(),
			"The result is not INVALIDRULE");
		Assertions.assertTrue(taskContext.getLog().contains("rule took too long"),
			"Error message is not correct");
	}

	@Test
	@DisplayName("Endless JS rule, in top-level code")
	void endlessTopLevelRule() {
		rule = rule.toBuilder().script(
			"while (true) {}"
				+ "function check(device) {"
				+ " return CONFORMING;"
				+ "}"
		).build();
		// Set max execution time
		Properties config = new Properties();
		config.setProperty("netshot.javascript.maxexecutiontime", "5000");
		TestConfig.init(config);
		// Check
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.INVALIDRULE, result.result(),
			"The result is not INVALIDRULE");
		Assertions.assertTrue(taskContext.getLog().contains("rule took too long"),
			"Error message is not correct");
	}

	@Test
	@DisplayName("JS rule using Toolbox.ConfigTree")
	void toolboxConfigTreeRule() {
		rule = rule.toBuilder().script(
			"function check(device) {"
				+ "  const config = Toolbox.ConfigTree.parse(device.get('runningConfig'));"
				+ "  const down = config.findWithoutChild(/^interface (\\S+)/, /^no shutdown$/);"
				+ "  if (config.findValue(/^hostname (\\S+)/) !== 'router00100') {"
				+ "    return NOTAPPLICABLE;"
				+ "  }"
				+ "  return {"
				+ "    result: down.length > 0 ? NONCONFORMING : CONFORMING,"
				+ "    comment: down.map((i) => i.match[1]).join(','),"
				+ "  };"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.result(),
			"The result is not NONCONFORMING");
		Assertions.assertEquals("GigabitEthernet0/2", result.comment(), "The comment is not correct");
	}

	@Test
	@DisplayName("JS rule using device.findSections, with its own Toolbox declaration")
	void findSectionsRule() {
		rule = rule.toBuilder().script(
			"const Toolbox = 'user value';"
				+ "function check(device) {"
				+ "  const sections = device.findSections(device.get('runningConfig'), /^interface (\\S+)/m);"
				+ "  if (Toolbox === 'user value' && sections.length === 3"
				+ "      && sections[2].match[1] === 'GigabitEthernet0/2'"
				+ "      && sections[2].config === ' description Description of Gi0/2\\n ip address 10.0.2.1 255.255.255.0') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		).build();
		CheckResult result = evaluators.evaluate(rule, device, false, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.result(),
			"The result is not CONFORMING");
	}
}
