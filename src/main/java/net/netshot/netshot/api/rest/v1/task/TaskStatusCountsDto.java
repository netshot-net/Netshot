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

import java.util.Map;
import java.util.TreeMap;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskOverview;

/**
 * The number of tasks by status, and the number of threads executing them.
 *
 * @param countByStatus the number of tasks by status (every status is given, 0 included)
 * @param threadCount the number of task threads
 */
@Schema(name = "TaskStatusCounts", description = "Number of tasks by status")
public record TaskStatusCountsDto(
	Map<Task.Status, Long> countByStatus,
	int threadCount) {

	/**
	 * Maps an overview.
	 *
	 * @param overview the overview
	 * @return the representation
	 */
	public static TaskStatusCountsDto of(TaskOverview overview) {
		return new TaskStatusCountsDto(new TreeMap<>(overview.countByStatus()), overview.threadCount());
	}
}
