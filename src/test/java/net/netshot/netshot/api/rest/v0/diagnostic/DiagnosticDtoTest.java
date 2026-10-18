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
package net.netshot.netshot.api.rest.v0.diagnostic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import net.netshot.netshot.DtoContract;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.TestDrivers;
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
import net.netshot.netshot.driver.DriverValueType;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.userscript.DeviceJsScript;

/**
 * Checks that the diagnostic, diagnostic result and script DTOs are a faithful replacement of
 * the entities the REST API used to write (every kind of diagnostic and of result, in JSON and
 * XML), and that the requests are read like the former request classes did.
 */
class DiagnosticDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final XmlMapper XML = XmlMapper.builder().build();

	@RegisterExtension
	private final DtoContract contract = new DtoContract(DiagnosticDtoTest.class);

	@BeforeAll
	static void loadDrivers() throws Exception {
		Properties config = new Properties();
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "WARN");
		TestConfig.init(config);
		TestDrivers.reload();
	}

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
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), diagnostic));
		diagnostic.setTargetGroup(staticGroup());
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), diagnostic));
		diagnostic.setTargetGroup(null);
		diagnostic.setDeviceDriver("NoSuchDriver");
		diagnostic.setModifierPattern(null);
		diagnostic.setResultType(AttributeType.LONGTEXT);
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), diagnostic));
		diagnostic.setDeviceDriver("");
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), diagnostic));
		String json = new String(json(DiagnosticMapper.toDto(TestDrivers.registry(), diagnostic)));
		Assertions.assertTrue(json.contains("\"type\":\"SimpleDiagnostic\""), json);
		Assertions.assertTrue(new String(xml(DiagnosticMapper.toDto(TestDrivers.registry(), diagnostic))).startsWith("<SimpleDiagnostic type=\"SimpleDiagnostic\">"));
	}

	@Test
	@DisplayName("JavaScript and Python diagnostics have the same JSON and XML representation as the entities")
	void scriptDiagnostics() throws Exception {
		JavaScriptDiagnostic js = new JavaScriptDiagnostic("JS", false, staticGroup(), AttributeType.NUMERIC, "function diagnose() {\n}");
		js.setId(6);
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), js));
		js.setTargetGroup(null);
		js.setScript(null);
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), js));
		PythonDiagnostic python = new PythonDiagnostic("Python", true, dynamicGroup(), AttributeType.BINARY, "def diagnose():\n  pass");
		python.setId(7);
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), python));
		python.setTargetGroup(null);
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), python));
		Assertions.assertTrue(new String(json(DiagnosticMapper.toDto(TestDrivers.registry(), python))).contains("\"type\":\"PythonDiagnostic\""));
		Assertions.assertTrue(new String(xml(DiagnosticMapper.toDto(TestDrivers.registry(), js))).startsWith("<JavaScriptDiagnostic type=\"JavaScriptDiagnostic\">"));
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
		this.assertRepresentation(new ArrayList<>(DiagnosticMapper.toDtos(TestDrivers.registry(), entities)));
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
			this.assertRepresentation(DiagnosticMapper.toDto(result));
		}
		List<DiagnosticResult> all = new ArrayList<>(List.of(text, longText, numeric, binary));
		this.assertRepresentation(new ArrayList<>(DiagnosticMapper.toResultDtos(all)));
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
		this.assertRepresentation(ScriptMapper.toDto(TestDrivers.registry(), script));
		definitions(script, new LinkedHashMap<>());
		this.assertRepresentation(ScriptMapper.toDto(TestDrivers.registry(), script));
		UserInputDefinition text = new UserInputDefinition("hostname");
		text.setDescription("The host");
		text.setChoices(List.of("a", "b"));
		text.setDefaultValue("a");
		UserInputDefinition flag = new UserInputDefinition("flag", DriverValueType.BOOLEAN, "Flag", null);
		Map<String, UserInputDefinition> map = new LinkedHashMap<>();
		map.put("hostname", text);
		map.put("flag", flag);
		definitions(script, map);
		this.assertRepresentation(ScriptMapper.toDto(TestDrivers.registry(), script));
		script.setScript(null);
		script.setDeviceDriver("NoSuchDriver");
		script.setFolder(null);
		this.assertRepresentation(ScriptMapper.toDto(TestDrivers.registry(), script));
		DeviceJsScript other = script();
		List<DeviceJsScript> list = new ArrayList<>(List.of(script, other));
		this.assertRepresentation(new ArrayList<>(ScriptMapper.toDtos(TestDrivers.registry(), list)));
	}

	private static DiagnosticRequest diagnosticRequest(String body) throws Exception {
		return JSON.readerFor(DiagnosticRequest.class).readValue(body);
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
		return JSON.readerFor(ScriptRequest.class).readValue(body);
	}

	@Test
	@DisplayName("A script request is read like the script entity it replaces, quirks included; posted input definitions are ignored")
	void scriptRequest() throws Exception {
		int index = 0;
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
			index++;
			ScriptRequest request = scriptRequest(body);
			Map<String, Object> read = new TreeMap<>();
			read.put("name", request.name());
			read.put("script", request.script());
			read.put("deviceDriver", request.deviceDriver());
			read.put("author", request.author());
			read.put("folder", request.folder());
			this.contract.checkText("script-request-" + index, DtoContract.render(request, read));
		}
		Assertions.assertEquals("", scriptRequest("{}").folder());
		Assertions.assertNull(scriptRequest("{\"folder\": null}").folder());
		// The input definitions of a script, as the API returns them, can be posted back: they are ignored
		for (String body : List.of(
			"{\"userInputDefinitions\": {\"a\": {\"name\": \"a\", \"type\": \"TEXT\"}}}",
			"{\"userInputDefinitions\": []}")) {
			Assertions.assertEquals(scriptRequest("{}"), scriptRequest(body), body);
		}
		// What the entity could not read, the request refuses as well
		for (String body : List.of(
			"{\"unknown\": 1}",
			"{\"realDeviceType\": \"Cisco IOS\"}",
			"{\"id\": \"x\"}")) {
			Assertions.assertThrows(JsonMappingException.class, () -> scriptRequest(body), body);
		}
		Assertions.assertEquals("x", ScriptMapper.toCreation(scriptRequest("{\"author\": \"x\"}"), null).author(), "posted author kept");
		Assertions.assertEquals("u", ScriptMapper.toCreation(scriptRequest("{\"author\": \"x\"}"), "u").author(), "user wins");
	}
}
