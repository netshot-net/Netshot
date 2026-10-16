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

import net.netshot.netshot.devicescript.UserDeviceScript.UserInputDefinition;

/**
 * The properties of a command script to create (or only to validate).
 *
 * @param name the name
 * @param deviceDriver the name of the device driver the script is written for
 * @param script the JavaScript code
 * @param author the name of the author (the user who creates the script)
 * @param folder the folder the script is stored in (may be null, as it was posted)
 * @param userInputDefinitions the input definitions posted along with the script, or null; when present they are
 *        kept as they are and the script is not evaluated to extract them
 */
public record ScriptCreation(
	String name,
	String deviceDriver,
	String script,
	String author,
	String folder,
	Map<String, UserInputDefinition> userInputDefinitions) {
}
