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

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * The command to create a task of any type. A single record serves every type: {@code type}
 * (the simple class name of the task) tells which components are used, the others are ignored.
 * The scheduling components apply to all the types.
 *
 * <p>The components keep the semantics of the former REST request: they are not validated
 * by the record, and a {@code null} is passed as is to the checks of
 * {@link TaskService#create(TaskCreation)}.</p>
 *
 * @param author the user creating the task
 * @param type the simple class name of the task to create
 * @param device the target device ID (device-based tasks)
 * @param group the target device group ID (group-based tasks, 0 or null for none)
 * @param deviceList the ordered list of device IDs, as an alternative to a group (group-based tasks)
 * @param scheduleMode parallel vs sequential scheduling of the child tasks (group-based tasks)
 * @param stopOnFailure whether to stop scheduling further child tasks after one fails (sequential mode)
 * @param domain the domain ID (subnet scan)
 * @param subnets the subnets to scan, separated by commas, spaces, semicolons or new lines (subnet scan)
 * @param scheduleReference the scheduling reference date (null for no scheduling data at all)
 * @param scheduleType the scheduling mode (ASAP, once, recurring...)
 * @param scheduleFactor the scheduling factor of a recurring task
 * @param schedulePriority the priority, from 1 to 9
 * @param comments the comments
 * @param debugEnabled whether to enable task debugging
 * @param limitToOutofdateDeviceHours ignore the devices having a successful snapshot in the last given hours (group snapshot)
 * @param daysToPurge purge the tasks older than this number of days (database purge)
 * @param configDaysToPurge purge the configurations older than this number of days (database purge)
 * @param configSizeToPurge purge the configurations bigger than this size in KB (database purge)
 * @param configKeepDays when purging old configurations, keep one every n days (database purge)
 * @param moduleDaysToPurge purge the removed hardware modules older than this number of days (database purge)
 * @param removeOrphanFiles remove the orphan binary attribute files (database purge)
 * @param script the script to execute (script tasks)
 * @param driver the name of the device driver (script tasks)
 * @param userInputs the user inputs of the script (script tasks)
 * @param dontRunDiagnostics disable the automatic diagnostics (snapshot tasks)
 * @param dontCheckCompliance disable the automatic compliance check (snapshot and diagnostic tasks)
 * @param runSnapshot run a snapshot after a successful script execution (script tasks)
 * @param runDiagnostics run the diagnostics after a successful script execution (script tasks)
 * @param checkCompliance check the compliance after a successful script execution (script tasks)
 */
public record TaskCreation(
	String author,
	String type,
	Long device,
	Long group,
	List<Long> deviceList,
	Task.ScheduleMode scheduleMode,
	boolean stopOnFailure,
	Long domain,
	String subnets,
	Date scheduleReference,
	Task.ScheduleType scheduleType,
	int scheduleFactor,
	int schedulePriority,
	String comments,
	boolean debugEnabled,
	int limitToOutofdateDeviceHours,
	int daysToPurge,
	int configDaysToPurge,
	int configSizeToPurge,
	int configKeepDays,
	int moduleDaysToPurge,
	boolean removeOrphanFiles,
	String script,
	String driver,
	Map<String, String> userInputs,
	boolean dontRunDiagnostics,
	boolean dontCheckCompliance,
	boolean runSnapshot,
	boolean runDiagnostics,
	boolean checkCompliance) {
}
