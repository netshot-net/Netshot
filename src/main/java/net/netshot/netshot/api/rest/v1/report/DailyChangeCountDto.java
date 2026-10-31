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
package net.netshot.netshot.api.rest.v1.report;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The number of configuration changes of a day.
 *
 * @param date the start of the day (in the requested time zone)
 * @param changeCount the number of configuration changes
 */
@Schema(name = "DailyChangeCount", description = "Number of configuration changes of a day")
public record DailyChangeCountDto(
	Instant date,
	long changeCount) {
}
