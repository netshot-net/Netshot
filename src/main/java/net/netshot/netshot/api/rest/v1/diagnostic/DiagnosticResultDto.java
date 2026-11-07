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

/**
 * The result of a diagnostic on a device: the last value the diagnostic gave, with the date it was first given
 * and the date it was last checked.
 *
 * @param display the name of the diagnostic
 * @param diagnostic the diagnostic
 * @param type the type of the value: TEXT, LONGTEXT, NUMERIC or BINARY
 * @param value the value (a string, a number or a boolean)
 * @param creationDate when the diagnostic first gave this value
 * @param lastCheckDate when the diagnostic last gave this value
 */
@Schema(name = "DiagnosticResult", description = "Result of a diagnostic on a device")
public record DiagnosticResultDto(
	String display,
	@Brief @Ref DiagnosticDto diagnostic,
	@Brief String type,
	@Brief Object value,
	Instant creationDate,
	Instant lastCheckDate) {
}
