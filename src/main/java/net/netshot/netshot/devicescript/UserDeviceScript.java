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
package net.netshot.netshot.devicescript;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.access.AccessManager;
import net.netshot.netshot.access.InvalidCredentialsException;
import net.netshot.netshot.devicescript.helper.JsCliHelper;
import net.netshot.netshot.devicescript.helper.JsClientFactory;
import net.netshot.netshot.devicescript.helper.JsDeviceHelper;
import net.netshot.netshot.devicescript.helper.JsDeviceScriptOptions;
import net.netshot.netshot.devicescript.helper.JsSnmpHelper;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.driver.DriverValueType;
import net.netshot.netshot.inventory.device.Device.MissingDeviceDriverException;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.platform.script.JsUtils;
import net.netshot.netshot.vault.SecretResolver;

/**
 * A JavaScript-based script to execute on a device.
 * @author sylvain.cadilhac
 *
 */
@Slf4j
public class UserDeviceScript extends DeviceScript {

	/**
	 * Definition of user input data.
	 */
	public static class UserInputDefinition {
		@Getter
		@Setter
		private String name;

		@Getter
		@Setter
		private DriverValueType type;

		@Getter
		@Setter
		private String label;

		@Getter
		@Setter
		private String description;

		/** Valid choices restricting a TEXT input's value; when set, the value must be one of these. */
		@Getter
		@Setter
		private List<String> choices;

		/** Default value, as a raw string (e.g. "true"/"false" for BOOLEAN). */
		@Getter
		@Setter
		private String defaultValue;

		public UserInputDefinition(String name) {
			this.name = name;
			this.label = StringUtils.capitalize(name);
			this.type = DriverValueType.TEXT;
		}

		public UserInputDefinition(String name, DriverValueType type, String label, String description) {
			this.name = name;
			this.label = label;
			this.description = description;
			this.type = type;
		}
	}


	@Getter
	private String driverName;

	// The JavaScript code to execute
	@Getter
	private String code;

	@Getter
	@Setter
	private Map<String, String> userInputValues;

	/**
	 * Instantiates a JS-based script.
	 * @param driverRegistry = The loaded device drivers
	 * @param secrets resolves the secrets of the credentials (local or Vault-backed)
	 * @param driverName = The name of the driver
	 * @param code = The JavaScript code
	 * @param logger = The task context
	 */
	public UserDeviceScript(DriverRegistry driverRegistry, SecretResolver secrets, String driverName, String code, TaskContext logger) {
		super(driverRegistry, secrets, logger);
		this.driverName = driverName;
		this.code = code;
	}

	@Override
	protected void run(DeviceDraft device, AccessManager accessManager)
		throws InvalidCredentialsException, IOException, UnsupportedOperationException, MissingDeviceDriverException {
		DeviceDriver driver = this.driverRegistry.forDevice(device);
		JsCliHelper jsCliHelper = new JsCliHelper(accessManager, driver.getDefaultCliAccessDefinitions(), true, this.taskContext);
		JsSnmpHelper jsSnmpHelper = new JsSnmpHelper(accessManager, driver.getDefaultSnmpAccessDefinitions(), true, this.taskContext);
		JsClientFactory clientFactory = new JsClientFactory(accessManager, driver);
		try (Context context = driver.getContext()) {
			driver.loadCode(context);
			context.eval("js", code);
			JsDeviceScriptOptions options = new JsDeviceScriptOptions(jsCliHelper, jsSnmpHelper, this.taskContext);
			options.setClientFactory(clientFactory);
			clientFactory.setOptions(options);
			options.setDeviceHelper(new JsDeviceHelper(this.driverRegistry, device, jsCliHelper, this.taskContext, false));
			options.setUserInputs(this.userInputValues);
			context
				.getBindings("js")
				.getMember("_connect")
				.execute("run", options);
		}
		catch (PolyglotException e) {
			log.error("Error while running script using driver {}.", driver.getName(), e);
			this.taskContext.error("Error while running script  using driver {}: '{}'.",
				driver.getName(), JsUtils.jsErrorToMessage(e));
			if (e.getMessage().contains("Authentication failed")) {
				throw new InvalidCredentialsException("Authentication failed");
			}
			else {
				throw e;
			}
		}
		catch (UnsupportedOperationException e) {
			log.error("No such method while using driver {}.", driver.getName(), e);
			this.taskContext.error("No such method while using driver {} to execute script: '{}'.",
				driver.getName(), e.getMessage());
			throw e;
		}
	}

	public void validateUserInputs() throws IllegalArgumentException {
		final DeviceDriver driver = this.driverRegistry.getDriverByName(this.driverName);
		try (Context context = driver.getContext()) {
			driver.loadCode(context);
			context.eval("js", this.code);
			JsDeviceScriptOptions options = new JsDeviceScriptOptions(null, null, null);
			options.setUserInputs(this.userInputValues);
			context
				.getBindings("js")
				.getMember("_validate")
				.execute("runInputs", options);
		}
		catch (PolyglotException e) {
			log.error("Error while validating user inputs.", e);
			throw new IllegalArgumentException(e.getMessage());
		}
		catch (IOException e) {
			log.error("Error while validating user inputs.", e);
			throw new IllegalArgumentException("Error while validating user inputs", e);
		}

	}

	public Map<String, UserInputDefinition> extractInputDefinitions() throws IllegalArgumentException {
		final Map<String, UserInputDefinition> definitions = new HashMap<>();
		final DeviceDriver driver = this.driverRegistry.getDriverByName(this.driverName);
		if (driver == null) {
			return definitions;
		}
		try (Context context = driver.getContext()) {
			driver.loadCode(context);
			context.eval("js", this.code);
			JsDeviceScriptOptions options = new JsDeviceScriptOptions(null, null, null);
			options.setUserInputs(this.userInputValues);
			context
				.getBindings("js")
				.getMember("_validate")
				.execute("runScript", options);

			final Value input = context.getBindings("js").getMember("Input");
			if (input == null) {
				log.debug("No 'Input' object was found in the script");
				return definitions;
			}
			if (!input.hasMembers()) {
				throw new IllegalArgumentException("Invalid 'Input' object in the script");
			}
			for (String name : input.getMemberKeys()) {
				final Value member = input.getMember(name);
				final UserInputDefinition definition = new UserInputDefinition(name);
				if (member == null || !member.hasMembers()) {
					throw new IllegalArgumentException(String.format("Invalid input definition '%s'", name));
				}
				final Value descriptionValue = member.getMember("description");
				if (descriptionValue != null) {
					if (!descriptionValue.isString()) {
						throw new IllegalArgumentException(String.format("Invalid 'description' type in definition of '%s'", name));
					}
					definition.setDescription(descriptionValue.asString());
				}
				final Value labelValue = member.getMember("label");
				if (labelValue != null) {
					if (!labelValue.isString()) {
						throw new IllegalArgumentException(String.format("Invalid 'label' type in definition of '%s'", name));
					}
					definition.setLabel(labelValue.asString());
				}
				final Value typeValue = member.getMember("type");
				if (typeValue != null) {
					if (!typeValue.isString()) {
						throw new IllegalArgumentException(String.format("Invalid 'type' type in definition of '%s'", name));
					}
					switch (typeValue.asString()) {
						case "text":
							definition.setType(DriverValueType.TEXT);
							break;
						case "boolean":
							definition.setType(DriverValueType.BOOLEAN);
							break;
						default:
							throw new IllegalArgumentException(String.format("Invalid 'type' value in definition of '%s'", name));
					}
				}
				final Value choicesValue = member.getMember("choices");
				if (choicesValue != null) {
					if (!choicesValue.hasArrayElements()) {
						throw new IllegalArgumentException(String.format("Invalid 'choices' type in definition of '%s'", name));
					}
					final List<String> choices = new ArrayList<>();
					for (long i = 0; i < choicesValue.getArraySize(); i++) {
						choices.add(choicesValue.getArrayElement(i).asString());
					}
					definition.setChoices(choices);
				}
				final Value defaultValue = member.getMember("default");
				if (defaultValue != null) {
					definition.setDefaultValue(defaultValue.isString() ? defaultValue.asString() : defaultValue.toString());
				}
				definitions.put(name, definition);
			}
		}
		catch (PolyglotException e) {
			log.error("Error while validating script.", e);
			throw new IllegalArgumentException(e.getMessage());
		}
		catch (IOException e) {
			log.error("Error while validating script.", e);
			throw new IllegalArgumentException("Error while validating script", e);
		}
		return definitions;
	}
}
