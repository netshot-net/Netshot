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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import net.netshot.netshot.api.rest.v0.EmptyWhenAbsentDeserializer;

/**
 * The request to add (or only validate) a command script. It used to be the script entity itself,
 * hence its quirks, kept as they were: the {@code realDeviceType} of a script is refused as an
 * unknown property, the {@code id} and the {@code author} are overridden by the server, and the {@code folder} is empty when
 * absent (null when explicitly null). The {@code userInputDefinitions} of a script (as returned by the
 * API) are accepted and ignored: the server always extracts them from the script code.
 *
 * @param id not used
 * @param name the name
 * @param script the JavaScript code
 * @param deviceDriver the name of the device driver the script is written for
 * @param author the author, used only when the user cannot be determined
 * @param folder the folder
 */
@JsonIgnoreProperties(value = "userInputDefinitions")
public record ScriptRequest(
	long id,
	String name,
	String script,
	String deviceDriver,
	String author,
	@JsonDeserialize(using = EmptyWhenAbsentDeserializer.class) String folder) {
}
