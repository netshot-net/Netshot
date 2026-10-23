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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import net.netshot.netshot.api.rest.v0.script.ScriptMapper;
import net.netshot.netshot.api.rest.v0.script.ScriptRequest;
import net.netshot.netshot.devicescript.UserDeviceScript.UserInputDefinition;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticBinaryResult;
import net.netshot.netshot.diagnostic.DiagnosticLongTextResult;
import net.netshot.netshot.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.diagnostic.DiagnosticView;
import net.netshot.netshot.diagnostic.JavaScriptDiagnostic;
import net.netshot.netshot.diagnostic.PythonDiagnostic;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.driver.DriverValueType;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.contract.DtoContract;
import net.netshot.netshot.userscript.DeviceJsScript;

/**
 * Checks that the diagnostic, diagnostic result and script DTOs are a faithful replacement of
 * the entities the REST API used to write (every kind of diagnostic and of result, in JSON and
 * XML), and that the requests are read like the former request classes did.
 */
@ScriptEngineTest
class DiagnosticDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final XmlMapper XML = XmlMapper.builder().build();

	@RegisterExtension
	private final DtoContract contract = new DtoContract(DiagnosticDtoTest.class);

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
			.changeDate(Instant.ofEpochMilli(1_600_000_200_000L)).build();
	}

	private static StaticDeviceGroup staticGroup() {
		return StaticDeviceGroup.builder().id(101).name("Static").folder("").hiddenFromReports(true)
			.changeDate(Instant.ofEpochMilli(1_600_000_300_000L)).build();
	}

	private static DiagnosticView view(Diagnostic diagnostic, DeviceGroup group) {
		return new DiagnosticView(diagnostic, group);
	}

	@Test
	@DisplayName("A simple diagnostic has the same JSON and XML representation as the entity, with either kind of group or none")
	void simpleDiagnostic() throws Exception {
		DynamicDeviceGroup dynamic = dynamicGroup();
		StaticDeviceGroup fixed = staticGroup();
		SimpleDiagnostic diagnostic = new SimpleDiagnostic(5, "Simple", true, dynamic.id(), AttributeType.TEXT, "CiscoIOS12",
			"enable", "show version", "(.*)", "$1");
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), view(diagnostic, dynamic)));
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), view(diagnostic, fixed)));
		diagnostic = new SimpleDiagnostic(5, "Simple", true, null, AttributeType.LONGTEXT, "NoSuchDriver",
			"enable", "show version", null, "$1");
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), view(diagnostic, null)));
		diagnostic = new SimpleDiagnostic(5, "Simple", true, null, AttributeType.LONGTEXT, "",
			"enable", "show version", null, "$1");
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), view(diagnostic, null)));
		String json = new String(json(DiagnosticMapper.toDto(TestDrivers.registry(), view(diagnostic, null))));
		Assertions.assertTrue(json.contains("\"type\":\"SimpleDiagnostic\""), json);
		Assertions.assertTrue(new String(xml(DiagnosticMapper.toDto(TestDrivers.registry(), view(diagnostic, null))))
			.startsWith("<SimpleDiagnostic type=\"SimpleDiagnostic\">"));
	}

	@Test
	@DisplayName("JavaScript and Python diagnostics have the same JSON and XML representation as the entities")
	void scriptDiagnostics() throws Exception {
		DynamicDeviceGroup dynamic = dynamicGroup();
		StaticDeviceGroup fixed = staticGroup();
		JavaScriptDiagnostic js = new JavaScriptDiagnostic(6, "JS", false, fixed.id(), AttributeType.NUMERIC, "function diagnose() {\n}");
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), view(js, fixed)));
		js = new JavaScriptDiagnostic(6, "JS", false, null, AttributeType.NUMERIC, null);
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), view(js, null)));
		PythonDiagnostic python = new PythonDiagnostic(7, "Python", true, dynamic.id(), AttributeType.BINARY, "def diagnose():\n  pass");
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), view(python, dynamic)));
		python = new PythonDiagnostic(7, "Python", true, null, AttributeType.BINARY, "def diagnose():\n  pass");
		this.assertRepresentation(DiagnosticMapper.toDto(TestDrivers.registry(), view(python, null)));
		Assertions.assertTrue(new String(json(DiagnosticMapper.toDto(TestDrivers.registry(), view(python, null)))).contains("\"type\":\"PythonDiagnostic\""));
		Assertions.assertTrue(new String(xml(DiagnosticMapper.toDto(TestDrivers.registry(), view(js, null)))).startsWith("<JavaScriptDiagnostic type=\"JavaScriptDiagnostic\">"));
	}

	@Test
	@DisplayName("A list of diagnostics has the same representation as a list of entities")
	void diagnosticList() throws Exception {
		DynamicDeviceGroup dynamic = dynamicGroup();
		StaticDeviceGroup fixed = staticGroup();
		SimpleDiagnostic simple = new SimpleDiagnostic(1, "Simple", true, dynamic.id(), AttributeType.TEXT, "CiscoIOS12", "enable", "show", null, null);
		PythonDiagnostic python = new PythonDiagnostic(2, "Python", true, null, AttributeType.BINARY, "x");
		JavaScriptDiagnostic js = new JavaScriptDiagnostic(3, "JS", true, fixed.id(), AttributeType.TEXT, "y");
		List<DiagnosticView> entities = new ArrayList<>(List.of(view(simple, dynamic), view(python, null), view(js, fixed)));
		this.assertRepresentation(new ArrayList<>(DiagnosticMapper.toDtos(TestDrivers.registry(), entities)));
	}

	@SuppressWarnings("unchecked")
	private static <R extends DiagnosticResult> R dated(R result) {
		return (R) result.withDates(Instant.ofEpochMilli(1_609_502_400_000L), Instant.ofEpochMilli(1_609_502_400_500L));
	}

	@Test
	@DisplayName("Each type of diagnostic result has the same JSON and XML representation as the entity")
	void results() throws Exception {
		PythonDiagnostic diagnostic = new PythonDiagnostic(9, "Diag", true, null, AttributeType.TEXT, "x");
		DiagnosticTextResult text = dated(DiagnosticTextResult.of(diagnostic, "some text"));
		DiagnosticTextResult noText = dated(DiagnosticTextResult.of(diagnostic, null));
		DiagnosticLongTextResult longText = dated(DiagnosticLongTextResult.of(diagnostic, "long\ntext"));
		DiagnosticNumericResult numeric = dated(DiagnosticNumericResult.of(diagnostic, 42.5));
		DiagnosticNumericResult noNumber = dated(DiagnosticNumericResult.of(diagnostic, null));
		DiagnosticBinaryResult binary = dated(DiagnosticBinaryResult.of(diagnostic, true));
		DiagnosticBinaryResult noAssumption = dated(DiagnosticBinaryResult.of(diagnostic, null));
		for (DiagnosticResult result : List.of(text, noText, longText, numeric, noNumber, binary, noAssumption)) {
			this.assertRepresentation(DiagnosticMapper.toDto(result));
		}
		List<DiagnosticResult> all = new ArrayList<>(List.of(text, longText, numeric, binary));
		this.assertRepresentation(new ArrayList<>(DiagnosticMapper.toResultDtos(all)));
		String json = new String(json(DiagnosticMapper.toDto(longText)));
		Assertions.assertTrue(json.contains("\"type\":\"LONGTEXT\"") && json.contains("\"diagnosticId\":9") && !json.contains("longText"), json);
	}

	private static DeviceJsScript script() {
		return script(null);
	}

	private static DeviceJsScript script(Map<String, UserInputDefinition> definitions) {
		return new DeviceJsScript(3, "Script", "Folder", "function run() {}", "CiscoIOS12", "tester", definitions);
	}

	@Test
	@DisplayName("A script has the same JSON and XML representation as the entity, with or without input definitions")
	void scripts() throws Exception {
		DeviceJsScript script = script();
		this.assertRepresentation(ScriptMapper.toDto(TestDrivers.registry(), script));
		script = script(new LinkedHashMap<>());
		this.assertRepresentation(ScriptMapper.toDto(TestDrivers.registry(), script));
		UserInputDefinition text = new UserInputDefinition("hostname");
		text.setDescription("The host");
		text.setChoices(List.of("a", "b"));
		text.setDefaultValue("a");
		UserInputDefinition flag = new UserInputDefinition("flag", DriverValueType.BOOLEAN, "Flag", null);
		Map<String, UserInputDefinition> map = new LinkedHashMap<>();
		map.put("hostname", text);
		map.put("flag", flag);
		script = script(map);
		this.assertRepresentation(ScriptMapper.toDto(TestDrivers.registry(), script));
		script = new DeviceJsScript(script.id(), script.name(), null, null, "NoSuchDriver", script.author(), script.userInputDefinitions());
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
