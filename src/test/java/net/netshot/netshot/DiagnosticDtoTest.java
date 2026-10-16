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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import net.netshot.netshot.api.rest.v0.diagnostic.DiagnosticMapper;
import net.netshot.netshot.api.rest.v0.diagnostic.DiagnosticRequest;
import net.netshot.netshot.api.rest.v0.script.ScriptMapper;
import net.netshot.netshot.api.rest.v0.script.ScriptRequest;
import net.netshot.netshot.devicescript.UserDeviceScript.UserInputDefinition;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticBinaryResult;
import net.netshot.netshot.diagnostic.DiagnosticLongTextResult;
import net.netshot.netshot.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.diagnostic.JavaScriptDiagnostic;
import net.netshot.netshot.diagnostic.PythonDiagnostic;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverValueType;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.rest.RestViews.RestApiView;
import net.netshot.netshot.userscript.DeviceJsScript;

/**
 * Checks that the diagnostic, diagnostic result and script DTOs are a faithful replacement of
 * the entities the REST API used to write (every kind of diagnostic and of result, in JSON and
 * XML), and that the requests are read like the former request classes did.
 */
class DiagnosticDtoTest {

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
		dynamic.setChangeDate(new Date(1_600_000_200_000L));
		return dynamic;
	}

	private static StaticDeviceGroup staticGroup() {
		StaticDeviceGroup fixed = new StaticDeviceGroup("Static");
		fixed.setId(101);
		fixed.setHiddenFromReports(true);
		fixed.setChangeDate(new Date(1_600_000_300_000L));
		return fixed;
	}

	@Test
	@DisplayName("A simple diagnostic has the same JSON and XML representation as the entity, with either kind of group or none")
	void simpleDiagnostic() throws Exception {
		SimpleDiagnostic diagnostic = new SimpleDiagnostic("Simple", true, dynamicGroup(), AttributeType.TEXT, "CiscoIOS12",
			"enable", "show version", "(.*)", "$1");
		diagnostic.setId(5);
		this.assertSameRepresentation(diagnostic, DiagnosticMapper.toDto(diagnostic));
		diagnostic.setTargetGroup(staticGroup());
		this.assertSameRepresentation(diagnostic, DiagnosticMapper.toDto(diagnostic));
		diagnostic.setTargetGroup(null);
		diagnostic.setDeviceDriver("NoSuchDriver");
		diagnostic.setModifierPattern(null);
		diagnostic.setResultType(AttributeType.LONGTEXT);
		this.assertSameRepresentation(diagnostic, DiagnosticMapper.toDto(diagnostic));
		diagnostic.setDeviceDriver("");
		this.assertSameRepresentation(diagnostic, DiagnosticMapper.toDto(diagnostic));
		String json = new String(json(DiagnosticMapper.toDto(diagnostic)));
		Assertions.assertTrue(json.contains("\"type\":\"SimpleDiagnostic\""), json);
		Assertions.assertTrue(new String(xml(DiagnosticMapper.toDto(diagnostic))).startsWith("<SimpleDiagnostic type=\"SimpleDiagnostic\">"));
	}

	@Test
	@DisplayName("JavaScript and Python diagnostics have the same JSON and XML representation as the entities")
	void scriptDiagnostics() throws Exception {
		JavaScriptDiagnostic js = new JavaScriptDiagnostic("JS", false, staticGroup(), AttributeType.NUMERIC, "function diagnose() {\n}");
		js.setId(6);
		this.assertSameRepresentation(js, DiagnosticMapper.toDto(js));
		js.setTargetGroup(null);
		js.setScript(null);
		this.assertSameRepresentation(js, DiagnosticMapper.toDto(js));
		PythonDiagnostic python = new PythonDiagnostic("Python", true, dynamicGroup(), AttributeType.BINARY, "def diagnose():\n  pass");
		python.setId(7);
		this.assertSameRepresentation(python, DiagnosticMapper.toDto(python));
		python.setTargetGroup(null);
		this.assertSameRepresentation(python, DiagnosticMapper.toDto(python));
		Assertions.assertTrue(new String(json(DiagnosticMapper.toDto(python))).contains("\"type\":\"PythonDiagnostic\""));
		Assertions.assertTrue(new String(xml(DiagnosticMapper.toDto(js))).startsWith("<JavaScriptDiagnostic type=\"JavaScriptDiagnostic\">"));
	}

	@Test
	@DisplayName("A list of diagnostics has the same representation as a list of entities")
	void diagnosticList() throws Exception {
		SimpleDiagnostic simple = new SimpleDiagnostic("Simple", true, dynamicGroup(), AttributeType.TEXT, "CiscoIOS12", "enable", "show", null, null);
		simple.setId(1);
		PythonDiagnostic python = new PythonDiagnostic("Python", true, null, AttributeType.BINARY, "x");
		python.setId(2);
		JavaScriptDiagnostic js = new JavaScriptDiagnostic("JS", true, staticGroup(), AttributeType.TEXT, "y");
		js.setId(3);
		List<Diagnostic> entities = new ArrayList<>(List.of(simple, python, js));
		this.assertSameRepresentation(entities, new ArrayList<>(DiagnosticMapper.toDtos(entities)));
	}

	private static void dates(DiagnosticResult result) {
		result.setCreationDate(new Date(1_609_502_400_000L));
		result.setLastCheckDate(new Date(1_609_502_400_500L));
	}

	@Test
	@DisplayName("Each type of diagnostic result has the same JSON and XML representation as the entity")
	void results() throws Exception {
		PythonDiagnostic diagnostic = new PythonDiagnostic("Diag", true, null, AttributeType.TEXT, "x");
		diagnostic.setId(9);
		DiagnosticTextResult text = new DiagnosticTextResult(null, diagnostic, "some text");
		dates(text);
		DiagnosticTextResult noText = new DiagnosticTextResult(null, diagnostic, null);
		dates(noText);
		DiagnosticLongTextResult longText = new DiagnosticLongTextResult(null, diagnostic, "long\ntext");
		dates(longText);
		DiagnosticNumericResult numeric = new DiagnosticNumericResult(null, diagnostic, 42.5);
		dates(numeric);
		DiagnosticNumericResult noNumber = new DiagnosticNumericResult(null, diagnostic, 1.0);
		noNumber.setNumber(null);
		dates(noNumber);
		DiagnosticBinaryResult binary = new DiagnosticBinaryResult(null, diagnostic, true);
		dates(binary);
		DiagnosticBinaryResult noAssumption = new DiagnosticBinaryResult(null, diagnostic, false);
		noAssumption.setAssumption(null);
		dates(noAssumption);
		for (DiagnosticResult result : List.of(text, noText, longText, numeric, noNumber, binary, noAssumption)) {
			this.assertSameRepresentation(result, DiagnosticMapper.toDto(result));
		}
		List<DiagnosticResult> all = new ArrayList<>(List.of(text, longText, numeric, binary));
		this.assertSameRepresentation(all, new ArrayList<>(DiagnosticMapper.toResultDtos(all)));
		String json = new String(json(DiagnosticMapper.toDto(longText)));
		Assertions.assertTrue(json.contains("\"type\":\"LONGTEXT\"") && json.contains("\"diagnosticId\":9") && !json.contains("longText"), json);
	}

	private static DeviceJsScript script() throws Exception {
		DeviceJsScript script = new DeviceJsScript("Script", "CiscoIOS12", "function run() {}", "tester");
		script.setId(3);
		script.setFolder("Folder");
		return script;
	}

	private static void definitions(DeviceJsScript script, Map<String, UserInputDefinition> definitions) throws Exception {
		Field field = DeviceJsScript.class.getDeclaredField("userInputDefinitions");
		field.setAccessible(true);
		field.set(script, definitions);
	}

	@Test
	@DisplayName("A script has the same JSON and XML representation as the entity, with or without input definitions")
	void scripts() throws Exception {
		DeviceJsScript script = script();
		this.assertSameRepresentation(script, ScriptMapper.toDto(script));
		definitions(script, new LinkedHashMap<>());
		this.assertSameRepresentation(script, ScriptMapper.toDto(script));
		UserInputDefinition text = new UserInputDefinition("hostname");
		text.setDescription("The host");
		text.setChoices(List.of("a", "b"));
		text.setDefaultValue("a");
		UserInputDefinition flag = new UserInputDefinition("flag", DriverValueType.BOOLEAN, "Flag", null);
		Map<String, UserInputDefinition> map = new LinkedHashMap<>();
		map.put("hostname", text);
		map.put("flag", flag);
		definitions(script, map);
		this.assertSameRepresentation(script, ScriptMapper.toDto(script));
		script.setScript(null);
		script.setDeviceDriver("NoSuchDriver");
		script.setFolder(null);
		this.assertSameRepresentation(script, ScriptMapper.toDto(script));
		DeviceJsScript other = script();
		List<DeviceJsScript> list = new ArrayList<>(List.of(script, other));
		this.assertSameRepresentation(list, new ArrayList<>(ScriptMapper.toDtos(list)));
	}

	private static DiagnosticRequest diagnosticRequest(String body) throws Exception {
		return JSON.readerFor(DiagnosticRequest.class).withView(RestApiView.class).readValue(body);
	}

	@Test
	@DisplayName("A diagnostic request has the defaults of the former request class and refuses unknown properties")
	void diagnosticRequest() throws Exception {
		DiagnosticRequest empty = diagnosticRequest("{}");
		Assertions.assertEquals("", empty.name(), "absent name");
		Assertions.assertEquals(0, empty.targetGroup());
		Assertions.assertFalse(empty.enabled());
		Assertions.assertNull(empty.type());
		Assertions.assertNull(empty.resultType());
		Assertions.assertNull(diagnosticRequest("{\"name\": null}").name(), "explicit null name");
		DiagnosticRequest request = diagnosticRequest("""
			{"id": 4, "name": " n ", "targetGroup": -1, "enabled": true, "resultType": "TEXT", "type": "SimpleDiagnostic",
			 "script": "s", "deviceDriver": "d", "cliMode": "m", "command": "c", "modifierPattern": "p", "modifierReplacement": "r"}""");
		Assertions.assertEquals(" n ", request.name());
		Assertions.assertEquals(-1, request.targetGroup());
		Assertions.assertTrue(request.enabled());
		Assertions.assertEquals("SimpleDiagnostic", request.type());
		Assertions.assertEquals("r", DiagnosticMapper.toDefinition(request).modifierReplacement());
		Assertions.assertEquals("m", DiagnosticMapper.toDefinition(request).cliMode());
		Assertions.assertThrows(JsonMappingException.class, () -> diagnosticRequest("{\"deviceDriverDescription\": \"x\"}"));
	}

	private static ScriptRequest scriptRequest(String body) throws Exception {
		return JSON.readerFor(ScriptRequest.class).withView(RestApiView.class).readValue(body);
	}

	private static DeviceJsScript legacyScriptRequest(String body) throws Exception {
		return JSON.readerFor(DeviceJsScript.class).withView(RestApiView.class).readValue(body);
	}

	@Test
	@DisplayName("A script request is read like the script entity it replaces, quirks included")
	void scriptRequest() throws Exception {
		for (String body : List.of(
			"{}",
			"{\"name\": \"n\", \"deviceDriver\": \"CiscoIOS12\", \"script\": \"s\"}",
			"{\"id\": 4, \"name\": \"n\", \"deviceDriver\": \"d\", \"script\": \"s\", \"author\": \"a\", \"folder\": \"f\"}",
			"{\"folder\": null}",
			"{\"userInputDefinitions\": null}",
			"{\"name\": \"n\", \"folder\": 12}",
			"{\"userInputDefinitions\": {}}",
			"{\"userInputDefinitions\": {\"a\": null}}",
			"{\"userInputDefinitions\": {\"a\": \"x\"}}")) {
			DeviceJsScript legacy = legacyScriptRequest(body);
			ScriptRequest request = scriptRequest(body);
			Assertions.assertEquals(legacy.getName(), request.name(), body);
			Assertions.assertEquals(legacy.getScript(), request.script(), body);
			Assertions.assertEquals(legacy.getDeviceDriver(), request.deviceDriver(), body);
			Assertions.assertEquals(legacy.getAuthor(), request.author(), body);
			Assertions.assertEquals(legacy.getFolder(), request.folder(), body);
			Assertions.assertEquals(legacy.getUserInputDefinitions() == null, request.userInputDefinitions() == null, body);
			if (legacy.getUserInputDefinitions() != null) {
				Assertions.assertEquals(legacy.getUserInputDefinitions().keySet(), request.userInputDefinitions().keySet(), body);
			}
		}
		Assertions.assertEquals("", scriptRequest("{}").folder());
		Assertions.assertNull(scriptRequest("{\"folder\": null}").folder());
		// What the entity could not read, the request refuses as well
		for (String body : List.of(
			"{\"userInputDefinitions\": {\"a\": {\"name\": \"a\"}}}",
			"{\"userInputDefinitions\": []}",
			"{\"unknown\": 1}",
			"{\"realDeviceType\": \"Cisco IOS\"}",
			"{\"id\": \"x\"}")) {
			Assertions.assertThrows(JsonMappingException.class, () -> legacyScriptRequest(body), "legacy " + body);
			Assertions.assertThrows(JsonMappingException.class, () -> scriptRequest(body), body);
		}
		Assertions.assertEquals("x", ScriptMapper.toCreation(scriptRequest("{\"author\": \"x\"}"), null).author(), "posted author kept");
		Assertions.assertEquals("u", ScriptMapper.toCreation(scriptRequest("{\"author\": \"x\"}"), "u").author(), "user wins");
	}
}
