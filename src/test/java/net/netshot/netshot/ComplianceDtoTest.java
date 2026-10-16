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
package net.netshot.netshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import net.netshot.netshot.api.rest.v0.compliance.ExemptedDeviceDto;
import net.netshot.netshot.api.rest.v0.compliance.HardwareRuleMapper;
import net.netshot.netshot.api.rest.v0.compliance.HardwareRuleRequest;
import net.netshot.netshot.api.rest.v0.compliance.PolicyMapper;
import net.netshot.netshot.api.rest.v0.compliance.PolicyRequest;
import net.netshot.netshot.api.rest.v0.compliance.RuleDto;
import net.netshot.netshot.api.rest.v0.compliance.RuleMapper;
import net.netshot.netshot.api.rest.v0.compliance.RuleRequest;
import net.netshot.netshot.api.rest.v0.compliance.RuleTestRequest;
import net.netshot.netshot.api.rest.v0.compliance.RuleTestResultDto;
import net.netshot.netshot.api.rest.v0.compliance.SoftwareRuleMapper;
import net.netshot.netshot.api.rest.v0.compliance.SoftwareRuleRequest;
import net.netshot.netshot.compliance.CheckResult.ResultOption;
import net.netshot.netshot.compliance.ExemptedDevice;
import net.netshot.netshot.compliance.HardwareRule;
import net.netshot.netshot.compliance.HardwareRuleDefinition;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.PolicyDefinition;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.RuleDefinition;
import net.netshot.netshot.compliance.RuleSummary;
import net.netshot.netshot.compliance.RuleTestResult;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.compliance.SoftwareRuleDefinition;
import net.netshot.netshot.compliance.rules.JavaScriptRule;
import net.netshot.netshot.compliance.rules.PythonRule;
import net.netshot.netshot.compliance.rules.TextRule;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.platform.rest.RestViews.RestApiView;

/**
 * Checks that the compliance DTOs and their mappers are a faithful replacement of the entities
 * the REST API used to write: the representation of an in-memory rule (of every kind), policy,
 * software and hardware rule is the same, in JSON and XML, as the one of the entity with the
 * same view; the requests are read like the former request classes did. (The API contract tests
 * only cover the kinds of objects the seed data holds.)
 */
class ComplianceDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder()
		.disable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();
	private static final XmlMapper XML = XmlMapper.builder()
		.disable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();

	private final ApiContractGolden golden = new ApiContractGolden(0);

	@BeforeAll
	static void loadDrivers() throws Exception {
		Properties config = new Properties();
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "WARN");
		Netshot.initConfig(config);
		DeviceDriver.refreshDrivers();
	}

	private static byte[] json(Object value) throws Exception {
		return JSON.writerWithView(RestApiView.class).writeValueAsBytes(value);
	}

	private static byte[] xml(Object value) throws Exception {
		return XML.writerWithView(RestApiView.class).writeValueAsBytes(value);
	}

	private void assertSameRepresentation(Object entity, Object dto) throws Exception {
		String expectedJson = this.golden.canonicalize(ApiContractGolden.Format.JSON, json(entity), ApiContractGolden.Transform.NONE, false);
		String actualJson = this.golden.canonicalize(ApiContractGolden.Format.JSON, json(dto), ApiContractGolden.Transform.NONE, false);
		Assertions.assertEquals(expectedJson, actualJson, "JSON");
		String expectedXml = this.golden.canonicalize(ApiContractGolden.Format.XML, xml(entity), ApiContractGolden.Transform.NONE, false);
		String actualXml = this.golden.canonicalize(ApiContractGolden.Format.XML, xml(dto), ApiContractGolden.Transform.NONE, false);
		Assertions.assertEquals(expectedXml, actualXml, "XML");
	}

	private static DynamicDeviceGroup dynamicGroup() {
		DynamicDeviceGroup dynamic = new DynamicDeviceGroup("Dynamic", "CiscoIOS12", "[Name] contains \"r\"");
		dynamic.setId(100);
		dynamic.setFolder("Production");
		dynamic.setHiddenFromReports(true);
		dynamic.setChangeDate(new Date(1_600_000_200_000L));
		return dynamic;
	}

	private static StaticDeviceGroup staticGroup() {
		StaticDeviceGroup fixed = new StaticDeviceGroup("Static");
		fixed.setId(101);
		fixed.setChangeDate(new Date(1_600_000_300_000L));
		return fixed;
	}

	private static TextRule textRule() {
		TextRule rule = new TextRule("Text rule", null);
		rule.setId(7);
		rule.setEnabled(true);
		rule.setText("line1\nline2");
		rule.setRegExp(true);
		rule.setContext("interface .*");
		rule.setField("runningConfig");
		rule.setDeviceDriver("CiscoIOS12");
		rule.setInvert(true);
		rule.setMatchAll(true);
		rule.setAnyBlock(true);
		rule.setNormalize(true);
		return rule;
	}

	@Test
	@DisplayName("A text rule has the same JSON and XML representation as the entity")
	void textRuleRepresentation() throws Exception {
		TextRule rule = textRule();
		this.assertSameRepresentation(rule, RuleMapper.toDto(rule));
		TextRule unknownDriver = new TextRule("Other", null);
		unknownDriver.setDeviceDriver("NoSuchDriver");
		this.assertSameRepresentation(unknownDriver, RuleMapper.toDto(unknownDriver));
		TextRule noDriver = new TextRule("Another", null);
		noDriver.setDeviceDriver("");
		this.assertSameRepresentation(noDriver, RuleMapper.toDto(noDriver));
		// Make sure the check is meaningful
		String json = new String(json(RuleMapper.toDto(rule)));
		Assertions.assertTrue(json.contains("\"type\":\"TextRule\""), json);
		Assertions.assertTrue(json.contains("\"deviceDriverDescription\":\"Cisco IOS and IOS-XE\""), json);
		String xml = new String(xml(RuleMapper.toDto(rule)));
		Assertions.assertTrue(xml.startsWith("<TextRule type=\"TextRule\">"), xml);
	}

	@Test
	@DisplayName("A JavaScript rule has the same JSON and XML representation as the entity")
	void javaScriptRuleRepresentation() throws Exception {
		JavaScriptRule rule = new JavaScriptRule("JS rule", null);
		rule.setId(8);
		rule.setEnabled(true);
		rule.setScript("function check(device) {\n  return CONFORMING;\n}");
		this.assertSameRepresentation(rule, RuleMapper.toDto(rule));
		JavaScriptRule template = new JavaScriptRule("JS template", null);
		this.assertSameRepresentation(template, RuleMapper.toDto(template));
		String json = new String(json(RuleMapper.toDto(rule)));
		Assertions.assertTrue(json.contains("\"type\":\"JavaScriptRule\""), json);
		Assertions.assertTrue(new String(xml(RuleMapper.toDto(rule))).startsWith("<JavaScriptRule type=\"JavaScriptRule\">"));
	}

	@Test
	@DisplayName("A Python rule has the same JSON and XML representation as the entity")
	void pythonRuleRepresentation() throws Exception {
		PythonRule rule = new PythonRule("Python rule", null);
		rule.setId(9);
		rule.setScript("def check(device):\n  return {'result': result_option.CONFORMING}");
		this.assertSameRepresentation(rule, RuleMapper.toDto(rule));
		PythonRule template = new PythonRule("Python template", null);
		this.assertSameRepresentation(template, RuleMapper.toDto(template));
		String json = new String(json(RuleMapper.toDto(rule)));
		Assertions.assertTrue(json.contains("\"type\":\"PythonRule\""), json);
		Assertions.assertTrue(new String(xml(RuleMapper.toDto(rule))).startsWith("<PythonRule type=\"PythonRule\">"));
	}

	@Test
	@DisplayName("A list of rules of every kind has the same representation as the entities")
	void ruleListRepresentation() throws Exception {
		JavaScriptRule js = new JavaScriptRule("JS rule", null);
		js.setId(8);
		PythonRule python = new PythonRule("Python rule", null);
		python.setId(9);
		List<Rule> rules = new ArrayList<>(List.of(textRule(), js, python));
		this.assertSameRepresentation(rules, RuleMapper.toDtos(rules));
		List<RuleDto> dtos = RuleMapper.toDtos(rules);
		Assertions.assertInstanceOf(RuleDto.Text.class, dtos.get(0));
		Assertions.assertInstanceOf(RuleDto.JavaScript.class, dtos.get(1));
		Assertions.assertInstanceOf(RuleDto.Python.class, dtos.get(2));
	}

	@Test
	@DisplayName("A policy has the same JSON and XML representation as the entity (both kinds of group)")
	void policyRepresentation() throws Exception {
		Set<DeviceGroup> groups = new HashSet<>();
		groups.add(dynamicGroup());
		groups.add(staticGroup());
		Policy policy = new Policy("Policy", groups);
		policy.setId(3);
		this.assertSameRepresentation(policy, PolicyMapper.toDto(policy));
		Policy empty = new Policy("Empty", new HashSet<>());
		empty.setId(4);
		this.assertSameRepresentation(empty, PolicyMapper.toDto(empty));
		// A group which doesn't exist gives a null in the groups of a new policy (see PolicyService)
		Set<DeviceGroup> withNull = new HashSet<>();
		withNull.add(null);
		withNull.add(staticGroup());
		Policy quirk = new Policy("Quirk", withNull);
		quirk.setId(5);
		this.assertSameRepresentation(quirk, PolicyMapper.toDto(quirk));
		this.assertSameRepresentation(new ArrayList<>(List.of(policy, empty)), PolicyMapper.toDtos(List.of(policy, empty)));
		String json = new String(json(PolicyMapper.toDto(policy)));
		Assertions.assertTrue(json.contains("\"type\":\"DynamicDeviceGroup\""), json);
		Assertions.assertTrue(json.contains("\"type\":\"StaticDeviceGroup\""), json);
		Assertions.assertTrue(new String(xml(PolicyMapper.toDto(policy))).startsWith("<Policy>"));
	}

	@Test
	@DisplayName("A software rule has the same JSON and XML representation as the entity (with and without group, driver)")
	void softwareRuleRepresentation() throws Exception {
		SoftwareRule rule = new SoftwareRule(10, dynamicGroup(), "CiscoIOS12", "Cisco 2900", true, "15\\..*", true,
			"CISCO.*", true, ConformanceLevel.SILVER);
		rule.setId(5);
		this.assertSameRepresentation(rule, SoftwareRuleMapper.toDto(rule));
		SoftwareRule anything = new SoftwareRule(20, null, null, "", false, "1", false, "", false, ConformanceLevel.BRONZE);
		anything.setId(6);
		this.assertSameRepresentation(anything, SoftwareRuleMapper.toDto(anything));
		SoftwareRule staticTarget = new SoftwareRule(30, staticGroup(), "NoSuchDriver", "", false, "1", false, null, false,
			ConformanceLevel.GOLD);
		staticTarget.setId(7);
		this.assertSameRepresentation(new ArrayList<>(List.of(rule, anything, staticTarget)),
			SoftwareRuleMapper.toDtos(List.of(rule, anything, staticTarget)));
		Assertions.assertTrue(new String(xml(SoftwareRuleMapper.toDto(rule))).startsWith("<SoftwareRule>"));
		Assertions.assertTrue(new String(json(SoftwareRuleMapper.toDto(rule))).contains("\"deviceType\":\"Cisco IOS and IOS-XE\""));
	}

	@Test
	@DisplayName("A hardware rule has the same JSON and XML representation as the entity (with and without group, dates)")
	void hardwareRuleRepresentation() throws Exception {
		HardwareRule rule = new HardwareRule("CiscoIOS12", staticGroup(), "Cisco 2900", false, "CISCO29.*", true,
			new Date(1_600_000_000_000L), new Date(1_700_000_000_000L));
		rule.setId(5);
		this.assertSameRepresentation(rule, HardwareRuleMapper.toDto(rule));
		HardwareRule anything = new HardwareRule(null, null, "", true, "", false, null, null);
		anything.setId(6);
		this.assertSameRepresentation(anything, HardwareRuleMapper.toDto(anything));
		HardwareRule dynamicTarget = new HardwareRule("NoSuchDriver", dynamicGroup(), null, false, null, false, null,
			new Date(1_700_000_000_000L));
		dynamicTarget.setId(7);
		this.assertSameRepresentation(new ArrayList<>(List.of(rule, anything, dynamicTarget)),
			HardwareRuleMapper.toDtos(List.of(rule, anything, dynamicTarget)));
		Assertions.assertTrue(new String(xml(HardwareRuleMapper.toDto(rule))).startsWith("<HardwareRule>"));
	}

	/** The former exempted device: a subclass of the light device. */
	@XmlRootElement(name = "RsLightExemptedDevice")
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsLightExemptedDevice extends RsLightDevice {

		private final Date expirationDate;

		public RsLightExemptedDevice(long id, String name, String family, String mgmtAddress, Device.Status status,
			String driver, Boolean eol, Boolean eos, Boolean configCompliant, ConformanceLevel softwareLevel,
			Device.NetworkClass networkClass, Date expirationDate) {
			super(id, name, family, mgmtAddress, status, driver, eol, eos, configCompliant, softwareLevel, networkClass);
			this.expirationDate = expirationDate;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public Date getExpirationDate() {
			return this.expirationDate;
		}
	}

	@Test
	@DisplayName("An exempted device has the same representation as the former subclass of the light device")
	void exemptedDeviceRepresentation() throws Exception {
		Date expiration = new Date(1_700_000_000_000L);
		RsLightExemptedDevice legacy = new RsLightExemptedDevice(1, "r1", "Cisco", "1.2.3.4", Device.Status.INPRODUCTION,
			"CiscoIOS12", false, null, true, ConformanceLevel.GOLD, Device.NetworkClass.ROUTER, expiration);
		ExemptedDevice exempted = new ExemptedDevice(new DeviceSummary(1, "r1", "Cisco", "1.2.3.4",
			Device.Status.INPRODUCTION, "CiscoIOS12", false, null, true, ConformanceLevel.GOLD, Device.NetworkClass.ROUTER),
			expiration);
		ExemptedDeviceDto dto = RuleMapper.toExemptedDtos(List.of(exempted)).get(0);
		this.assertSameRepresentation(legacy, dto);
		this.assertSameRepresentation(new ArrayList<>(List.of(legacy)), new ArrayList<>(List.of(dto)));
		ExemptedDevice noExpiration = new ExemptedDevice(exempted.device(), null);
		this.assertSameRepresentation(
			new RsLightExemptedDevice(1, "r1", "Cisco", "1.2.3.4", Device.Status.INPRODUCTION, "CiscoIOS12", false, null, true,
				ConformanceLevel.GOLD, Device.NetworkClass.ROUTER, null),
			RuleMapper.toExemptedDtos(List.of(noExpiration)).get(0));
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
		PolicyRequest empty = JSON.readerFor(PolicyRequest.class).withView(RestApiView.class).readValue("{}");
		Assertions.assertEquals("", empty.name());
		Assertions.assertTrue(empty.targetGroups().isEmpty());
		PolicyRequest request = JSON.readerFor(PolicyRequest.class).withView(RestApiView.class)
			.readValue("{\"id\": 3, \"name\": \" Policy \", \"targetGroups\": [1, 2, 2]}");
		Assertions.assertEquals(" Policy ", request.name());
		Assertions.assertEquals(Set.of(1L, 2L), request.targetGroups());
		Assertions.assertEquals(new PolicyDefinition(" Policy ", Set.of(1L, 2L)),
			PolicyMapper.toDefinition(request));
	}

	@Test
	@DisplayName("A rule request has the default values of the former request, and maps to the command")
	void ruleRequestDefaults() throws Exception {
		RuleRequest empty = JSON.readerFor(RuleRequest.class).withView(RestApiView.class).readValue("{}");
		Assertions.assertEquals("", empty.type());
		Assertions.assertNull(empty.name());
		Assertions.assertNull(empty.script());
		Assertions.assertFalse(empty.enabled());
		Assertions.assertEquals(0, empty.policy());
		Assertions.assertTrue(empty.exemptions().isEmpty());
		Assertions.assertNull(empty.regExp());
		Assertions.assertNull(empty.invert());
		Assertions.assertNull(empty.normalize());
		RuleRequest request = JSON.readerFor(RuleRequest.class).withView(RestApiView.class).readValue("""
			{"id": 4, "type": "TextRule", "name": "n", "policy": 2, "enabled": true, "exemptions": {"5": 1700000000000},
			 "text": "t", "regExp": false, "context": "c", "driver": "d", "field": "f", "anyBlock": true,
			 "matchAll": false, "invert": true, "normalize": false, "script": "s"}""");
		RuleDefinition definition = RuleMapper.toDefinition(request);
		Assertions.assertEquals(new RuleDefinition("TextRule", "n", 2, true, Map.of(5L, new Date(1_700_000_000_000L)), "s",
			"t", false, "c", "d", "f", true, false, true, false), definition);
		RuleTestRequest test = JSON.readerFor(RuleTestRequest.class).withView(RestApiView.class)
			.readValue("{\"type\": \"JavaScriptRule\", \"script\": \"s\", \"device\": 12}");
		Assertions.assertEquals(12, test.device());
		Assertions.assertEquals("JavaScriptRule", RuleMapper.toDefinition(test).type());
		Assertions.assertEquals("s", RuleMapper.toDefinition(test).script());
	}

	@Test
	@DisplayName("A software rule request has the default values of the former request, and maps to the command")
	void softwareRuleRequestDefaults() throws Exception {
		SoftwareRuleRequest empty = JSON.readerFor(SoftwareRuleRequest.class).withView(RestApiView.class).readValue("{}");
		Assertions.assertEquals(-1L, empty.group());
		Assertions.assertEquals("", empty.driver());
		Assertions.assertEquals("", empty.version());
		Assertions.assertEquals("", empty.family());
		Assertions.assertEquals("", empty.partNumber());
		Assertions.assertEquals(ConformanceLevel.GOLD, empty.level());
		Assertions.assertFalse(empty.versionRegExp());
		Assertions.assertEquals(new SoftwareRuleDefinition(null, "", "", false, "", false, "", false, ConformanceLevel.GOLD),
			SoftwareRuleMapper.toDefinition(empty));
		SoftwareRuleRequest request = JSON.readerFor(SoftwareRuleRequest.class).withView(RestApiView.class).readValue("""
			{"id": 1, "group": 5, "driver": "CiscoIOS12", "version": "1", "versionRegExp": true, "family": "f",
			 "familyRegExp": true, "partNumber": "p", "partNumberRegExp": true, "level": "BRONZE"}""");
		Assertions.assertEquals(new SoftwareRuleDefinition(5L, "CiscoIOS12", "f", true, "1", true, "p", true, ConformanceLevel.BRONZE),
			SoftwareRuleMapper.toDefinition(request));
	}

	@Test
	@DisplayName("A hardware rule request has the default values of the former request, and maps to the command")
	void hardwareRuleRequestDefaults() throws Exception {
		HardwareRuleRequest empty = JSON.readerFor(HardwareRuleRequest.class).withView(RestApiView.class).readValue("{}");
		Assertions.assertEquals(-1L, empty.group());
		Assertions.assertEquals("", empty.driver());
		Assertions.assertEquals("", empty.family());
		Assertions.assertEquals("", empty.partNumber());
		Assertions.assertNull(empty.endOfSale());
		Assertions.assertNull(empty.endOfLife());
		HardwareRuleRequest request = JSON.readerFor(HardwareRuleRequest.class).withView(RestApiView.class).readValue("""
			{"id": 1, "group": 5, "driver": "d", "partNumber": "p", "partNumberRegExp": true, "family": "f",
			 "familyRegExp": true, "endOfSale": 1700000000000, "endOfLife": null}""");
		Assertions.assertEquals(new HardwareRuleDefinition(5L, "d", "f", true, "p", true,
			new Date(1_700_000_000_000L), null), HardwareRuleMapper.toDefinition(request));
	}

	@Test
	@DisplayName("A request with an unknown property is refused, like it used to be")
	void unknownPropertiesAreRefused() {
		Assertions.assertThrows(JsonMappingException.class, () -> JSON.readerFor(PolicyRequest.class)
			.withView(RestApiView.class).readValue("{\"name\": \"p\", \"unknown\": 1}"));
		Assertions.assertThrows(JsonMappingException.class, () -> JSON.readerFor(RuleRequest.class)
			.withView(RestApiView.class).readValue("{\"name\": \"p\", \"unknown\": 1}"));
		Assertions.assertThrows(JsonMappingException.class, () -> JSON.readerFor(RuleTestRequest.class)
			.withView(RestApiView.class).readValue("{\"unknown\": 1}"));
		Assertions.assertThrows(JsonMappingException.class, () -> JSON.readerFor(SoftwareRuleRequest.class)
			.withView(RestApiView.class).readValue("{\"unknown\": 1}"));
		Assertions.assertThrows(JsonMappingException.class, () -> JSON.readerFor(HardwareRuleRequest.class)
			.withView(RestApiView.class).readValue("{\"unknown\": 1}"));
	}
}
