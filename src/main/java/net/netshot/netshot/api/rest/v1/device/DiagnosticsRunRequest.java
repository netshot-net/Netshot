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
package net.netshot.netshot.api.rest.v1.device;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.api.rest.v1.task.TaskDto;
import net.netshot.netshot.api.rest.v1.task.TaskScheduling;
import net.netshot.netshot.task.Task;

/**
 * Options of a run of the diagnostics of a device (all optional).
 *
 * @param dontCheckCompliance whether to skip the compliance check after the diagnostics
 * @param comments the comments of the task
 * @param scheduleType the type of schedule ({@code ASAP} by default)
 * @param scheduleReference the reference date of the schedule
 * @param scheduleFactor the factor of the recurrence
 * @param priority the priority, from 1 to 9 (5 by default)
 * @param debugEnabled whether to record a debug log of the execution
 */
@Schema(name = "DiagnosticsRunRequest", description = "Options of a run of the diagnostics of a device")
@ReadOnlyFrom(TaskDto.class)
public record DiagnosticsRunRequest(
	Boolean dontCheckCompliance,
	String comments,
	Task.ScheduleType scheduleType,
	Instant scheduleReference,
	Integer scheduleFactor,
	Integer priority,
	Boolean debugEnabled) implements TaskScheduling {
}
