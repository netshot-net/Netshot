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

import java.util.List;


import net.netshot.netshot.driver.DriverValueType;

/**
 * The declaration of a user input of a command script.
 *
 * @param name the name of the input
 * @param type the type of value
 * @param label the label shown to the user
 * @param description the description
 * @param choices the valid choices restricting a text input, or null
 * @param defaultValue the default value, as a raw string
 */
public record UserInputDefinitionDto(
	String name,
	DriverValueType type,
	String label,
	String description,
	List<String> choices,
	String defaultValue) {
}
