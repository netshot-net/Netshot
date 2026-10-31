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

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;

/**
 * A command script (a script to be later run over devices). The user inputs are extracted by evaluating the
 * script: they are written when the script is read, created or validated, and only on demand in a list.
 *
 * @param id the ID (0 for a script validated but not saved)
 * @param url the path of the script (null for a script validated but not saved)
 * @param display the display name
 * @param name the name
 * @param folder the folder
 * @param deviceDriver the name of the device driver the script is written for
 * @param deviceDriverDescription the description of the driver
 * @param author the author
 * @param script the JavaScript code
 * @param userInputDefinitions the user inputs the script asks for, by name (null if they couldn't be extracted)
 */
@Schema(name = "Script", description = "Command script")
public record ScriptDto(
	long id,
	String url,
	String display,
	@Brief String name,
	@Brief String folder,
	@Brief String deviceDriver,
	String deviceDriverDescription,
	String author,
	String script,
	Map<String, UserInputDefinitionDto> userInputDefinitions) {
}
