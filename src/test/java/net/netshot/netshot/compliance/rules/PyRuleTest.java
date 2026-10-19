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
import java.util.concurrent.TimeUnit;

import org.hibernate.Session;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.check.JsRuleRuntime;
import net.netshot.netshot.compliance.check.PyRuleRuntime;
import net.netshot.netshot.compliance.check.RuleEvaluators;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;
import net.netshot.netshot.testing.fake.FakeTaskContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@ScriptEngineTest
@DisplayName("Python Rule")
public class PyRuleTest {

	static PyRuleRuntime pyRuntime;
	static RuleEvaluators evaluators;

	@BeforeAll
	static void initNetshot() throws Exception {
		pyRuntime = new PyRuleRuntime();
		evaluators = RuleEvaluators.standard(new JsRuleRuntime(), pyRuntime, TestDrivers.registry());
	}

	Policy policy = new Policy("Fake policy", null);
	Device device = FakeDeviceFactory.getFakeCiscoIosDevice();
	FakeTaskContext taskContext = new FakeTaskContext();
	Session nullSession;
	PythonRule rule;

	PyRuleTest() {
		this.rule = new PythonRule("Testing rule", policy);
		rule.setEnabled(true);
		rule.setScript(
			"import re" + "\n"
				+ "" + "\n"
				+ "def check(device):" + "\n"
				+ "  type = device.get('type')" + "\n"
				+ "  if type != 'Cisco IOS and IOS-XE':" + "\n"
				+ "    return {" + "\n"
				+ "      'result': result_option.NOTAPPLICABLE," + "\n"
				+ "      'comment': 'Type is {}'.format(type)" + "\n"
				+ "    }" + "\n"
				+ "  name = device.get('name')" + "\n"
				+ "  if re.search(r'router00100', name):" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "\n"
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
	@DisplayName("Name-based Python rule")
	void nameRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  if device.get('name') == 'router00100':" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Location-based Python rule")
	void locationRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  if device.get('location') == 'Test Location':" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Contact-based Python rule")
	void contactRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  if device.get('contact') == 'Test Contact':" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Family-based Python rule")
	void familyRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  if device.get('family') == 'Unknown IOS device':" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Domain-based Python rule")
	void domainRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  if device.get('management_domain') == 'Test domain':" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Management IP-based Python rule")
	void managementIpRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  if device.get('management_ip_address').startswith('172.16.'):" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Class-based Python rule")
	void classRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  if device.get('network_class') == 'ROUTER':" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Software version-based Python rule")
	void softwareVersionRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  if device.get('software_version') == '16.1.6':" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Serial-based Python rule")
	void serialRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  if device.get('serial_number') == '16161616TEST16':" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Comments-based Python rule")
	void commentRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  if 'testing' in device.get('comments'):" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("VRF-based Python rule")
	void vrfRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  vrfs = device.get('vrfs')" + "\n"
				+ "  if 'VRF1' in vrfs:" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Tag-based Python rule, with tag and comment changes")
	void tagRule() {
		device.addTag("core");
		device.addTag("legacy");
		rule.setScript(
			"def check(device):" + "\n"
				+ "  tags = device.get('tags')" + "\n"
				+ "  if 'core' not in tags:" + "\n"
				+ "    return result_option.NONCONFORMING" + "\n"
				+ "  device.remove('tag', 'legacy')" + "\n"
				+ "  device.add('tag', 'checked')" + "\n"
				+ "  device.set('comments', 'Checked by rule')" + "\n"
				+ "  return result_option.CONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
		Assertions.assertEquals(Set.of("core", "checked"), device.getTags(), "Unexpected device tags");
		Assertions.assertEquals("Checked by rule", device.getComments(), "Unexpected device comments");
	}

	@Test
	@DisplayName("Module-based Python rule")
	void moduleRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  modules = device.get('modules')" + "\n"
				+ "  if modules[1]['serial_number'] == '29038POSD203':" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Interface-based Python rule")
	void interfaceRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  interfaces = device.get('interfaces')" + "\n"
				+ "  if interfaces[1]['name'] == 'GigabitEthernet0/1' and interfaces[1]['ip'][0]['ip'] == '10.0.100.129':" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Running config-based Python rule")
	void runningConfigRule() {
		rule.setScript(
			"import re" + "\n"
				+ "def check(device):" + "\n"
				+ "  running_config = device.get('running_config')" + "\n"
				+ "  if re.search(r'^enable secret .+', running_config, re.MULTILINE):" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Diagnostic result-based Python rule")
	void diagnosticResultRule() {
		rule.setScript(
			"import re" + "\n"
				+ "def check(device):" + "\n"
				+ "  reload_reason = device.get('Reload reason')" + "\n"
				+ "  if reload_reason == 'Reload Command':" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Python rule with debug")
	void debugRule() {
		rule.setScript(
			"import re" + "\n"
				+ "def check(device):" + "\n"
				+ "  debug('Debug message')" + "\n"
				+ "  return result_option.NONCONFORMING" + "\n"
				+ "" + "\n"
		);
		evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertTrue(taskContext.getLog().contains("[DEBUG] Debug message\n"),
			"Debug message is not correct");
	}

	@Test
	@DisplayName("Python rule with direct DNS resolution")
	void directNsRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  record = device.nslookup('dns.google')" + "\n"
				+ "  return {" + "\n"
				+ "    'result': result_option.CONFORMING," + "\n"
				+ "    'comment': record['address']" + "\n"
				+ "  }" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		String comment = result.getComment();
		Assertions.assertTrue("8.8.8.8".equals(comment) || "8.8.4.4".equals(comment),
			"The resolved IP is not 8.8.8.8 nor 8.8.4.4");
	}

	@Test
	@DisplayName("Python rule with reverse DNS resolution")
	void reverseNsRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  record = device.nslookup('8.8.8.8')" + "\n"
				+ "  return {" + "\n"
				+ "    'result': result_option.CONFORMING," + "\n"
				+ "    'comment': record['name']" + "\n"
				+ "  }" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(result.getComment(), "dns.google",
			"The resolved name is not dns.google");
	}

	@Test
	@DisplayName("Endless Python rule")
	void endlessRule() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  while True:" + "\n"
				+ "    pass" + "\n"
				+ "  return result_option.CONFORMING" + "\n"
				+ "" + "\n"
		);
		// Set max execution time
		Properties config = new Properties();
		config.setProperty("netshot.python.maxexecutiontime", "5000");
		TestConfig.init(config);
		pyRuntime.loadConfig();
		// Check
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.INVALIDRULE, result.getResult(),
			"The result is not INVALIDRULE");
		Assertions.assertTrue(taskContext.getLog().contains("rule took too long"),
			"Error message is not correct");
	}

	@Test
	@DisplayName("Endless Python rule, in top-level code")
	void endlessTopLevelRule() {
		rule.setScript(
			"while True:" + "\n"
				+ "  pass" + "\n"
				+ "" + "\n"
				+ "def check(device):" + "\n"
				+ "  return result_option.CONFORMING" + "\n"
		);
		// Set max execution time
		Properties config = new Properties();
		config.setProperty("netshot.python.maxexecutiontime", "5000");
		TestConfig.init(config);
		pyRuntime.loadConfig();
		// Check
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.INVALIDRULE, result.getResult(),
			"The result is not INVALIDRULE");
		Assertions.assertTrue(taskContext.getLog().contains("rule took too long"),
			"Error message is not correct");
	}

	@Test
	@DisplayName("Python rule importing the threading module")
	@Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
	void threadingImportRule() {
		rule.setScript(
			"import threading" + "\n"
				+ "def check(device):" + "\n"
				+ "  return result_option.CONFORMING" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The result is not CONFORMING");
	}

	@Test
	@DisplayName("Endless Python rule, in exit handler")
	@Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
	void endlessExitHandlerRule() {
		rule.setScript(
			"import atexit" + "\n"
				+ "def forever():" + "\n"
				+ "  while True:" + "\n"
				+ "    pass" + "\n"
				+ "atexit.register(forever)" + "\n"
				+ "" + "\n"
				+ "def check(device):" + "\n"
				+ "  return result_option.CONFORMING" + "\n"
		);
		// Set max execution time
		Properties config = new Properties();
		config.setProperty("netshot.python.maxexecutiontime", "5000");
		TestConfig.init(config);
		pyRuntime.loadConfig();
		// Check
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.INVALIDRULE, result.getResult(),
			"The result is not INVALIDRULE");
		Assertions.assertTrue(taskContext.getLog().contains("rule took too long"),
			"Error message is not correct");
	}

	@Test
	@DisplayName("Python rule can't read the process environment from /proc")
	void procSelfAccessRule() {
		rule.setScript(
			"def check(device):" + "\n"
				+ "  for path in ['/proc/self/environ', '/proc/self/cmdline', '/proc/thread-self/environ']:" + "\n"
				+ "    try:" + "\n"
				+ "      with open(path, 'rb') as f:" + "\n"
				+ "        f.read()" + "\n"
				+ "      return { 'result': result_option.NONCONFORMING, 'comment': path }" + "\n"
				+ "    except Exception:" + "\n"
				+ "      pass" + "\n"
				+ "  return result_option.CONFORMING" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The Python rule could read " + result.getComment());
	}

	@Test
	@DisplayName("Python rule using netshot.toolbox.ConfigTree")
	void toolboxConfigTreeRule() {
		rule.setScript(
			"from netshot.toolbox import ConfigTree" + "\n"
				+ "\n"
				+ "def check(device):" + "\n"
				+ "  config = ConfigTree.parse(device.get('running_config'))" + "\n"
				+ "  down = config.find_without_child(r'^interface (\\S+)', r'^no shutdown$')" + "\n"
				+ "  if config.find_value(r'^hostname (\\S+)') != 'router00100':" + "\n"
				+ "    return result_option.NOTAPPLICABLE" + "\n"
				+ "  return {" + "\n"
				+ "    'result': result_option.NONCONFORMING if down else result_option.CONFORMING," + "\n"
				+ "    'comment': ','.join(i.match.group(1) for i in down)," + "\n"
				+ "  }" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.getResult(),
			"The result is not NONCONFORMING");
		Assertions.assertEquals("GigabitEthernet0/2", result.getComment(), "The comment is not correct");
	}

	@Test
	@DisplayName("Python rule using the ciscoconfparse-compatible parser")
	void ciscoConfParseRule() {
		rule.setScript(
			"from netshot.toolbox import CiscoConfParse" + "\n"
				+ "\n"
				+ "def check(device):" + "\n"
				+ "  parse = CiscoConfParse(device.get('running_config').splitlines())" + "\n"
				+ "  vty = parse.find_objects_w_child(r'^line vty', r'transport input all')" + "\n"
				+ "  if vty:" + "\n"
				+ "    return {'result': result_option.NONCONFORMING, 'comment': vty[0].text}" + "\n"
				+ "  return result_option.CONFORMING" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.NONCONFORMING, result.getResult(),
			"The result is not NONCONFORMING");
		Assertions.assertEquals("line vty 0 4", result.getComment(), "The comment is not correct");
	}

	@Test
	@DisplayName("Python rule using standard library modules")
	void standardLibraryRule() {
		rule.setScript(
			"import json, ipaddress, collections, datetime" + "\n"
				+ "def check(device):" + "\n"
				+ "  net = ipaddress.ip_network('10.0.0.0/24')" + "\n"
				+ "  data = json.loads(json.dumps({ 'name': device.get('name'), 'hosts': net.num_addresses }))" + "\n"
				+ "  if data == { 'name': 'router00100', 'hosts': 256 }:" + "\n"
				+ "    return result_option.CONFORMING" + "\n"
				+ "  return { 'result': result_option.NONCONFORMING, 'comment': str(data) }" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The Python rule couldn't use the standard library: " + taskContext.getLog());
	}

	@Test
	@DisplayName("Python rule can't modify the Python standard library")
	void standardLibraryWriteRule() {
		rule.setScript(
			"import os, json" + "\n"
				+ "def check(device):" + "\n"
				+ "  lib = os.path.dirname(json.__file__)" + "\n"
				+ "  attempts = {" + "\n"
				+ "    'create': lambda: open(os.path.join(lib, 'netshot_probe.py'), 'w').write('x = 1')," + "\n"
				+ "    'append': lambda: open(json.__file__, 'a').write('')," + "\n"
				+ "    'mkdir': lambda: os.mkdir(os.path.join(lib, 'netshot_probe'))," + "\n"
				+ "    'remove': lambda: os.remove(json.__file__)," + "\n"
				+ "  }" + "\n"
				+ "  for name, attempt in attempts.items():" + "\n"
				+ "    try:" + "\n"
				+ "      attempt()" + "\n"
				+ "      return { 'result': result_option.NONCONFORMING, 'comment': name }" + "\n"
				+ "    except Exception:" + "\n"
				+ "      pass" + "\n"
				+ "  return result_option.CONFORMING" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The Python rule could modify the standard library (" + result.getComment() + "): " + taskContext.getLog());
	}

	@Test
	@DisplayName("Python rule can't read the process environment variables")
	void environmentAccessRule() {
		Assertions.assertNotNull(System.getenv("PATH"), "PATH should be set in the test environment");
		rule.setScript(
			"import os, sys" + "\n"
				+ "def check(device):" + "\n"
				+ "  if 'PATH' in os.environ:" + "\n"
				+ "    return { 'result': result_option.NONCONFORMING, 'comment': 'os.environ' }" + "\n"
				+ "  del sys.modules['posix']" + "\n"
				+ "  import posix" + "\n"
				+ "  if b'PATH' in posix.environ or 'PATH' in posix.environ:" + "\n"
				+ "    return { 'result': result_option.NONCONFORMING, 'comment': 'posix.environ' }" + "\n"
				+ "  return result_option.CONFORMING" + "\n"
		);
		CheckResult result = evaluators.evaluate(rule, device, nullSession, taskContext);
		Assertions.assertEquals(CheckResult.ResultOption.CONFORMING, result.getResult(),
			"The Python rule could read the environment through " + result.getComment());
	}
}
