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

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.compliance.check.JsRuleRuntime;
import net.netshot.netshot.compliance.check.PyRuleRuntime;
import net.netshot.netshot.compliance.check.RuleEvaluators;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.inventory.compliance.CheckResult.ResultOption;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;
import net.netshot.netshot.testing.fake.FakeTaskContext;

/**
 * What a Python compliance rule can read and change through its device: the other (stored) devices, and the
 * read-only device, even when the rule bypasses the Python wrapper and calls the device helper directly.
 */
@DatabaseTest
@DisplayName("Python rule access to the devices (DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
class PyRuleDeviceAccessTest {

	RuleEvaluators evaluators;
	Domain domain;
	/** The device the rules run on. */
	Device current;
	/** Another device, with a configuration. */
	Device other;
	/** A device without configuration. */
	Device unconfigured;
	DeviceDraft draft;
	FakeTaskContext taskContext;

	@BeforeAll
	void createData() {
		this.evaluators = RuleEvaluators.standard(new JsRuleRuntime(TestConfig.store()), new PyRuleRuntime(TestConfig.store()),
			TestDrivers.registry(), TestWiring.get().core().repositories().deviceReads());
		this.domain = TestDevices.addDomain("Test domain", "Fake domain for tests");
		this.current = TestDevices.addFakeCiscoIos(this.domain, 1);
		this.other = TestDevices.addFakeCiscoIos(this.domain, 2);
		this.unconfigured = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(this.domain, null, 3));
	}

	@AfterAll
	void cleanUpData() {
		TestDevices.deleteAll();
	}

	@BeforeEach
	void newDraft() {
		this.draft = new DeviceDraft(TestDevices.find(this.current.id()));
		this.taskContext = new FakeTaskContext();
	}

	/**
	 * Runs a rule on the current device.
	 *
	 * @param body the body of the check function (indented by two spaces)
	 * @return the result
	 */
	private CheckResult check(String body) {
		PythonRule rule = PythonRule.of("Device access rule", 0).toBuilder().enabled(true)
			.script("def check(device):\n" + body.indent(2)).build();
		return this.evaluators.evaluate(rule, this.draft, false, this.taskContext);
	}

	private void assertConforming(CheckResult result) {
		Assertions.assertEquals(ResultOption.CONFORMING, result.result(), this.taskContext.getLog());
	}

	@Test
	@DisplayName("Reads the items of another device by ID, including its last configuration")
	void readsOtherDeviceById() {
		CheckResult result = this.check("""
			other = %d
			config = device.get('running_config', other)
			interfaces = device.get('interfaces', other)
			values = [device.get('name', other), device.get('serial_number', other), str(len(interfaces)),
				str('hostname router00002' in config), device.get('ios_version', other)]
			return {'result': result_option.CONFORMING, 'comment': '|'.join(values)}
			""".formatted(this.other.id()));

		assertConforming(result);
		Assertions.assertEquals("router00002|16161616TEST16|2|True|16.1.6", result.comment());
	}

	@Test
	@DisplayName("Reads another device by name, and the current one by its own ID or name")
	void readsDeviceByName() {
		CheckResult result = this.check("""
			values = [device.get('location', 'router00002'), device.get('name', %d), device.get('family', 'router00001')]
			return {'result': result_option.CONFORMING, 'comment': '|'.join(values)}
			""".formatted(this.current.id()));

		assertConforming(result);
		Assertions.assertEquals("Test Location|router00001|Unknown IOS device", result.comment());
	}

	@Test
	@DisplayName("Gets nothing for an unknown device or a device without configuration")
	void unknownDevices() {
		CheckResult result = this.check("""
			values = [device.get('name', %d), device.get('name', %d), device.get('name', 'nosuchdevice')]
			return {'result': result_option.CONFORMING, 'comment': str(values)}
			""".formatted(this.unconfigured.id(), this.other.id() + 1000));

		assertConforming(result);
		Assertions.assertEquals("[None, None, None]", result.comment());
		Assertions.assertTrue(this.taskContext.getLog().contains("Unable to get data name for device named nosuchdevice"),
			this.taskContext.getLog());
	}

	@Test
	@DisplayName("The Python device only allows changing the comments and tags")
	void wrapperRefusesChanges() {
		for (String change : List.of("device.set('name', 'changed')", "device.add('vrf', 'X')", "device.remove('module', 'X')",
			"device.add('tag', 5)")) {
			Assertions.assertEquals(ResultOption.INVALIDRULE, this.check(change + "\nreturn result_option.CONFORMING").result(), change);
		}
		Assertions.assertEquals("router00001", this.draft.getName());
	}

	@Test
	@DisplayName("Bypassing the Python device, the helper still refuses to change the device")
	void helperIsReadOnly() {
		CheckResult result = this.check("""
			helper = device._device_helper
			helper.set('name', 'changed')
			helper.set('location', 'Elsewhere')
			helper.set('mainMemorySize', 1.0)
			helper.set('configurationSaved', False)
			helper.add('vrf', 'HACKED')
			helper.add('module', {'slot': 'x', 'part_number': 'y', 'serial_number': 'z'})
			helper.reset()
			helper.remove('vrf', 'VRF1')
			return result_option.CONFORMING
			""");

		assertConforming(result);
		Device unchanged = TestDevices.find(this.current.id());
		Device after = this.draft.toDevice();
		Assertions.assertEquals(unchanged.name(), after.name());
		Assertions.assertEquals(unchanged.location(), after.location());
		Assertions.assertEquals(unchanged.family(), after.family());
		Assertions.assertEquals(unchanged.vrfInstances(), after.vrfInstances());
		Assertions.assertEquals(unchanged.modules().size(), after.modules().size());
		Assertions.assertEquals(unchanged.networkInterfaces().size(), after.networkInterfaces().size());
		Assertions.assertEquals(unchanged.attributes().size(), after.attributes().size());
		String log = this.taskContext.getLog();
		Assertions.assertTrue(log.contains("Setting key name is forbidden"), log);
		Assertions.assertTrue(log.contains("Adding key vrf is forbidden"), log);
		Assertions.assertTrue(log.contains("Resetting key is forbidden"), log);
		Assertions.assertTrue(log.contains("Removing key vrf is not supported"), log);
	}

	@Test
	@DisplayName("Through the helper, invalid tags are ignored")
	void invalidTags() {
		CheckResult result = this.check("""
			helper = device._device_helper
			helper.add('tag', '   ')
			helper.add('tag', 5)
			helper.remove('tag', 5)
			device.add('tag', 'valid')
			return result_option.CONFORMING
			""");

		assertConforming(result);
		Assertions.assertEquals(Set.of("valid"), this.draft.getTags());
		String log = this.taskContext.getLog();
		Assertions.assertTrue(log.contains("Ignoring invalid device tag"), log);
		Assertions.assertTrue(log.contains("The tag to add should be a string"), log);
		Assertions.assertTrue(log.contains("The tag to remove should be a string"), log);
	}

	@Test
	@DisplayName("Resolves an address with nslookup")
	void nslookup() {
		CheckResult result = this.check("""
			record = device.nslookup('127.0.0.1')
			return {'result': result_option.CONFORMING, 'comment': record['address']}
			""");

		assertConforming(result);
		Assertions.assertEquals("127.0.0.1", result.comment());
	}
}
