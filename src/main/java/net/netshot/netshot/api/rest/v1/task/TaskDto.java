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
package net.netshot.netshot.api.rest.v1.task;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;
import net.netshot.netshot.api.rest.v1.Ref;
import net.netshot.netshot.api.rest.v1.device.DeviceDto;
import net.netshot.netshot.api.rest.v1.group.GroupDto;
import net.netshot.netshot.task.Task;

/**
 * A task.
 *
 * @param id the ID
 * @param url the path of the task
 * @param display the display name (the description of the type and the target)
 * @param version the version, incremented on each change (the ETag)
 * @param type the type (TakeSnapshotTask, RunDeviceScriptTask...)
 * @param status the status
 * @param description the description of the type
 * @param target the description of the target
 * @param author the user who created the task
 * @param comments the comments
 * @param device the target device (of a device task)
 * @param group the target group (of a group task)
 * @param parentTask the parent task (of a per-device child of a group task)
 * @param childOrder the position among the children of the parent task
 * @param priority the priority (1 to 9)
 * @param scheduleType the type of schedule
 * @param scheduleFactor the factor of the schedule (every n days...)
 * @param scheduleReference the reference date of the schedule
 * @param nextExecutionDate the date of the next execution (null for as soon as possible)
 * @param repeating whether the task is repeated
 * @param scheduleMode how the child tasks run
 * @param stopOnFailure whether the sequential child tasks stop on the first failure
 * @param creationDate the creation date
 * @param changeDate the date of the last change
 * @param executionDate the date of the (last) execution
 * @param runnerId the cluster member running the task
 * @param debugEnabled whether the debug log is kept
 * @param cancelRequested whether the cancellation was requested while the task runs
 * @param log the log of the last run
 * @param parameters the parameters of the type of task
 */
@Schema(name = "Task", description = "Task")
public record TaskDto(
	long id,
	String url,
	String display,
	Long version,
	@Brief String type,
	@Brief Task.Status status,
	String description,
	String target,
	String author,
	String comments,
	@Ref DeviceDto device,
	@Ref GroupDto group,
	@Ref TaskDto parentTask,
	Integer childOrder,
	Integer priority,
	Task.ScheduleType scheduleType,
	Integer scheduleFactor,
	Instant scheduleReference,
	Instant nextExecutionDate,
	Boolean repeating,
	Task.ScheduleMode scheduleMode,
	Boolean stopOnFailure,
	Instant creationDate,
	Instant changeDate,
	Instant executionDate,
	String runnerId,
	Boolean debugEnabled,
	Boolean cancelRequested,
	String log,
	@Schema(description = "Parameters of the type of task") Map<String, Object> parameters) {

	/** The properties a task summary (the light list query) fills. */
	public static final Set<String> SUMMARY_PROPERTIES = Set.of("id", "url", "display", "version", "type", "status",
		"description", "target", "author", "comments", "device", "group", "parentTask", "childOrder", "priority",
		"scheduleType", "scheduleFactor", "scheduleReference", "scheduleMode", "stopOnFailure", "creationDate", "changeDate",
		"executionDate", "runnerId", "debugEnabled");
}
