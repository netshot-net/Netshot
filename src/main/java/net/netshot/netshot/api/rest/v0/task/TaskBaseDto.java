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
package net.netshot.netshot.api.rest.v0.task;

import java.util.Date;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonView;

import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.task.Task;

/**
 * The properties every task has in its full representation, whatever its type. It is unwrapped
 * in each subtype of {@link TaskDto}, which adds the properties specific to the type.
 *
 * <p>The script, device driver, user inputs and script options are properties of every task, even
 * of the ones which do not run scripts (they are then empty).</p>
 *
 * @param author the author
 * @param cancelRequested whether the cancellation of the running task has been requested
 * @param changeDate the change date
 * @param checkCompliance whether the compliance is checked after a successful script execution
 * @param childOrder the position among the sibling tasks, when the task has a parent task
 * @param comments the comments
 * @param creationDate the creation date
 * @param debugEnabled whether debug is enabled
 * @param deviceDriver the compatible device driver (script tasks)
 * @param executionDate the execution date
 * @param id the task ID
 * @param log the log of the task
 * @param nextExecutionDate the next execution date
 * @param parentTaskId the ID of the parent task, when the task is a per-device child of a group-based task
 * @param priority the priority
 * @param repeating whether the task is repeating
 * @param runDiagnostics whether the diagnostics are run after a successful script execution
 * @param runSnapshot whether a snapshot is taken after a successful script execution
 * @param runnerId the runner ID (clustering mode)
 * @param scheduleFactor the schedule factor
 * @param scheduleMode the parallel vs sequential scheduling of the child tasks
 * @param scheduleReference the schedule reference date
 * @param scheduleType the schedule type
 * @param script the script (script tasks)
 * @param status the status
 * @param stopOnFailure whether to stop scheduling the child tasks after one fails
 * @param target the target
 * @param taskDescription the description of the type of task
 * @param userInputValues the values of the user inputs of the script
 */
@JsonView(DefaultView.class)
public record TaskBaseDto(
	String author,
	boolean cancelRequested,
	Date changeDate,
	boolean checkCompliance,
	int childOrder,
	String comments,
	Date creationDate,
	boolean debugEnabled,
	String deviceDriver,
	Date executionDate,
	long id,
	String log,
	Date nextExecutionDate,
	Long parentTaskId,
	int priority,
	boolean repeating,
	boolean runDiagnostics,
	boolean runSnapshot,
	String runnerId,
	int scheduleFactor,
	Task.ScheduleMode scheduleMode,
	Date scheduleReference,
	Task.ScheduleType scheduleType,
	String script,
	Task.Status status,
	boolean stopOnFailure,
	String target,
	String taskDescription,
	Map<String, String> userInputValues) {
}
