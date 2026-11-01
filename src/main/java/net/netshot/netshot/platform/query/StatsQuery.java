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
package net.netshot.netshot.platform.query;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * A statistics query: the objects matching a list query are counted, by the values of some fields ({@code groupBy})
 * and/or by time buckets of a date field ({@code on}, by {@code interval}, over a range).
 *
 * @param groupBy the names of the fields to count by (none for one count)
 * @param interval the size of the time buckets, or null for no time buckets
 * @param on the name of the date field the time buckets are on (null without time buckets)
 * @param from the start of the range of the time buckets (included)
 * @param to the end of the range of the time buckets (excluded)
 * @param timeZone the time zone the time buckets are aligned in
 */
public record StatsQuery(List<String> groupBy, Interval interval, String on, Instant from, Instant to, ZoneId timeZone) {

	/**
	 * Copies the list.
	 */
	public StatsQuery {
		groupBy = List.copyOf(groupBy);
	}
}
