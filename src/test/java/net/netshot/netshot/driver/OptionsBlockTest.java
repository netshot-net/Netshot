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
package net.netshot.netshot.driver;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.access.AccessManager;
import net.netshot.netshot.devicescript.SnapshotDeviceScript;
import net.netshot.netshot.devicescript.helper.JsDeviceHelper;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.DeviceDriver.Location;
import net.netshot.netshot.driver.DeviceDriver.LocationType;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeTaskContext;

/**
 * Tests for the {@code Options} descriptor block: per-device,
 * user-configurable settings a driver declares (as opposed to the
 * driver-collected {@code Config}/{@code Device} attributes).
 */
@ScriptEngineTest
@DisplayName("Options block test")
public class OptionsBlockTest {

	private static final String OPTIONS_DRIVER_JS = """
		var Info = {
			name: "OptionsTestDriver",
			author: "test",
			description: "test",
			version: "1.0"
		};

		var Config = {};
		var Device = {};
		var CLI = { ssh: {} };

		var Options = {
			fullBackup: {
				type: "Boolean",
				title: "Force full backup",
				default: true,
			},
			backupMode: {
				type: "Text",
				title: "Backup mode",
				choices: ["running-config", "startup-config", "both"],
				default: "running-config",
			},
			comment: {
				type: "Text",
				title: "Comment",
			},
		};

		function snapshot(client, device, config) {
		}
		""";

	private static final String NO_OPTIONS_DRIVER_JS = """
		var Info = {
			name: "NoOptionsTestDriver",
			author: "test",
			description: "test",
			version: "1.0"
		};

		var Config = {};
		var Device = {};
		var CLI = { ssh: {} };

		function snapshot(client, device, config) {
		}
		""";

	private static String badOptionsDriverJs(String optionsBlock) {
		return """
			var Info = {
				name: "BadOptionsTestDriver",
				author: "test",
				description: "test",
				version: "1.0"
			};

			var Config = {};
			var Device = {};
			var CLI = { ssh: {} };

			var Options = %s;

			function snapshot(client, device, config) {
			}
			""".formatted(optionsBlock);
	}

	private DeviceDriver buildDriver(String js, String name) throws Exception {
		return new DeviceDriver(new StringReader(js), name,
			new Location(LocationType.EMBEDDED, name));
	}

	@Test
	@DisplayName("Text, choices-restricted and Boolean options are parsed with their title/choices/default")
	void parsesAllOptionTypes() throws Exception {
		DeviceDriver driver = this.buildDriver(OPTIONS_DRIVER_JS, "OptionsTestDriver.js");

		OptionDefinition fullBackup = driver.getOptions().get("fullBackup");
		Assertions.assertNotNull(fullBackup, "The 'fullBackup' option should exist");
		Assertions.assertEquals(DriverValueType.BOOLEAN, fullBackup.getType());
		Assertions.assertEquals("Force full backup", fullBackup.getTitle());
		Assertions.assertEquals(Boolean.TRUE, fullBackup.getDefaultValue(),
			"Boolean option default should be a real boolean, not a string");

		OptionDefinition backupMode = driver.getOptions().get("backupMode");
		Assertions.assertNotNull(backupMode, "The 'backupMode' option should exist");
		Assertions.assertEquals(DriverValueType.TEXT, backupMode.getType());
		Assertions.assertEquals(
			List.of("running-config", "startup-config", "both"), backupMode.getChoices());
		Assertions.assertEquals("running-config", backupMode.getDefaultValue());

		OptionDefinition comment = driver.getOptions().get("comment");
		Assertions.assertNotNull(comment, "The 'comment' option should exist");
		Assertions.assertEquals(DriverValueType.TEXT, comment.getType());
		Assertions.assertNull(comment.getDefaultValue(), "No default was declared for 'comment'");
	}

	@Test
	@DisplayName("A driver with no Options block simply has no declared options")
	void missingOptionsBlockYieldsNoOptions() throws Exception {
		DeviceDriver driver = this.buildDriver(NO_OPTIONS_DRIVER_JS, "NoOptionsTestDriver.js");
		Assertions.assertTrue(driver.getOptions().isEmpty());
	}

	@Test
	@DisplayName("A Boolean option with 'choices' is rejected")
	void booleanOptionWithChoicesIsRejected() {
		String js = badOptionsDriverJs(
			"{ mode: { type: \"Boolean\", title: \"Mode\", choices: [\"a\", \"b\"] } }");
		Assertions.assertThrows(IllegalArgumentException.class,
			() -> this.buildDriver(js, "BadOptionsTestDriver.js"));
	}

	@Test
	@DisplayName("A Text option whose default is not one of its choices is rejected")
	void textOptionWithInvalidDefaultIsRejected() {
		String js = badOptionsDriverJs(
			"{ mode: { type: \"Text\", title: \"Mode\", choices: [\"a\", \"b\"], default: \"c\" } }");
		Assertions.assertThrows(IllegalArgumentException.class,
			() -> this.buildDriver(js, "BadOptionsTestDriver.js"));
	}

	@Test
	@DisplayName("An option with an unknown type is rejected")
	void unknownOptionTypeIsRejected() {
		String js = badOptionsDriverJs(
			"{ mode: { type: \"Enum\", title: \"Mode\" } }");
		Assertions.assertThrows(IllegalArgumentException.class,
			() -> this.buildDriver(js, "BadOptionsTestDriver.js"));
	}

	@Test
	@DisplayName("An option with an invalid title is rejected")
	void invalidOptionTitleIsRejected() {
		String js = badOptionsDriverJs(
			"{ mode: { type: \"Text\", title: \"!\" } }");
		Assertions.assertThrows(IllegalArgumentException.class,
			() -> this.buildDriver(js, "BadOptionsTestDriver.js"));
	}

	/**
	 * Registers a driver directly into {@link DeviceDriver}'s private static
	 * registry (there's no public API for this - drivers are normally only
	 * discovered from disk by {@link net.netshot.netshot.driver.DriverRegistry#refresh()}), so that
	 * {@link net.netshot.netshot.driver.DriverRegistry#forDevice(net.netshot.netshot.driver.DrivenDevice)} can resolve it by name like any other
	 * driver. Callers must remove it again with {@link #unregisterDriver}.
	 * @param driver the driver to register
	 */
	private void registerDriver(DeviceDriver driver) throws Exception {
		TestDrivers.registry().getDrivers().put(driver.getName(), driver);
	}

	private void unregisterDriver(String name) throws Exception {
		TestDrivers.registry().getDrivers().remove(name);
	}

	@Test
	@DisplayName("A Boolean option is exposed at runtime as a real JS boolean, not the string \"true\"/\"false\"")
	void booleanOptionIsExposedAsRealBoolean() throws Exception {
		// A non-empty string is truthy in JS, so if this leaked a stringified
		// "false", a driver's natural `if (device.options.x)` check would
		// always be true - this locks in that Device#getOptions() (and the
		// JSON column behind it) stores real typed values, not strings.
		DeviceDriver driver = this.buildDriver(OPTIONS_DRIVER_JS, "OptionsTestDriver.js");
		this.registerDriver(driver);
		try {
			Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
			Device.DeviceBuilder deviceBuilder = Device.create("OptionsTestDriver", null, domain, "test").toBuilder();
			DeviceDraft device = new DeviceDraft(deviceBuilder.build());

			JsDeviceHelper helper = new JsDeviceHelper(TestDrivers.registry(), null, device, null, new FakeTaskContext(), false);
			ProxyObject options = (ProxyObject) helper.getOptions();
			Assertions.assertEquals(Boolean.TRUE, options.getMember("fullBackup"),
				"Default Boolean option value should be a real boolean, not a string");
			Assertions.assertEquals("running-config", options.getMember("backupMode"),
				"Text option value should remain a plain string");

			DeviceDraft updated = new DeviceDraft(deviceBuilder.options(Map.of("fullBackup", Boolean.FALSE)).build());
			helper = new JsDeviceHelper(TestDrivers.registry(), null, updated, null, new FakeTaskContext(), false);
			ProxyObject updatedOptions = (ProxyObject) helper.getOptions();
			Assertions.assertEquals(Boolean.FALSE, updatedOptions.getMember("fullBackup"),
				"An explicitly stored false value should be exposed as boolean false");
		}
		finally {
			this.unregisterDriver("OptionsTestDriver");
		}
	}

	@Test
	@DisplayName("A snapshot in full-debug mode dumps the resolved option values to the debug log")
	void snapshotDumpsOptionValuesInDebugLog() throws Exception {
		DeviceDriver driver = this.buildDriver(OPTIONS_DRIVER_JS, "OptionsTestDriver.js");
		this.registerDriver(driver);
		try {
			Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
			Device.DeviceBuilder deviceBuilder = Device.create("OptionsTestDriver", null, domain, "test").toBuilder();
			DeviceDraft device = new DeviceDraft(deviceBuilder.build());
			FakeTaskContext taskContext = new FakeTaskContext();
			SnapshotDeviceScript script = new SnapshotDeviceScript(TestDrivers.registry(), TestConnectors.local(), null, taskContext);
			AccessManager accessManager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, taskContext, null);
			Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", DeviceDraft.class, AccessManager.class);
			runMethod.setAccessible(true);
			runMethod.invoke(script, device, accessManager);

			String log = taskContext.getLog();
			Assertions.assertTrue(log.contains("options") && log.contains("fullBackup=true"),
				"Debug log should contain the resolved option values (with defaults applied): " + log);
		}
		finally {
			this.unregisterDriver("OptionsTestDriver");
		}
	}

}
