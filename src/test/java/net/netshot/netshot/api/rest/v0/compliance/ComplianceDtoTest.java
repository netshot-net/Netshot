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
package net.netshot.netshot.api.rest.v0.compliance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import net.netshot.netshot.compliance.ExemptedDevice;
import net.netshot.netshot.compliance.HardwareRule;
import net.netshot.netshot.compliance.HardwareRuleDefinition;
import net.netshot.netshot.compliance.HardwareRuleView;
import net.netshot.netshot.compliance.JavaScriptRule;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.PolicyDefinition;
import net.netshot.netshot.compliance.PolicyView;
import net.netshot.netshot.compliance.PythonRule;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.RuleDefinition;
import net.netshot.netshot.compliance.RuleSummary;
import net.netshot.netshot.compliance.RuleTestResult;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRuleDefinition;
import net.netshot.netshot.compliance.SoftwareRuleView;
import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.inventory.compliance.CheckResult.ResultOption;
import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.contract.DtoContract;

/**
 * Checks that the compliance DTOs and their mappers are a faithful replacement of the entities
 * the REST API used to write: the representation of an in-memory rule (of every kind), policy,
 * software and hardware rule is the same, in JSON and XML, as the one of the entity with the
 * same view; the requests are read like the former request classes did. (The API contract tests
 * only cover the kinds of objects the seed data holds.)
 */
@ScriptEngineTest
@DisplayName("v0 compliance DTOs")
class ComplianceDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final XmlMapper XML = XmlMapper.builder().build();

	@RegisterExtension
	private final DtoContract contract = new DtoContract(ComplianceDtoTest.class);

	private static byte[] json(Object value) throws Exception {
		return JSON.writer().writeValueAsBytes(value);
	}

	private static byte[] xml(Object value) throws Exception {
		return XML.writer().writeValueAsBytes(value);
	}

	private void assertRepresentation(Object dto) throws Exception {
		this.contract.checkBoth(json(dto), xml(dto));
	}

	private static DynamicDeviceGroup dynamicGroup() {
		return DynamicDeviceGroup.builder().id(100).name("Dynamic").query("[Name] contains \"r\"").folder("Production")
			.hiddenFromReports(true).changeDate(Instant.ofEpochMilli(1_600_000_200_000L)).build();
	}

	private static StaticDeviceGroup staticGroup() {
		return StaticDeviceGroup.builder().id(101).name("Static").folder("").changeDate(Instant.ofEpochMilli(1_600_000_300_000L)).build();
	}

	private static TextRule textRule() {
		return TextRule.builder("Text rule", 0)
			.id(7)
			.enabled(true)
			.text("line1\nline2")
			.regExp(true)
			.context("interface .*")
			.field("runningConfig")
			.deviceDriver("CiscoIOS12")
			.invert(true)
			.matchAll(true)
			.anyBlock(true)
			.normalize(true)
			.build();
	}

	@Test
	@DisplayName("A text rule has the same JSON and XML representation as the entity")
	void textRuleRepresentation() throws Exception {
		TextRule rule = textRule();
		this.assertRepresentation(RuleMapper.toDto(TestDrivers.registry(), rule));
		TextRule unknownDriver = TextRule.builder("Other", 0).deviceDriver("NoSuchDriver").build();
		this.assertRepresentation(RuleMapper.toDto(TestDrivers.registry(), unknownDriver));
		TextRule noDriver = TextRule.builder("Another", 0).deviceDriver("").build();
		this.assertRepresentation(RuleMapper.toDto(TestDrivers.registry(), noDriver));
		// Make sure the check is meaningful
		String json = new String(json(RuleMapper.toDto(TestDrivers.registry(), rule)));
		Assertions.assertTrue(json.contains("\"type\":\"TextRule\""), json);
		Assertions.assertTrue(json.contains("\"deviceDriverDescription\":\"Cisco IOS and IOS-XE\""), json);
		String xml = new String(xml(RuleMapper.toDto(TestDrivers.registry(), rule)));
		Assertions.assertTrue(xml.startsWith("<TextRule type=\"TextRule\">"), xml);
	}

	@Test
	@DisplayName("A JavaScript rule has the same JSON and XML representation as the entity")
	void javaScriptRuleRepresentation() throws Exception {
		JavaScriptRule rule = new JavaScriptRule(8, 0, null, "JS rule", true, 0, "function check(device) {\n  return CONFORMING;\n}");
		this.assertRepresentation(RuleMapper.toDto(TestDrivers.registry(), rule));
		JavaScriptRule template = JavaScriptRule.of("JS template", 0);
		this.assertRepresentation(RuleMapper.toDto(TestDrivers.registry(), template));
		String json = new String(json(RuleMapper.toDto(TestDrivers.registry(), rule)));
		Assertions.assertTrue(json.contains("\"type\":\"JavaScriptRule\""), json);
		Assertions.assertTrue(new String(xml(RuleMapper.toDto(TestDrivers.registry(), rule))).startsWith("<JavaScriptRule type=\"JavaScriptRule\">"));
	}

	@Test
	@DisplayName("A Python rule has the same JSON and XML representation as the entity")
	void pythonRuleRepresentation() throws Exception {
		PythonRule rule = new PythonRule(9, 0, null, "Python rule", false, 0, "def check(device):\n  return {'result': result_option.CONFORMING}");
		this.assertRepresentation(RuleMapper.toDto(TestDrivers.registry(), rule));
		PythonRule template = PythonRule.of("Python template", 0);
		this.assertRepresentation(RuleMapper.toDto(TestDrivers.registry(), template));
		String json = new String(json(RuleMapper.toDto(TestDrivers.registry(), rule)));
		Assertions.assertTrue(json.contains("\"type\":\"PythonRule\""), json);
		Assertions.assertTrue(new String(xml(RuleMapper.toDto(TestDrivers.registry(), rule))).startsWith("<PythonRule type=\"PythonRule\">"));
	}

	@Test
	@DisplayName("A list of rules of every kind has the same representation as the entities")
	void ruleListRepresentation() throws Exception {
		JavaScriptRule js = JavaScriptRule.of("JS rule", 0).withId(8);
		PythonRule python = PythonRule.of("Python rule", 0).withId(9);
		List<Rule> rules = new ArrayList<>(List.of(textRule(), js, python));
		this.assertRepresentation(RuleMapper.toDtos(TestDrivers.registry(), rules));
		List<RuleDto> dtos = RuleMapper.toDtos(TestDrivers.registry(), rules);
		Assertions.assertInstanceOf(RuleDto.Text.class, dtos.get(0));
		Assertions.assertInstanceOf(RuleDto.JavaScript.class, dtos.get(1));
		Assertions.assertInstanceOf(RuleDto.Python.class, dtos.get(2));
	}

	@Test
	@DisplayName("A policy has the same JSON and XML representation as the entity (both kinds of group)")
	void policyRepresentation() throws Exception {
		DynamicDeviceGroup dynamic = dynamicGroup();
		StaticDeviceGroup fixed = staticGroup();
		PolicyView policy = new PolicyView(new Policy(3, 0, null, "Policy", Set.of(dynamic.id(), fixed.id())), List.of(dynamic, fixed));
		this.assertRepresentation(PolicyMapper.toDto(policy));
		PolicyView empty = new PolicyView(new Policy(4, 0, null, "Empty", Set.of()), List.of());
		this.assertRepresentation(PolicyMapper.toDto(empty));
		this.assertRepresentation(PolicyMapper.toDtos(List.of(policy, empty)));
		String json = new String(json(PolicyMapper.toDto(policy)));
		Assertions.assertTrue(json.contains("\"type\":\"DynamicDeviceGroup\""), json);
		Assertions.assertTrue(json.contains("\"type\":\"StaticDeviceGroup\""), json);
		Assertions.assertTrue(new String(xml(PolicyMapper.toDto(policy))).startsWith("<Policy>"));
	}

	@Test
	@DisplayName("A software rule has the same JSON and XML representation as the entity (with and without group, driver)")
	void softwareRuleRepresentation() throws Exception {
		DynamicDeviceGroup dynamic = dynamicGroup();
		StaticDeviceGroup fixed = staticGroup();
		SoftwareRuleView rule = new SoftwareRuleView(new SoftwareRule(5, 0, null, 10, dynamic.id(), "CiscoIOS12", "Cisco 2900", true,
			"15\\..*", true, "CISCO.*", true, ConformanceLevel.SILVER), dynamic);
		this.assertRepresentation(SoftwareRuleMapper.toDto(TestDrivers.registry(), rule));
		SoftwareRuleView anything = new SoftwareRuleView(new SoftwareRule(6, 0, null, 20, null, null, "", false, "1", false, "", false,
			ConformanceLevel.BRONZE), null);
		this.assertRepresentation(SoftwareRuleMapper.toDto(TestDrivers.registry(), anything));
		SoftwareRuleView staticTarget = new SoftwareRuleView(new SoftwareRule(7, 0, null, 30, fixed.id(), "NoSuchDriver", "", false, "1",
			false, null, false, ConformanceLevel.GOLD), fixed);
		this.assertRepresentation(SoftwareRuleMapper.toDtos(TestDrivers.registry(), List.of(rule, anything, staticTarget)));
		Assertions.assertTrue(new String(xml(SoftwareRuleMapper.toDto(TestDrivers.registry(), rule))).startsWith("<SoftwareRule>"));
		Assertions.assertTrue(new String(json(SoftwareRuleMapper.toDto(TestDrivers.registry(), rule))).contains("\"deviceType\":\"Cisco IOS and IOS-XE\""));
	}

	@Test
	@DisplayName("A hardware rule has the same JSON and XML representation as the entity (with and without group, dates)")
	void hardwareRuleRepresentation() throws Exception {
		DynamicDeviceGroup dynamic = dynamicGroup();
		StaticDeviceGroup fixed = staticGroup();
		HardwareRuleView rule = new HardwareRuleView(new HardwareRule(5, 0, null, "CiscoIOS12", fixed.id(), "Cisco 2900", false, "CISCO29.*",
			true, Instant.ofEpochMilli(1_600_000_000_000L), Instant.ofEpochMilli(1_700_000_000_000L)), fixed);
		this.assertRepresentation(HardwareRuleMapper.toDto(TestDrivers.registry(), rule));
		HardwareRuleView anything = new HardwareRuleView(new HardwareRule(6, 0, null, null, null, "", true, "", false, null, null), null);
		this.assertRepresentation(HardwareRuleMapper.toDto(TestDrivers.registry(), anything));
		HardwareRuleView dynamicTarget = new HardwareRuleView(new HardwareRule(7, 0, null, "NoSuchDriver", dynamic.id(), null, false, null,
			false, null, Instant.ofEpochMilli(1_700_000_000_000L)), dynamic);
		this.assertRepresentation(HardwareRuleMapper.toDtos(TestDrivers.registry(), List.of(rule, anything, dynamicTarget)));
		Assertions.assertTrue(new String(xml(HardwareRuleMapper.toDto(TestDrivers.registry(), rule))).startsWith("<HardwareRule>"));
	}

	@Test
	@DisplayName("An exempted device is written like a light device with its expiration date")
	void exemptedDeviceRepresentation() throws Exception {
		Date expiration = new Date(1_700_000_000_000L);
		ExemptedDevice exempted = new ExemptedDevice(new DeviceSummary(1, "r1", "Cisco", "1.2.3.4",
			Device.Status.INPRODUCTION, "CiscoIOS12", false, null, true, ConformanceLevel.GOLD, Device.NetworkClass.ROUTER),
			expiration);
		ExemptedDeviceDto dto = RuleMapper.toExemptedDtos(List.of(exempted)).get(0);
		this.assertRepresentation(dto);
		this.assertRepresentation(new ArrayList<>(List.of(dto)));
		ExemptedDevice noExpiration = new ExemptedDevice(exempted.device(), null);
		this.assertRepresentation(RuleMapper.toExemptedDtos(List.of(noExpiration)).get(0));
	}

	@Test
	@DisplayName("The single objects keep the XML root names of the former classes")
	void xmlRootNames() throws Exception {
		Assertions.assertTrue(new String(xml(RuleMapper.toDto(new RuleTestResult(ResultOption.CONFORMING, "log", "ok"))))
			.startsWith("<RsRuleTestResult>"));
		Assertions.assertTrue(new String(xml(RuleMapper.toSummaryDtos(List.of(
			new RuleSummary(1, "r", TextRule.class, true, 2))).get(0)))
			.startsWith("<RsLightRule>"));
		Assertions.assertEquals("TextRule", new RuleSummary(1, "r", TextRule.class, true, 2).type());
		Assertions.assertEquals("PythonRule", new RuleSummary(1, "r", PythonRule.class, true, 2).type());
		RuleTestResultDto dto = RuleMapper.toDto(new RuleTestResult(ResultOption.NONCONFORMING, "[DEBUG] x\n", "comment"));
		Assertions.assertEquals("[DEBUG] x\n", dto.scriptError());
		String json = new String(json(dto));
		Assertions.assertTrue(json.contains("\"result\":\"NONCONFORMING\""), json);
		Assertions.assertTrue(json.contains("\"scriptError\":\"[DEBUG] x\\n\""), json);
		Assertions.assertTrue(json.contains("\"comment\":\"comment\""), json);
	}

	@Test
	@DisplayName("A policy request has the default values of the former request")
	void policyRequestDefaults() throws Exception {
		PolicyRequest empty = JSON.readerFor(PolicyRequest.class).readValue("{}");
		Assertions.assertEquals("", empty.name());
		Assertions.assertTrue(empty.targetGroups().isEmpty());
		PolicyRequest request = JSON.readerFor(PolicyRequest.class)
			.readValue("{\"id\": 3, \"name\": \" Policy \", \"targetGroups\": [1, 2, 2]}");
		Assertions.assertEquals(" Policy ", request.name());
		Assertions.assertEquals(Set.of(1L, 2L), request.targetGroups());
		Assertions.assertEquals(new PolicyDefinition(" Policy ", Set.of(1L, 2L)),
			PolicyMapper.toDefinition(request));
	}

	@Test
	@DisplayName("A rule request has the default values of the former request, and maps to the command")
	void ruleRequestDefaults() throws Exception {
		RuleRequest empty = JSON.readerFor(RuleRequest.class).readValue("{}");
		Assertions.assertEquals("", empty.type());
		Assertions.assertNull(empty.name());
		Assertions.assertNull(empty.script());
		Assertions.assertFalse(empty.enabled());
		Assertions.assertEquals(0, empty.policy());
		Assertions.assertTrue(empty.exemptions().isEmpty());
		Assertions.assertNull(empty.regExp());
		Assertions.assertNull(empty.invert());
		Assertions.assertNull(empty.normalize());
		RuleRequest request = JSON.readerFor(RuleRequest.class).readValue("""
			{"id": 4, "type": "TextRule", "name": "n", "policy": 2, "enabled": true, "exemptions": {"5": 1700000000000},
			 "text": "t", "regExp": false, "context": "c", "driver": "d", "field": "f", "anyBlock": true,
			 "matchAll": false, "invert": true, "normalize": false, "script": "s"}""");
		RuleDefinition definition = RuleMapper.toDefinition(request);
		Assertions.assertEquals(new RuleDefinition("TextRule", "n", 2, true, Map.of(5L, new Date(1_700_000_000_000L)), "s",
			"t", false, "c", "d", "f", true, false, true, false), definition);
		RuleTestRequest test = JSON.readerFor(RuleTestRequest.class)
			.readValue("{\"type\": \"JavaScriptRule\", \"script\": \"s\", \"device\": 12}");
		Assertions.assertEquals(12, test.device());
		Assertions.assertEquals("JavaScriptRule", RuleMapper.toDefinition(test).type());
		Assertions.assertEquals("s", RuleMapper.toDefinition(test).script());
	}

	@Test
	@DisplayName("A software rule request has the default values of the former request, and maps to the command")
	void softwareRuleRequestDefaults() throws Exception {
		SoftwareRuleRequest empty = JSON.readerFor(SoftwareRuleRequest.class).readValue("{}");
		Assertions.assertEquals(-1L, empty.group());
		Assertions.assertEquals("", empty.driver());
		Assertions.assertEquals("", empty.version());
		Assertions.assertEquals("", empty.family());
		Assertions.assertEquals("", empty.partNumber());
		Assertions.assertEquals(ConformanceLevel.GOLD, empty.level());
		Assertions.assertFalse(empty.versionRegExp());
		Assertions.assertEquals(new SoftwareRuleDefinition(null, "", "", false, "", false, "", false, ConformanceLevel.GOLD),
			SoftwareRuleMapper.toDefinition(empty));
		SoftwareRuleRequest request = JSON.readerFor(SoftwareRuleRequest.class).readValue("""
			{"id": 1, "group": 5, "driver": "CiscoIOS12", "version": "1", "versionRegExp": true, "family": "f",
			 "familyRegExp": true, "partNumber": "p", "partNumberRegExp": true, "level": "BRONZE"}""");
		Assertions.assertEquals(new SoftwareRuleDefinition(5L, "CiscoIOS12", "f", true, "1", true, "p", true, ConformanceLevel.BRONZE),
			SoftwareRuleMapper.toDefinition(request));
	}

	@Test
	@DisplayName("A hardware rule request has the default values of the former request, and maps to the command")
	void hardwareRuleRequestDefaults() throws Exception {
		HardwareRuleRequest empty = JSON.readerFor(HardwareRuleRequest.class).readValue("{}");
		Assertions.assertEquals(-1L, empty.group());
		Assertions.assertEquals("", empty.driver());
		Assertions.assertEquals("", empty.family());
		Assertions.assertEquals("", empty.partNumber());
		Assertions.assertNull(empty.endOfSale());
		Assertions.assertNull(empty.endOfLife());
		HardwareRuleRequest request = JSON.readerFor(HardwareRuleRequest.class).readValue("""
			{"id": 1, "group": 5, "driver": "d", "partNumber": "p", "partNumberRegExp": true, "family": "f",
			 "familyRegExp": true, "endOfSale": 1700000000000, "endOfLife": null}""");
		Assertions.assertEquals(new HardwareRuleDefinition(5L, "d", "f", true, "p", true,
			new Date(1_700_000_000_000L), null), HardwareRuleMapper.toDefinition(request));
	}

	@Test
	@DisplayName("A request with an unknown property is refused, like it used to be")
	void unknownPropertiesAreRefused() {
		Assertions.assertThrows(JsonMappingException.class, () -> JSON.readerFor(PolicyRequest.class)
			.readValue("{\"name\": \"p\", \"unknown\": 1}"));
		Assertions.assertThrows(JsonMappingException.class, () -> JSON.readerFor(RuleRequest.class)
			.readValue("{\"name\": \"p\", \"unknown\": 1}"));
		Assertions.assertThrows(JsonMappingException.class, () -> JSON.readerFor(RuleTestRequest.class)
			.readValue("{\"unknown\": 1}"));
		Assertions.assertThrows(JsonMappingException.class, () -> JSON.readerFor(SoftwareRuleRequest.class)
			.readValue("{\"unknown\": 1}"));
		Assertions.assertThrows(JsonMappingException.class, () -> JSON.readerFor(HardwareRuleRequest.class)
			.readValue("{\"unknown\": 1}"));
	}
}
