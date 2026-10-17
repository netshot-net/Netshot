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
package net.netshot.netshot.api.rest.v0.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.netshot.netshot.devicescript.UserDeviceScript.UserInputDefinition;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.userscript.DeviceJsScript;
import net.netshot.netshot.userscript.ScriptCreation;

/**
 * Maps the command scripts between the entities and their REST representation. All the methods
 * are pure functions.
 */
public final class ScriptMapper {

	private ScriptMapper() {
		// Static helpers only
	}

	// The description of the driver of the script (the typo of the unknown case is part of the API output)
	private static String realDeviceType(DriverRegistry driverRegistry, DeviceJsScript script) {
		DeviceDriver deviceDriver = driverRegistry.getDriverByName(script.getDeviceDriver());
		if (deviceDriver == null) {
			return "Unknwon driver";
		}
		return deviceDriver.getDescription();
	}

	/**
	 * Maps a script to its representation.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param script the script
	 * @return the DTO
	 */
	public static ScriptDto toDto(DriverRegistry driverRegistry, DeviceJsScript script) {
		Map<String, UserInputDefinitionDto> definitions = null;
		if (script.getUserInputDefinitions() != null) {
			definitions = new LinkedHashMap<>();
			for (Map.Entry<String, UserInputDefinition> entry : script.getUserInputDefinitions().entrySet()) {
				UserInputDefinition definition = entry.getValue();
				definitions.put(entry.getKey(), new UserInputDefinitionDto(definition.getName(), definition.getType(),
					definition.getLabel(), definition.getDescription(), definition.getChoices(), definition.getDefaultValue()));
			}
		}
		return new ScriptDto(script.getAuthor(), script.getDeviceDriver(), script.getFolder(), script.getId(), script.getName(),
			realDeviceType(driverRegistry, script), script.getScript(), definitions);
	}

	/**
	 * Maps the scripts to their representation.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param scripts the scripts
	 * @return the DTOs, in the same order
	 */
	public static List<ScriptDto> toDtos(DriverRegistry driverRegistry, List<DeviceJsScript> scripts) {
		List<ScriptDto> dtos = new ArrayList<>(scripts.size());
		scripts.forEach(script -> dtos.add(toDto(driverRegistry, script)));
		return dtos;
	}

	/**
	 * Maps a request to add a script to the command of the service.
	 *
	 * @param request the request
	 * @param author the author, when the user could be determined, else null (the posted author is used)
	 * @return the command
	 */
	public static ScriptCreation toCreation(ScriptRequest request, String author) {
		return new ScriptCreation(request.name(), request.deviceDriver(), request.script(),
			author != null ? author : request.author(), request.folder());
	}
}
