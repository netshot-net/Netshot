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
import net.netshot.netshot.inventory.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;
import net.netshot.netshot.testing.fake.FakeTaskContext;

/**
 * What a JavaScript compliance rule reads through {@code device.get}: every item of the device (inventory, modules,
 * interfaces, driver attributes of the device and of the last configuration, diagnostic results), and of the other
 * stored devices, by ID or name; and what it can change (the comments and tags only).
 */
@DatabaseTest
@DisplayName("JavaScript rule access to the devices (DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
class JsRuleDeviceAccessTest {

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
	 * @param body the body of the check function
	 * @return the result
	 */
	private CheckResult check(String body) {
		JavaScriptRule rule = JavaScriptRule.of("Device access rule", 0).toBuilder().enabled(true)
			.script("function check(device) {\n" + body + "\n}").build();
		return this.evaluators.evaluate(rule, this.draft, false, this.taskContext);
	}

	private String conformingComment(CheckResult result) {
		Assertions.assertEquals(ResultOption.CONFORMING, result.result(), this.taskContext.getLog());
		return result.comment();
	}

	@Test
	@DisplayName("Reads the inventory items of the device")
	void inventory() {
		CheckResult result = this.check("""
			const items = ["type", "name", "family", "managementIpAddress", "managementDomain", "location", "contact",
				"softwareVersion", "serialNumber", "comments", "networkClass"];
			const values = items.map(item => device.get(item));
			// The arrays are read-only: copied to be sorted
			values.push(Array.from(device.get("vrfs")).sort().join(","));
			values.push(device.get("virtualDevices").length);
			values.push(device.get("tags").length);
			return { result: CONFORMING, comment: values.join("|") };
			""");
		Assertions.assertEquals("Cisco IOS and IOS-XE|router00001|Unknown IOS device|172.16.0.1|Test domain|Test Location|Test Contact"
			+ "|16.1.6|16161616TEST16|Comments for testing|ROUTER|VRF1,VRF2|0|0", this.conformingComment(result));
	}

	@Test
	@DisplayName("Reads the modules and the interfaces with their addresses")
	void modulesAndInterfaces() {
		CheckResult result = this.check("""
			const modules = device.get("modules").map(m => m.slot + ":" + m.partNumber + ":" + m.serialNumber);
			const interfaces = device.get("interfaces").map(i => [i.name, i.vrf, i.enabled, i.level3, i.description,
				i.ip.map(ip => (ip.ip || ip.ipv6) + "/" + ip.mask + ":" + ip.usage).join(",")].join(";"));
			return { result: CONFORMING, comment: modules.join(" ") + " / " + interfaces.join(" / ") };
			""");
		Assertions.assertEquals("chassis:TESTCHASSIS:16161616TEST16 slot:TESTSLOT:29038POSD203 / "
			+ "GigabitEthernet0/0;VRF1;true;true;Desc for interface 0/0;10.0.1.1/25:PRIMARY,2016:1:1:1:0:0:0:1/64:PRIMARY / "
			+ "GigabitEthernet0/1;VRF2;false;true;Desc for interface 0/1;10.0.1.129/25:PRIMARY,2016:1:1:2:0:0:0:1/64:PRIMARY",
			this.conformingComment(result));
	}

	@Test
	@DisplayName("Reads the driver attributes, by name, and the diagnostic results")
	void attributesAndDiagnostics() {
		this.draft.addDiagnosticResult(DiagnosticTextResult.of(5L, "Reload reason", "Reload Command"));
		CheckResult result = this.check("""
			const values = [device.get("iosVersion"), device.get("mainMemorySize"),
				device.get("configRegister"), device.get("configurationSaved"), device.get("Reload reason"),
				device.get("runningConfig").indexOf("hostname router00001") >= 0, device.get("noSuchItem")];
			return { result: CONFORMING, comment: values.join("|") };
			""");
		Assertions.assertEquals("16.1.6|2048|0x1202|true|Reload Command|true|", this.conformingComment(result));
	}

	@Test
	@DisplayName("Reads another device by ID or name, and nothing for unknown or unconfigured ones")
	void otherDevices() {
		CheckResult result = this.check("""
			const values = [device.get("name", %d), device.get("serialNumber", "router00002"), device.get("iosVersion", %d),
				device.get("name", %d), device.get("name", %d), device.get("name", "nosuchdevice")];
			return { result: CONFORMING, comment: values.map(v => String(v)).join("|") };
			""".formatted(this.other.id(), this.other.id(), this.unconfigured.id(), this.other.id() + 1000));
		Assertions.assertEquals("router00002|16161616TEST16|16.1.6|null|null|null", this.conformingComment(result));
	}

	@Test
	@DisplayName("Changes only the comments and the tags of the device")
	void changes() {
		CheckResult result = this.check("""
			device.set("comments", "Checked");
			device.add("tag", "checked");
			device.add("tag", "temporary");
			device.remove("tag", "temporary");
			const refused = [];
			for (const change of [() => device.set("name", "changed"), () => device.add("vrf", "X"), () => device.remove("module", "X")]) {
				try {
					change();
				}
				catch (e) {
					refused.push(String(e));
				}
			}
			return { result: CONFORMING, comment: refused.join("|") };
			""");
		Assertions.assertEquals("Only 'comments' can be set on the device from a compliance rule."
			+ "|Only a 'tag' can be added to the device from a compliance rule."
			+ "|Only a 'tag' can be removed from the device in a compliance rule.", this.conformingComment(result));
		Assertions.assertEquals("Checked", this.draft.toDevice().comments());
		Assertions.assertEquals(Set.of("checked"), this.draft.getTags());
		Assertions.assertEquals("router00001", this.draft.toDevice().name());
	}
}
