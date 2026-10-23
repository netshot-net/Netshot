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
package net.netshot.netshot.task;

import java.time.Instant;

/**
 * A light-weight projection of a {@link Task}, carrying only the fields needed to list tasks
 * (notably not the script and the logs, which can be arbitrarily large). It is the result of
 * the task list queries; the full task is fetched when its details are needed.
 *
 * @param id the task ID
 * @param type the type name of the task (e.g. TakeSnapshotTask)
 * @param status the status
 * @param author the author
 * @param target the target
 * @param comments the comments
 * @param creationDate the creation date
 * @param changeDate the change date
 * @param executionDate the execution date
 * @param scheduleReference the schedule reference date
 * @param scheduleType the schedule type
 * @param scheduleFactor the schedule factor
 * @param priority the priority
 * @param runnerId the runner ID (clustering mode)
 * @param debugEnabled whether debug is enabled
 * @param deviceId the target device ID, if any
 * @param deviceGroupId the target device group ID, if any
 * @param parentTaskId the parent task ID, when the task is a per-device child of a group-based task
 * @param childOrder the position of the task among its siblings, when it has a parent task
 * @param scheduleMode the parallel vs sequential scheduling of the child tasks
 * @param stopOnFailure whether to stop scheduling further child tasks after one fails
 */
public record TaskSummary(
	long id,
	String type,
	Task.Status status,
	String author,
	String target,
	String comments,
	Instant creationDate,
	Instant changeDate,
	Instant executionDate,
	Instant scheduleReference,
	Task.ScheduleType scheduleType,
	int scheduleFactor,
	int priority,
	String runnerId,
	boolean debugEnabled,
	Long deviceId,
	Long deviceGroupId,
	Long parentTaskId,
	int childOrder,
	Task.ScheduleMode scheduleMode,
	boolean stopOnFailure) {
}
