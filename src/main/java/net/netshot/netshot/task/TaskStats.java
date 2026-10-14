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
 * Time-based statistics (histogram) about the completed tasks over a date range.
 *
 * @param bins the time buckets
 * @param statusCounts the number of completed tasks by status, over the whole range
 * @param typeCounts the number of completed tasks by task type (simple class name), over the whole range
 */
public record TaskStats(List<Bin> bins, Map<Task.Status, Long> statusCounts, Map<String, Long> typeCounts) {

	/**
	 * One bucket of the statistics.
	 *
	 * @param from the start of the bucket
	 * @param to the end of the bucket
	 * @param countByStatus the number of completed tasks by status in the bucket
	 */
	public record Bin(Date from, Date to, Map<Task.Status, Long> countByStatus) {
	}
}
