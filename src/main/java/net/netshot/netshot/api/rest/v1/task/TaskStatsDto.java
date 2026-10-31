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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskStats;

/**
 * Time-based statistics about the completed tasks.
 *
 * @param after the start of the range
 * @param before the end of the range
 * @param bins the buckets the range is split into, in chronological order
 * @param statusCounts the number of completed tasks by status over the whole range
 * @param typeCounts the number of completed tasks by type over the whole range
 */
@Schema(name = "TaskStats", description = "Time-based statistics about the completed tasks")
public record TaskStatsDto(
	Instant after,
	Instant before,
	List<Bin> bins,
	Map<Task.Status, Long> statusCounts,
	Map<String, Long> typeCounts) {

	/**
	 * One bucket of the statistics.
	 *
	 * @param from the start of the bucket
	 * @param to the end of the bucket
	 * @param countByStatus the number of completed tasks by status in the bucket
	 */
	@Schema(name = "TaskStatsBin", description = "One bucket of the task statistics")
	public record Bin(Instant from, Instant to, Map<Task.Status, Long> countByStatus) {
	}

	/**
	 * Maps the statistics.
	 *
	 * @param stats the statistics
	 * @param after the start of the range
	 * @param before the end of the range
	 * @return the representation
	 */
	public static TaskStatsDto of(TaskStats stats, Instant after, Instant before) {
		return new TaskStatsDto(after, before,
			stats.bins().stream().map(b -> new Bin(b.from().toInstant(), b.to().toInstant(), new TreeMap<>(b.countByStatus()))).toList(),
			new TreeMap<>(stats.statusCounts()), new TreeMap<>(stats.typeCounts()));
	}
}
