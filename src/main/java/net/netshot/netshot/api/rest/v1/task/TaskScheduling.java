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

import net.netshot.netshot.task.Task;

/**
 * The properties common to every request which creates a task: when to run it, with which priority.
 * All are optional: the task runs as soon as possible, with the default priority.
 */
public interface TaskScheduling {

	/**
	 * Gets the comments of the task.
	 *
	 * @return the comments
	 */
	String comments();

	/**
	 * Gets the type of schedule.
	 *
	 * @return the type ({@code ASAP} if null)
	 */
	Task.ScheduleType scheduleType();

	/**
	 * Gets the reference date of the schedule: when to run the task for {@code AT}, otherwise when the recurrence starts.
	 *
	 * @return the date ({@code AT} requires it)
	 */
	Instant scheduleReference();

	/**
	 * Gets the factor of the recurrence (every n hours, days, weeks, months).
	 *
	 * @return the factor (1 if null)
	 */
	Integer scheduleFactor();

	/**
	 * Gets the priority of the task, from 1 to 9 (more is better).
	 *
	 * @return the priority (5 if null)
	 */
	Integer priority();

	/**
	 * Tells whether to record a debug log of the execution.
	 *
	 * @return true to enable the debug (false if null)
	 */
	Boolean debugEnabled();
}
