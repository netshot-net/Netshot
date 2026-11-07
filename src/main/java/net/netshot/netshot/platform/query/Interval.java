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
package net.netshot.netshot.platform.query;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * The size of the time buckets of a statistics query. The buckets are aligned in a time zone: on the start of the
 * hour, of the day, of the week (Monday) or of the month.
 */
public enum Interval {
	/** One hour. */
	HOUR,
	/** One day. */
	DAY,
	/** One week, from Monday. */
	WEEK,
	/** One month. */
	MONTH;

	/** The maximum number of time buckets of a statistics query. */
	public static final int MAX_BUCKETS = 1000;

	/**
	 * Gets the start of the bucket a time is in.
	 *
	 * @param time the time
	 * @param zone the time zone the buckets are aligned in
	 * @return the start of the bucket
	 */
	public ZonedDateTime truncate(Instant time, ZoneId zone) {
		ZonedDateTime local = time.atZone(zone);
		return switch (this) {
			case HOUR -> local.truncatedTo(ChronoUnit.HOURS);
			case DAY -> local.truncatedTo(ChronoUnit.DAYS);
			case WEEK -> local.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS);
			case MONTH -> local.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
		};
	}

	/**
	 * Gets the start of the next bucket.
	 *
	 * @param start the start of a bucket
	 * @return the start of the next one
	 */
	public ZonedDateTime next(ZonedDateTime start) {
		return switch (this) {
			case HOUR -> start.plusHours(1);
			case DAY -> start.plusDays(1);
			case WEEK -> start.plusWeeks(1);
			case MONTH -> start.plusMonths(1);
		};
	}

	/**
	 * Gets the start of the previous bucket.
	 *
	 * @param start the start of a bucket
	 * @return the start of the previous one
	 */
	public ZonedDateTime previous(ZonedDateTime start) {
		return switch (this) {
			case HOUR -> start.minusHours(1);
			case DAY -> start.minusDays(1);
			case WEEK -> start.minusWeeks(1);
			case MONTH -> start.minusMonths(1);
		};
	}

	/**
	 * Lists the starts of the buckets of a range.
	 *
	 * @param from the start of the range (included)
	 * @param to the end of the range (excluded)
	 * @param zone the time zone the buckets are aligned in
	 * @param max the maximum number of buckets
	 * @return the starts of the buckets, or null if there would be more than the maximum
	 */
	public List<Instant> starts(Instant from, Instant to, ZoneId zone, int max) {
		List<Instant> starts = new ArrayList<>();
		for (ZonedDateTime start = this.truncate(from, zone); start.toInstant().isBefore(to); start = this.next(start)) {
			if (starts.size() >= max) {
				return null;
			}
			starts.add(start.toInstant());
		}
		return starts;
	}
}
