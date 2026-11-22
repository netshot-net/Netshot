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
package net.netshot.netshot.devicescript;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.netshot.netshot.devicescript.UserDeviceScript.UserInputDefinition;
import net.netshot.netshot.driver.DriverValueType;
import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeTaskContext;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@code Input} object of ad-hoc "run script on device"
 * scripts: text/boolean parameter declaration and value validation, including
 * a text input restricted by {@code choices} (see {@code driver-loader.js}'s
 * {@code validateRunScript}/{@code validateUserInputs}, called via
 * {@link UserDeviceScript}).
 */
@ScriptEngineTest
@DisplayName("Input of the device scripts")
class UserDeviceScriptTest {

	/** Any already-loaded driver works here - the script's JS context is borrowed from it, not executed against a real device. */
	private static final String DRIVER_NAME = "CiscoIOS12";

	private static final String SCRIPT_WITH_ALL_TYPES = """
		var Input = {
			hostname: {
				label: "Hostname",
				regExp: /^[a-z0-9-]+$/,
			},
			backupMode: {
				label: "Backup mode",
				choices: ["running-config", "startup-config", "both"],
				default: "running-config",
			},
			fullBackup: {
				type: "boolean",
				label: "Force full backup",
				default: false,
			},
			comment: {
				label: "Comment",
				optional: true,
			},
		};

		function run(cli, device, config) {
		}
		""";

	private UserDeviceScript buildScript(String code, Map<String, String> userInputValues) {
		TaskContext taskContext = new FakeTaskContext();
		UserDeviceScript script = new UserDeviceScript(TestDrivers.registry(), TestConnectors.local(), DRIVER_NAME, code, taskContext);
		script.setUserInputValues(userInputValues);
		return script;
	}

	@Test
	@DisplayName("Text, choices-restricted and boolean inputs are parsed with their type/choices/default")
	void parsesAllInputTypes() throws Exception {
		UserDeviceScript script = this.buildScript(SCRIPT_WITH_ALL_TYPES, new HashMap<>());
		Map<String, UserInputDefinition> definitions = script.extractInputDefinitions();

		UserInputDefinition hostname = definitions.get("hostname");
		Assertions.assertNotNull(hostname, "The 'hostname' input should exist");
		Assertions.assertEquals(DriverValueType.TEXT, hostname.getType());

		UserInputDefinition backupMode = definitions.get("backupMode");
		Assertions.assertNotNull(backupMode, "The 'backupMode' input should exist");
		Assertions.assertEquals(DriverValueType.TEXT, backupMode.getType());
		Assertions.assertEquals(
			List.of("running-config", "startup-config", "both"), backupMode.getChoices());
		Assertions.assertEquals("running-config", backupMode.getDefaultValue());

		UserInputDefinition fullBackup = definitions.get("fullBackup");
		Assertions.assertNotNull(fullBackup, "The 'fullBackup' input should exist");
		Assertions.assertEquals(DriverValueType.BOOLEAN, fullBackup.getType());
		Assertions.assertEquals("false", fullBackup.getDefaultValue());
	}

	@Test
	@DisplayName("Valid values for every type pass validation")
	void validValuesPassValidation() {
		Map<String, String> values = new HashMap<>();
		values.put("hostname", "router1");
		values.put("backupMode", "startup-config");
		values.put("fullBackup", "true");
		UserDeviceScript script = this.buildScript(SCRIPT_WITH_ALL_TYPES, values);
		Assertions.assertDoesNotThrow(script::validateUserInputs);
	}

	@Test
	@DisplayName("A text value not matching its regExp is rejected")
	void invalidTextValueIsRejected() {
		Map<String, String> values = new HashMap<>();
		values.put("hostname", "Not Valid!");
		values.put("backupMode", "running-config");
		values.put("fullBackup", "true");
		UserDeviceScript script = this.buildScript(SCRIPT_WITH_ALL_TYPES, values);
		Assertions.assertThrows(IllegalArgumentException.class, script::validateUserInputs);
	}

	@Test
	@DisplayName("A value outside the declared choices is rejected")
	void invalidChoiceValueIsRejected() {
		Map<String, String> values = new HashMap<>();
		values.put("hostname", "router1");
		values.put("backupMode", "not-a-choice");
		values.put("fullBackup", "true");
		UserDeviceScript script = this.buildScript(SCRIPT_WITH_ALL_TYPES, values);
		Assertions.assertThrows(IllegalArgumentException.class, script::validateUserInputs);
	}

	@Test
	@DisplayName("A boolean value other than \"true\"/\"false\" is rejected")
	void invalidBooleanValueIsRejected() {
		Map<String, String> values = new HashMap<>();
		values.put("hostname", "router1");
		values.put("backupMode", "running-config");
		values.put("fullBackup", "yes");
		UserDeviceScript script = this.buildScript(SCRIPT_WITH_ALL_TYPES, values);
		Assertions.assertThrows(IllegalArgumentException.class, script::validateUserInputs);
	}

	@Test
	@DisplayName("A missing required input with no declared default is rejected")
	void missingRequiredInputIsRejected() {
		Map<String, String> values = new HashMap<>();
		values.put("backupMode", "running-config");
		values.put("fullBackup", "true");
		// "hostname" is required and has no default.
		UserDeviceScript script = this.buildScript(SCRIPT_WITH_ALL_TYPES, values);
		Assertions.assertThrows(IllegalArgumentException.class, script::validateUserInputs);
	}

	@Test
	@DisplayName("A missing optional input with no value is simply left out")
	void missingOptionalInputIsAllowed() {
		Map<String, String> values = new HashMap<>();
		values.put("hostname", "router1");
		values.put("backupMode", "running-config");
		values.put("fullBackup", "true");
		// "comment" is optional and not provided.
		UserDeviceScript script = this.buildScript(SCRIPT_WITH_ALL_TYPES, values);
		Assertions.assertDoesNotThrow(script::validateUserInputs);
	}

	@Test
	@DisplayName("A script without Input has no input; the label defaults to the capitalized name, the description is kept")
	void definitionDefaults() throws Exception {
		Assertions.assertEquals(Map.of(), this.buildScript("function run(cli, device, config) {}", new HashMap<>()).extractInputDefinitions());

		Map<String, UserInputDefinition> definitions = this.buildScript("""
			var Input = { vlan: { description: "VLAN to add" } };
			function run(cli, device, config) {}
			""", new HashMap<>()).extractInputDefinitions();
		UserInputDefinition vlan = definitions.get("vlan");
		Assertions.assertEquals("Vlan", vlan.getLabel());
		Assertions.assertEquals("VLAN to add", vlan.getDescription());
		Assertions.assertEquals(DriverValueType.TEXT, vlan.getType());
		Assertions.assertNull(vlan.getDefaultValue());
	}

	@Test
	@DisplayName("An Input declared with const is found as well")
	void constInput() throws Exception {
		Map<String, UserInputDefinition> definitions = this.buildScript("""
			const Input = { vlan: { label: "VLAN" } };
			function run(cli, device, config) {}
			""", new HashMap<>()).extractInputDefinitions();
		Assertions.assertEquals(Set.of("vlan"), definitions.keySet());
	}

	@Test
	@DisplayName("An invalid script or input definition is rejected with an explicit message")
	void invalidDefinitions() {
		Map<String, String> cases = new LinkedHashMap<>();
		cases.put("var Input = {};", "'run' is not defined or is not a function.");
		cases.put("var Input = 5; function run() {}", "Input should be an object");
		cases.put("var Input = { 'host-name': {} }; function run() {}",
			"Invalid input name 'host-name': allowed characters are {a to z, A to Z, 0 to 9, _}.");
		cases.put("var Input = { a: { label: 5 } }; function run() {}", "The 'label' field in 'a' input definition should be a string.");
		cases.put("var Input = { a: { description: 5 } }; function run() {}",
			"The 'description' field in 'a' input definition should be a string.");
		cases.put("var Input = { a: { optional: 'yes' } }; function run() {}",
			"The 'optional' field in 'a' input definition should be a boolean.");
		cases.put("var Input = { a: { type: 'number' } }; function run() {}",
			"The 'type' field in 'a' input definition should be one of \"text\" or \"boolean\".");
		cases.put("var Input = { a: { type: 'boolean', choices: ['x'] } }; function run() {}", "The 'choices' field is not applicable to 'a' input.");
		cases.put("var Input = { a: { choices: [] } }; function run() {}",
			"The 'choices' field in 'a' input definition should be a non-empty array of strings.");
		cases.put("var Input = { a: { type: 'boolean', regExp: /x/ } }; function run() {}",
			"The 'regExp' field is not applicable to boolean input 'a'.");
		cases.put("var Input = { a: { regExp: 'x' } }; function run() {}", "The 'regExp' field in 'a' input definition should be a RegExp object.");
		cases.put("var Input = { a: { type: 'boolean', default: 'true' } }; function run() {}",
			"The 'default' field in 'a' input definition should be a boolean.");
		cases.put("var Input = { a: { default: 1 } }; function run() {}", "The 'default' field in 'a' input definition should be a string.");
		cases.put("var Input = { a: { choices: ['x', 'y'], default: 'z' } }; function run() {}",
			"The 'default' field in 'a' input definition should be one of 'choices'.");
		for (Map.Entry<String, String> entry : cases.entrySet()) {
			UserDeviceScript script = this.buildScript(entry.getKey(), new HashMap<>());
			IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class, script::extractInputDefinitions, entry.getKey());
			Assertions.assertEquals(entry.getValue(), error.getMessage(), entry.getKey());
		}
	}

}
