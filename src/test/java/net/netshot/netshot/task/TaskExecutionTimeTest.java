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
package net.netshot.netshot.task;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.task.Task.ScheduleType;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.testing.UnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnitTest
@DisplayName("Next execution time of tasks")
public class TaskExecutionTimeTest {

	@BeforeAll
	static void initNetshot() {
		TestConfig.init(new Properties());
	}

	Task task = Task.of(new CheckComplianceTask(null), "", "None", "");

	@Test
	@DisplayName("Next execution time of ASAP task")
	void asapTask() {
		task = task.toBuilder().scheduleType(ScheduleType.ASAP).build();
		Assertions.assertNull(task.nextExecutionDate(),
			"Next execution date for ASAP task is not null");
	}

	@Test
	@DisplayName("Next execution time of AT task")
	void atTask() {
		Calendar inSixteenMinutes = Calendar.getInstance();
		inSixteenMinutes.add(Calendar.MINUTE, 16);
		task = task.toBuilder().scheduleType(ScheduleType.AT).build();
		task = task.toBuilder().scheduleReference(inSixteenMinutes.getTime().toInstant()).build();
		Assertions.assertEquals(task.nextExecutionDate(), inSixteenMinutes.getTime(),
			"Next execution date for AT task is not the schedule reference");
	}

	@Test
	@DisplayName("Next execution time of EVERY 2 HOURS imminent task")
	void everyTwoHoursImminentTask() {
		Calendar inTenSeconds = Calendar.getInstance();
		inTenSeconds.add(Calendar.SECOND, 10);
		task = task.toBuilder().scheduleReference(inTenSeconds.getTime().toInstant()).build();
		task = task.toBuilder().scheduleType(ScheduleType.HOURLY).build();
		task = task.toBuilder().scheduleFactor(2).build();
		Calendar inTwoHours = (Calendar) inTenSeconds.clone();
		inTwoHours.add(Calendar.HOUR, 2);
		Assertions.assertEquals(task.nextExecutionDate(), inTwoHours.getTime(),
			"Next execution date for EVERY 2 HOURS task is not in two hours");
	}

	@Test
	@DisplayName("Next execution time of EVERY 2 HOURS task")
	void everyTwoHoursTask() {
		Calendar inEightySeconds = Calendar.getInstance();
		inEightySeconds.add(Calendar.SECOND, 80);
		task = task.toBuilder().scheduleReference(inEightySeconds.getTime().toInstant()).build();
		task = task.toBuilder().scheduleType(ScheduleType.HOURLY).build();
		task = task.toBuilder().scheduleFactor(2).build();
		Assertions.assertEquals(task.nextExecutionDate(), inEightySeconds.getTime(),
			"Next execution date for EVERY 2 HOURS task is not now");
	}

	@Test
	@DisplayName("Next execution time of EVERY WEEK task")
	void everyWeekTask() {
		Calendar inTenSeconds = Calendar.getInstance();
		inTenSeconds.add(Calendar.SECOND, 10);
		task = task.toBuilder().scheduleReference(inTenSeconds.getTime().toInstant()).build();
		task = task.toBuilder().scheduleType(ScheduleType.WEEKLY).build();
		task = task.toBuilder().scheduleFactor(1).build();
		Calendar inOneWeek = (Calendar) inTenSeconds.clone();
		inOneWeek.add(Calendar.WEEK_OF_YEAR, 1);
		Assertions.assertEquals(task.nextExecutionDate(), inOneWeek.getTime(),
			"Next execution date for EVERY WEEK task is not now");
	}

	@Test
	@DisplayName("Next execution time of EVERY SIX MONTHS task")
	void everySixMonths() {
		Calendar inTenSeconds = Calendar.getInstance();
		inTenSeconds.add(Calendar.SECOND, 10);
		task = task.toBuilder().scheduleReference(inTenSeconds.getTime().toInstant()).build();
		task = task.toBuilder().scheduleType(ScheduleType.MONTHLY).build();
		task = task.toBuilder().scheduleFactor(6).build();
		Calendar inSixMonths = (Calendar) inTenSeconds.clone();
		inSixMonths.add(Calendar.MONTH, 6);
		Assertions.assertEquals(task.nextExecutionDate(), inSixMonths.getTime(),
			"Next execution date for EVERY SIX MONTHS task is not now");
	}

	@Test
	@DisplayName("Next execution time of EVERY FIRST OF THE MONTH task")
	void everyFirstOfTheMonth() {
		Calendar reference = Calendar.getInstance();
		reference.set(2016, 01, 01, 16, 00, 00);
		reference.set(Calendar.MILLISECOND, 0);
		task = task.toBuilder().scheduleReference(reference.getTime().toInstant()).build();
		task = task.toBuilder().scheduleType(ScheduleType.MONTHLY).build();
		task = task.toBuilder().scheduleFactor(1).build();
		Calendar nextFirst = Calendar.getInstance();
		nextFirst.set(Calendar.DAY_OF_MONTH, 1);
		nextFirst.set(Calendar.HOUR_OF_DAY, 16);
		nextFirst.set(Calendar.MINUTE, 0);
		nextFirst.set(Calendar.SECOND, 0);
		nextFirst.set(Calendar.MILLISECOND, 0);
		if (nextFirst.before(Calendar.getInstance())) {
			nextFirst.add(Calendar.MONTH, 1);
		}
		Assertions.assertEquals(task.nextExecutionDate(), nextFirst.getTime(),
			"Next execution date for EVERY FIRST OF THE MONTH is not next first of the month");
	}

	@Test
	@DisplayName("A weekly task created years ago keeps its weekday")
	void weeklyTaskKeepsItsWeekday() {
		// About three years ago: moving it to last year would shift its weekday by 2 or 3 days
		ZonedDateTime reference = ZonedDateTime.now().minusWeeks(156).minusDays(3).withNano(0);
		task = task.toBuilder().scheduleType(ScheduleType.WEEKLY).scheduleFactor(1).scheduleReference(reference.toInstant()).build();
		ZonedDateTime limit = ZonedDateTime.now().plusMinutes(1);
		ZonedDateTime expected = reference;
		while (!expected.isAfter(limit)) {
			expected = expected.plusWeeks(1);
		}
		Assertions.assertEquals(Date.from(expected.toInstant()), task.nextExecutionDate());
		Assertions.assertEquals(reference.getDayOfWeek(), task.nextExecutionDate().toInstant().atZone(ZoneId.systemDefault()).getDayOfWeek());
	}

	private Instant next(ScheduleType type, int factor, String reference, String now, String zone) {
		return task.toBuilder().scheduleType(type).scheduleFactor(factor).scheduleReference(Instant.parse(reference)).build()
			.nextExecutionDate(Instant.parse(now), ZoneId.of(zone));
	}

	@Test
	@DisplayName("Weekly task: next occurrence on the weekday of the reference, years later")
	void weeklyYearsLater() {
		// Tuesday 2023-11-14 -> Tuesday 2026-11-10 (not the 14th, a Saturday)
		Assertions.assertEquals(Instant.parse("2026-11-10T22:13:20Z"),
			next(ScheduleType.WEEKLY, 1, "2023-11-14T22:13:20Z", "2026-11-08T12:00:00Z", "UTC"));
	}

	@Test
	@DisplayName("Every 3 weeks task: keeps its phase across the years")
	void everyThreeWeeksKeepsPhase() {
		// 2026-11-10 is 156 weeks (52 x 3) after the reference, the next one is 3 weeks later
		Assertions.assertEquals(Instant.parse("2026-12-01T22:13:20Z"),
			next(ScheduleType.WEEKLY, 3, "2023-11-14T22:13:20Z", "2026-11-11T00:00:00Z", "UTC"));
	}

	@Test
	@DisplayName("Monthly task on the 31st: last day of shorter months, back to the 31st after")
	void monthlyOnThe31st() {
		Assertions.assertEquals(Instant.parse("2026-04-30T10:00:00Z"),
			next(ScheduleType.MONTHLY, 1, "2026-01-31T10:00:00Z", "2026-04-01T00:00:00Z", "UTC"));
		Assertions.assertEquals(Instant.parse("2026-05-31T10:00:00Z"),
			next(ScheduleType.MONTHLY, 1, "2026-01-31T10:00:00Z", "2026-05-01T00:00:00Z", "UTC"));
	}

	@Test
	@DisplayName("Daily task: same local time across a daylight saving time change")
	void dailySameLocalTime() {
		// 22:00 in Paris: 21:00 UTC in winter, 20:00 UTC in summer
		Assertions.assertEquals(Instant.parse("2026-07-01T20:00:00Z"),
			next(ScheduleType.DAILY, 1, "2026-03-01T21:00:00Z", "2026-07-01T12:00:00Z", "Europe/Paris"));
	}

	@Test
	@DisplayName("Hourly task: whole hours from the reference")
	void hourlyFromReference() {
		Assertions.assertEquals(Instant.parse("2026-11-08T13:05:00Z"),
			next(ScheduleType.HOURLY, 4, "2026-11-01T01:05:00Z", "2026-11-08T12:00:00Z", "UTC"));
	}

	@Test
	@DisplayName("Repeating task whose reference is in the future: the reference")
	void futureReference() {
		Assertions.assertEquals(Instant.parse("2027-01-01T00:00:00Z"),
			next(ScheduleType.DAILY, 1, "2027-01-01T00:00:00Z", "2026-11-08T12:00:00Z", "UTC"));
	}

}
