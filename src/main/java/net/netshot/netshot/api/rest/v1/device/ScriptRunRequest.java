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
package net.netshot.netshot.api.rest.v1.device;

import java.time.Instant;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.api.rest.v1.RefId;
import net.netshot.netshot.api.rest.v1.task.TaskDto;
import net.netshot.netshot.api.rest.v1.task.TaskScheduling;
import net.netshot.netshot.task.Task;

/**
 * A script to run on a device: either a saved script, or the text of a script with the driver it is written for.
 *
 * @param savedScript the saved script to run (instead of {@code script} and {@code deviceDriver})
 * @param script the text of the script to run
 * @param deviceDriver the name of the driver the script is written for
 * @param userInputValues the values of the inputs the script asks for
 * @param runSnapshot whether to take a snapshot after the script succeeds
 * @param runDiagnostics whether to run the diagnostics after the script succeeds
 * @param checkCompliance whether to check the compliance after the script succeeds
 * @param comments the comments of the task
 * @param scheduleType the type of schedule ({@code ASAP} by default)
 * @param scheduleReference the reference date of the schedule
 * @param scheduleFactor the factor of the recurrence
 * @param priority the priority, from 1 to 9 (5 by default)
 * @param debugEnabled whether to record a debug log of the execution
 */
@Schema(name = "ScriptRunRequest", description = "Script to run on a device")
@ReadOnlyFrom(TaskDto.class)
public record ScriptRunRequest(
	RefId savedScript,
	String script,
	String deviceDriver,
	Map<String, String> userInputValues,
	Boolean runSnapshot,
	Boolean runDiagnostics,
	Boolean checkCompliance,
	String comments,
	Task.ScheduleType scheduleType,
	Instant scheduleReference,
	Integer scheduleFactor,
	Integer priority,
	Boolean debugEnabled) implements TaskScheduling {
}
