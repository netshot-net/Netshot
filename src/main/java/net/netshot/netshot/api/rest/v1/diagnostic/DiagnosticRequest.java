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
package net.netshot.netshot.api.rest.v1.diagnostic;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.api.rest.v1.RefId;

/**
 * The definition of a diagnostic, to create one ({@code POST}) or replace one ({@code PUT}, and the result of a
 * {@code PATCH}). The read-only properties of the representation ({@link DiagnosticDto}) are ignored.
 *
 * @param name the name
 * @param type SIMPLE, JAVASCRIPT or PYTHON (it can't change)
 * @param resultType the type of the result: TEXT, LONGTEXT, NUMERIC or BINARY
 * @param enabled whether the diagnostic is run (false if absent)
 * @param targetGroup the group of devices the diagnostic is run on, by ID (none if absent)
 * @param script the script (JAVASCRIPT and PYTHON)
 * @param deviceDriver the name of the driver the command is for (SIMPLE; any driver if absent)
 * @param cliMode the CLI mode the command is run in (SIMPLE)
 * @param command the CLI command (SIMPLE)
 * @param modifierPattern the regular expression replaced in the output (SIMPLE; none if absent)
 * @param modifierReplacement the replacement of the pattern (SIMPLE; empty if absent)
 */
@Schema(name = "DiagnosticRequest", description = "Definition of a diagnostic")
@ReadOnlyFrom(DiagnosticDto.class)
public record DiagnosticRequest(
	@JsonProperty(required = true) String name,
	@JsonProperty(required = true) @Schema(allowableValues = {"SIMPLE", "JAVASCRIPT", "PYTHON"}) String type,
	@JsonProperty(required = true) @Schema(allowableValues = {"TEXT", "LONGTEXT", "NUMERIC", "BINARY"}) String resultType,
	boolean enabled,
	@Schema(description = "The target group: its ID, or an object with its ID (such as the one read)") RefId targetGroup,
	String script,
	String deviceDriver,
	String cliMode,
	String command,
	String modifierPattern,
	String modifierReplacement) {
}
