/**
 * Copyright 2013-present Netshot
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

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;

/**
 * The definition of a command script, to create one or validate one. The read-only properties of the
 * representation ({@link ScriptDto}) are ignored: the author is the user, the user inputs are extracted from
 * the script.
 *
 * @param name the name
 * @param folder the folder (the root folder if absent)
 * @param deviceDriver the name of the device driver the script is written for
 * @param script the JavaScript code
 */
@Schema(name = "ScriptRequest", description = "Definition of a command script")
@ReadOnlyFrom(ScriptDto.class)
public record ScriptRequest(
	@JsonProperty(required = true) String name,
	String folder,
	@JsonProperty(required = true) String deviceDriver,
	@JsonProperty(required = true) String script) {
}
