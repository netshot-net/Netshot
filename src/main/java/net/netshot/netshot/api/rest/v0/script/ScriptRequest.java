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


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.xml.bind.annotation.XmlRootElement;
import net.netshot.netshot.api.rest.EmptyWhenAbsentDeserializer;
import net.netshot.netshot.devicescript.UserDeviceScript.UserInputDefinition;

/**
 * The request to add (or only validate) a command script. It used to be the script entity itself,
 * hence its quirks, kept as they were: the {@code realDeviceType} of a script is refused as an
 * unknown property, the {@code id} and the {@code author} are overridden by the server, the {@code folder} is empty when
 * absent (null when explicitly null), and the {@code userInputDefinitions} are read as the engine
 * class declares them (which only succeeds for an empty map, null entries and bare text entries:
 * the class has no property-based creator).
 *
 * @param id not used
 * @param name the name
 * @param script the JavaScript code
 * @param deviceDriver the name of the device driver the script is written for
 * @param author the author, used only when the user cannot be determined
 * @param userInputDefinitions the input definitions, when posted
 * @param folder the folder
 */
// Transitional, see NewDeviceRequest
@XmlRootElement(name = "deviceJsScript")
public record ScriptRequest(
	long id,
	String name,
	String script,
	String deviceDriver,
	String author,
	Map<String, UserInputDefinition> userInputDefinitions,
	@JsonDeserialize(using = EmptyWhenAbsentDeserializer.class) String folder) {
}
