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

import java.util.Map;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;


/**
 * The representation of a command script. The content is null in the list of scripts, and the
 * user input definitions are only known right after the script has been validated.
 *
 * @param author the name of the author
 * @param deviceDriver the name of the device driver the script is written for
 * @param folder the folder
 * @param id the script ID (0 for a script validated but not saved)
 * @param name the name
 * @param realDeviceType the description of the device driver
 * @param script the JavaScript code
 * @param userInputDefinitions the user inputs declared by the script, or null
 */
@JacksonXmlRootElement(localName = "DeviceJsScript")
public record ScriptDto(
	String author,
	String deviceDriver,
	String folder,
	long id,
	String name,
	String realDeviceType,
	String script,
	Map<String, UserInputDefinitionDto> userInputDefinitions) {
}
