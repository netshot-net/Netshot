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

import java.util.Calendar;
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

}
