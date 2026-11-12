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

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Time buckets of the statistics")
class IntervalTest {

	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

	@Test
	@DisplayName("Buckets are aligned in the time zone, across a daylight saving change")
	void daysInTimeZone() {
		// 2026-03-29: Paris goes from UTC+1 to UTC+2
		List<Instant> starts = Interval.DAY.starts(Instant.parse("2026-03-28T12:00:00Z"), Instant.parse("2026-03-30T12:00:00Z"), PARIS, 10);
		Assertions.assertEquals(List.of(Instant.parse("2026-03-27T23:00:00Z"), Instant.parse("2026-03-28T23:00:00Z"),
			Instant.parse("2026-03-29T22:00:00Z")), starts);
	}

	@Test
	@DisplayName("Weeks start on Monday, months on the first day")
	void weeksAndMonths() {
		ZoneId utc = ZoneId.of("UTC");
		Assertions.assertEquals(Instant.parse("2026-10-26T00:00:00Z"),
			Interval.WEEK.truncate(Instant.parse("2026-11-01T15:00:00Z"), utc).toInstant());
		Assertions.assertEquals(Instant.parse("2026-11-01T00:00:00Z"),
			Interval.MONTH.truncate(Instant.parse("2026-11-30T15:00:00Z"), utc).toInstant());
		Assertions.assertEquals(3, Interval.MONTH.starts(Instant.parse("2026-01-15T00:00:00Z"),
			Instant.parse("2026-03-02T00:00:00Z"), utc, 10).size());
	}

	@Test
	@DisplayName("Too many buckets: null")
	void tooMany() {
		Assertions.assertNull(Interval.HOUR.starts(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"),
			ZoneId.of("UTC"), 1000));
	}
}
