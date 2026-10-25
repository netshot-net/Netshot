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
package net.netshot.netshot.userscript;

import java.util.Map;

import org.slf4j.event.Level;

import net.netshot.netshot.devicescript.UserDeviceScript;
import net.netshot.netshot.devicescript.UserDeviceScript.UserInputDefinition;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.vault.SecretResolver;

/**
 * A JavaScript script which users run on devices.
 *
 * @param id the ID (0 for a script not stored yet)
 * @param name the name
 * @param folder the folder
 * @param script the content of the script
 * @param deviceDriver the name of the driver of the devices the script is for
 * @param author the author
 * @param userInputDefinitions the inputs the script asks for (null if not extracted from the script)
 */
public record DeviceJsScript(long id, String name, String folder, String script, String deviceDriver, String author,
	Map<String, UserInputDefinition> userInputDefinitions) {

	/**
	 * Builds a script, not stored yet, in the root folder.
	 *
	 * @param name the name
	 * @param deviceDriver the name of the driver
	 * @param script the content of the script
	 * @param author the author
	 * @return the script
	 */
	public static DeviceJsScript of(String name, String deviceDriver, String script, String author) {
		return new DeviceJsScript(0, name, "", script, deviceDriver, author, null);
	}

	/**
	 * Gets a copy of the script with another ID.
	 *
	 * @param newId the ID
	 * @return the script
	 */
	public DeviceJsScript withId(long newId) {
		return new DeviceJsScript(newId, this.name, this.folder, this.script, this.deviceDriver, this.author, this.userInputDefinitions);
	}

	/**
	 * Gets a copy of the script in another folder.
	 *
	 * @param newFolder the folder
	 * @return the script
	 */
	public DeviceJsScript withFolder(String newFolder) {
		return new DeviceJsScript(this.id, this.name, newFolder, this.script, this.deviceDriver, this.author, this.userInputDefinitions);
	}

	/**
	 * Gets a copy of the script without its content (for the lists).
	 *
	 * @return the script
	 */
	public DeviceJsScript withoutScript() {
		return new DeviceJsScript(this.id, this.name, this.folder, null, this.deviceDriver, this.author, this.userInputDefinitions);
	}

	/**
	 * Create a task context which does nothing.
	 *
	 * @return the void task context
	 */
	private static TaskContext makeVoidLogger() {
		return new TaskContext() {
			@Override
			public boolean isTracing() {
				return false;
			}

			@Override
			public void log(Level level, String message, Object... params) {
				// Ignore
			}

			@Override
			public String getIdentifier() {
				return "Void";
			}
		};
	}

	/**
	 * Gets a copy of the script with its user input definitions, extracted by evaluating the script
	 * (unless they already are).
	 *
	 * @param driverRegistry the loaded device drivers
	 * @return the script
	 * @throws IllegalArgumentException if the script is invalid
	 */
	public DeviceJsScript withUserInputDefinitions(DriverRegistry driverRegistry) throws IllegalArgumentException {
		if (this.userInputDefinitions != null) {
			return this;
		}
		final UserDeviceScript jsScript = new UserDeviceScript(driverRegistry, SecretResolver.LOCAL, this.deviceDriver, this.script, makeVoidLogger());
		return new DeviceJsScript(this.id, this.name, this.folder, this.script, this.deviceDriver, this.author,
			jsScript.extractInputDefinitions());
	}

	/**
	 * Validate passed user inputs against input definitions.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param userInputs the data to validate
	 * @throws IllegalArgumentException if validation fails
	 */
	public void validateUserInputs(DriverRegistry driverRegistry, Map<String, String> userInputs) throws IllegalArgumentException {
		final UserDeviceScript jsScript = new UserDeviceScript(driverRegistry, SecretResolver.LOCAL, this.deviceDriver, this.script, makeVoidLogger());
		jsScript.setUserInputValues(userInputs);
		jsScript.validateUserInputs();
	}

	@Override
	public String toString() {
		return "Device JavaScript script " + id + " (name '" + name + "', author '" + author + "')";
	}
}
