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

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;
import net.netshot.netshot.api.rest.v1.Ref;
import net.netshot.netshot.api.rest.v1.group.GroupDto;

/**
 * A diagnostic: a piece of data collected from the devices of a group, by a CLI command or a script. The
 * properties specific to a type are null for the other types.
 *
 * @param id the ID
 * @param url the path of the diagnostic
 * @param display the display name
 * @param version the version, incremented on each change (the ETag)
 * @param changeDate the date of the last change
 * @param name the name
 * @param type SIMPLE (CLI command), JAVASCRIPT or PYTHON
 * @param resultType the type of the result: TEXT, LONGTEXT, NUMERIC or BINARY
 * @param enabled whether the diagnostic is run
 * @param targetGroup the group of devices the diagnostic is run on (null for none)
 * @param script the script (JAVASCRIPT and PYTHON)
 * @param deviceDriver the name of the driver the command is for, empty for any (SIMPLE)
 * @param deviceDriverDescription the description of the driver (SIMPLE)
 * @param cliMode the CLI mode the command is run in (SIMPLE)
 * @param command the CLI command (SIMPLE)
 * @param modifierPattern the regular expression replaced in the output (SIMPLE)
 * @param modifierReplacement the replacement of the pattern (SIMPLE)
 */
@Schema(name = "Diagnostic", description = "Diagnostic (data collected from devices)")
public record DiagnosticDto(
	long id,
	String url,
	String display,
	long version,
	Instant changeDate,
	@Brief String name,
	@Brief String type,
	@Brief String resultType,
	@Brief boolean enabled,
	@Brief @Ref GroupDto targetGroup,
	String script,
	String deviceDriver,
	String deviceDriverDescription,
	String cliMode,
	String command,
	String modifierPattern,
	String modifierReplacement) {
}
