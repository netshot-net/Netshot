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
package net.netshot.netshot.api.rest.v1.task;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.api.rest.v1.RefId;
import net.netshot.netshot.task.Task;

/**
 * A task to create.
 *
 * @param type the type of task (see the {@code type} of a task)
 * @param device the target device, for a task on a device
 * @param group the target group, for a task on a group
 * @param deviceList the ordered devices to work on, for a task on a group, instead of a group
 * @param parameters the parameters of the type of task (see the {@code parameters} of a task)
 * @param comments the comments
 * @param scheduleType the type of schedule ({@code ASAP} by default)
 * @param scheduleReference the reference date of the schedule
 * @param scheduleFactor the factor of the recurrence
 * @param priority the priority, from 1 to 9 (5 by default)
 * @param debugEnabled whether to record a debug log of the execution
 * @param scheduleMode how the tasks of the devices of a group are run (in parallel by default)
 * @param stopOnFailure whether to stop running the tasks of the devices of a group after a failure (sequential mode only)
 */
@Schema(name = "TaskRequest", description = "Task to create")
@ReadOnlyFrom(TaskDto.class)
public record TaskRequest(
	@JsonProperty(required = true) String type,
	RefId device,
	RefId group,
	List<RefId> deviceList,
	@Schema(description = "Parameters of the type of task") Map<String, Object> parameters,
	String comments,
	Task.ScheduleType scheduleType,
	Instant scheduleReference,
	Integer scheduleFactor,
	Integer priority,
	Boolean debugEnabled,
	Task.ScheduleMode scheduleMode,
	Boolean stopOnFailure) implements TaskScheduling {
}
