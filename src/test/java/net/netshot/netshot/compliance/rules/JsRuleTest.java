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
import java.util.Set;

import org.hibernate.Session;

import net.netshot.netshot.FakeDeviceFactory;
import net.netshot.netshot.FakeTaskContext;
import net.netshot.netshot.Netshot;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JavaScript Rule")
public class JsRuleTest {

	static JsRuleRuntime jsRuntime;
	static RuleEvaluators evaluators;

	@BeforeAll
	static void initNetshot() throws Exception {
		TestConfig.init(new Properties());
		Ssh.loadConfig();
		PythonFileSystem.loadConfig();
		jsRuntime = new JsRuleRuntime();
		evaluators = RuleEvaluators.standard(jsRuntime, new PyRuleRuntime(), TestDrivers.registry());
		TestDrivers.reload();
	}

	Policy policy = new Policy("Fake policy", null);
	Device device = FakeDeviceFactory.getFakeCiscoIosDevice();
	FakeTaskContext taskContext = new FakeTaskContext();
	Session nullSession;
	JavaScriptRule rule;

	JsRuleTest() {
		this.rule = new JavaScriptRule("Testing rule", policy);
		rule.setEnabled(true);
		rule.setScript(
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
		);
	}

	@Test
	@DisplayName("Conforming rule")
	void conformingRule() {
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Not applicable rule")
	void notApplicableRule() {
		device.setDriver("None");
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NOTAPPLICABLE, result.getResult(),
			"The result is not NOTAPPLICABLE");
	}

	@Test
	@DisplayName("Non conforming rule")
	void nonConformingRule() {
		device.setName("router2");
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.getResult(),
			"The result is not NONCONFORMING");
	}

	@Test
	@DisplayName("Name-based JS rule")
	void nameRule() {
		rule.setScript(
			"function check(device) {"
				+ "  if (device.get('name') === 'router00100') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Location-based JS rule")
	void locationRule() {
		rule.setScript(
			"function check(device) {"
				+ "  if (device.get('location') === 'Test Location') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Contact-based JS rule")
	void contactRule() {
		rule.setScript(
			"function check(device) {"
				+ "  if (device.get('contact') === 'Test Contact') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Family-based JS rule")
	void familyRule() {
		rule.setScript(
			"function check(device) {"
				+ "  if (device.get('family') === 'Unknown IOS device') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Domain-based JS rule")
	void domainRule() {
		rule.setScript(
			"function check(device) {"
				+ "  if (device.get('managementDomain') === 'Test domain') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Management IP-based JS rule")
	void managementIpRule() {
		rule.setScript(
			"function check(device) {"
				+ "  if (device.get('managementIpAddress').startsWith('172.16.')) {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Class-based JS rule")
	void classRule() {
		rule.setScript(
			"function check(device) {"
				+ "  if (device.get('networkClass') === 'ROUTER') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Software version-based JS rule")
	void softwareVersionRule() {
		rule.setScript(
			"function check(device) {"
				+ "  if (device.get('softwareVersion') === '16.1.6') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Serial-based JS rule")
	void serialRule() {
		rule.setScript(
			"function check(device) {"
				+ "  if (device.get('serialNumber') === '16161616TEST16') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Comments-based JS rule")
	void commentRule() {
		rule.setScript(
			"function check(device) {"
				+ "  if (device.get('comments').match(/testing/)) {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("VRF-based JS rule")
	void vrfRule() {
		rule.setScript(
			"function check(device) {"
				+ "  const vrfs = device.get('vrfs');"
				+ "  if (vrfs.includes('VRF1')) {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Tag-based JS rule, with tag and comment changes")
	void tagRule() {
		device.addTag("core");
		device.addTag("legacy");
		rule.setScript(
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
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
		Assertions.assertEquals(Set.of("core", "checked"), device.getTags(), "Unexpected device tags");
		Assertions.assertEquals("Checked by rule", device.getComments(), "Unexpected device comments");
	}

	@Test
	@DisplayName("JS rule can't set other device fields")
	void readOnlyDeviceRule() {
		String originalName = device.getName();
		rule.setScript(
			"function check(device) {"
				+ "  device.set('name', 'hacked');"
				+ "  return CONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.INVALIDRULE, result.getResult(),
			"The result is not INVALIDRULE");
		Assertions.assertEquals(originalName, device.getName(), "The device name was changed");
	}

	@Test
	@DisplayName("Module-based JS rule")
	void moduleRule() {
		rule.setScript(
			"function check(device) {"
				+ "  const modules = device.get('modules');"
				+ "  if (modules[1].serialNumber === '29038POSD203') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Interface-based JS rule")
	void interfaceRule() {
		rule.setScript(
			"function check(device) {"
				+ "  const interfaces = device.get('interfaces');"
				+ "  if (interfaces[1].name === 'GigabitEthernet0/1' && interfaces[1].ip[0].ip === '10.0.100.129') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Running config-based JS rule")
	void runningConfigRule() {
		rule.setScript(
			"function check(device) {"
				+ "  const runningConfig = device.get('runningConfig');"
				+ "  if (runningConfig.match(/^enable secret .+/m)) {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Diagnostic result-based JS rule")
	void diagnosticResultRule() {
		rule.setScript(
			"function check(device) {"
				+ "  const reloadReason = device.get('Reload reason');"
				+ "  if (reloadReason === 'Reload Command') {"
				+ "    return CONFORMING;"
				+ "  }"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("JS rule with debug")
	void debugRule() {
		rule.setScript(
			"function check(device, debug) {"
				+ "  debug('Debug message');"
				+ "  return NONCONFORMING;"
				+ "}"
		);
		evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertTrue(taskContext.getLog().contains("[DEBUG] Debug message\n"),
			"Debug message is not correct");
	}

	@Test
	@DisplayName("JS rule with direct DNS resolution")
	void directNsRule() {
		rule.setScript(
			"function check(device) {"
				+ "  const record = device.nslookup('dns.google');"
				+ "  return { result: CONFORMING, comment: record.address };"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		String comment = result.getComment();
		Assertions.assertTrue("8.8.8.8".equals(comment) || "8.8.4.4".equals(comment),
			"The resolved IP is not 8.8.8.8 nor 8.8.4.4");
	}

	@Test
	@DisplayName("JS rule with reverse DNS resolution")
	void reverseNsRule() {
		rule.setScript(
			"function check(device) {"
				+ "  const record = device.nslookup('8.8.8.8');"
				+ "  return { result: CONFORMING, comment: record.name };"
				+ "}"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(result.getComment(), "dns.google",
			"The resolved name is not dns.google");
	}

	@Test
	@DisplayName("Endless JS rule")
	void endlessRule() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		rule.setScript(
			"function check(device) {"
				+ " while (true) {}"
				+ " return CONFORMING;"
				+ "}"
		);
		// Set max execution time
		Properties config = new Properties();
		config.setProperty("netshot.javascript.maxexecutiontime", "5000");
		TestConfig.init(config);
		jsRuntime.loadConfig();
		// Check
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.INVALIDRULE, result.getResult(),
			"The result is not INVALIDRULE");
		Assertions.assertTrue(taskContext.getLog().contains("rule took too long"),
			"Error message is not correct");
	}

	@Test
	@DisplayName("Endless JS rule, in top-level code")
	void endlessTopLevelRule() {
		rule.setScript(
			"while (true) {}"
				+ "function check(device) {"
				+ " return CONFORMING;"
				+ "}"
		);
		// Set max execution time
		Properties config = new Properties();
		config.setProperty("netshot.javascript.maxexecutiontime", "5000");
		TestConfig.init(config);
		jsRuntime.loadConfig();
		// Check
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.INVALIDRULE, result.getResult(),
			"The result is not INVALIDRULE");
		Assertions.assertTrue(taskContext.getLog().contains("rule took too long"),
			"Error message is not correct");
	}

	@Test
	@DisplayName("JS rule using Toolbox.ConfigTree")
	void toolboxConfigTreeRule() {
		rule.setScript(
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
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.getResult(),
			"The result is not NONCONFORMING");
		Assertions.assertEquals("GigabitEthernet0/2", result.getComment(), "The comment is not correct");
	}

	@Test
	@DisplayName("JS rule using device.findSections, with its own Toolbox declaration")
	void findSectionsRule() {
		rule.setScript(
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
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}
}
