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
package net.netshot.netshot.api.rest.v1.script;

import java.util.LinkedHashMap;
import java.util.Map;

import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.devicescript.UserDeviceScript.UserInputDefinition;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.userscript.DeviceJsScript;
import net.netshot.netshot.userscript.ScriptCreation;

/**
 * Maps the command scripts to their v1 representation, and the requests to the commands the service takes.
 */
public final class ScriptMapper {

	/** The path of the scripts. */
	public static final String PATH = "scripts";

	private ScriptMapper() {
	}

	private static String driverDescription(DriverRegistry driverRegistry, String driverName) {
		DeviceDriver driver = driverRegistry.getDriverByName(driverName);
		return driver == null ? "Unknown driver" : driver.getDescription();
	}

	/**
	 * Maps a script.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param script the script (its user input definitions are written if it has them)
	 * @return the representation (without url for a script not stored)
	 */
	public static ScriptDto toDto(DriverRegistry driverRegistry, DeviceJsScript script) {
		Map<String, UserInputDefinitionDto> definitions = null;
		if (script.userInputDefinitions() != null) {
			definitions = new LinkedHashMap<>();
			for (Map.Entry<String, UserInputDefinition> entry : script.userInputDefinitions().entrySet()) {
				UserInputDefinition d = entry.getValue();
				definitions.put(entry.getKey(), new UserInputDefinitionDto(d.getName(), d.getType(), d.getLabel(), d.getDescription(),
					d.getChoices(), d.getDefaultValue()));
			}
		}
		return new ScriptDto(script.id(), script.id() == 0 ? null : V1Links.of(PATH, script.id()), script.name(), script.name(),
			script.folder(), script.deviceDriver(), driverDescription(driverRegistry, script.deviceDriver()), script.author(),
			script.script(), definitions);
	}

	/**
	 * Maps a request to the command of the service.
	 *
	 * @param request the request
	 * @param author the user creating the script
	 * @return the command
	 */
	public static ScriptCreation toCreation(ScriptRequest request, String author) {
		return new ScriptCreation(request.name(), request.deviceDriver(), request.script(), author,
			request.folder() == null ? "" : request.folder());
	}
}
