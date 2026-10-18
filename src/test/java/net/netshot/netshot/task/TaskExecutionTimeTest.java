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

import org.quartz.JobKey;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.task.Task.ScheduleType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Next execution time of tasks")
public class TaskExecutionTimeTest {

	@BeforeAll
	static void initNetshot() {
		Netshot.readConfig();
	}

	Task task = new Task() {
		@Override
		public JobKey getIdentity() {
			return null;
		}

		@Override
		public String getTaskDescription() {
			return null;
		}
	};

	@Test
	@DisplayName("Next execution time of ASAP task")
	void asapTask() {
		task.setScheduleType(ScheduleType.ASAP);
		Assertions.assertNull(task.getNextExecutionDate(),
			"Next execution date for ASAP task is not null");
	}

	@Test
	@DisplayName("Next execution time of AT task")
	void atTask() {
		Calendar inSixteenMinutes = Calendar.getInstance();
		inSixteenMinutes.add(Calendar.MINUTE, 16);
		task.setScheduleType(ScheduleType.AT);
		task.setScheduleReference(inSixteenMinutes.getTime());
		Assertions.assertEquals(task.getNextExecutionDate(), inSixteenMinutes.getTime(),
			"Next execution date for AT task is not the schedule reference");
	}

	@Test
	@DisplayName("Next execution time of EVERY 2 HOURS imminent task")
	void everyTwoHoursImminentTask() {
		Calendar inTenSeconds = Calendar.getInstance();
		inTenSeconds.add(Calendar.SECOND, 10);
		task.setScheduleReference(inTenSeconds.getTime());
		task.setScheduleType(ScheduleType.HOURLY);
		task.setScheduleFactor(2);
		Calendar inTwoHours = (Calendar) inTenSeconds.clone();
		inTwoHours.add(Calendar.HOUR, 2);
		Assertions.assertEquals(task.getNextExecutionDate(), inTwoHours.getTime(),
			"Next execution date for EVERY 2 HOURS task is not in two hours");
	}

	@Test
	@DisplayName("Next execution time of EVERY 2 HOURS task")
	void everyTwoHoursTask() {
		Calendar inEightySeconds = Calendar.getInstance();
		inEightySeconds.add(Calendar.SECOND, 80);
		task.setScheduleReference(inEightySeconds.getTime());
		task.setScheduleType(ScheduleType.HOURLY);
		task.setScheduleFactor(2);
		Assertions.assertEquals(task.getNextExecutionDate(), inEightySeconds.getTime(),
			"Next execution date for EVERY 2 HOURS task is not now");
	}

	@Test
	@DisplayName("Next execution time of EVERY WEEK task")
	void everyWeekTask() {
		Calendar inTenSeconds = Calendar.getInstance();
		inTenSeconds.add(Calendar.SECOND, 10);
		task.setScheduleReference(inTenSeconds.getTime());
		task.setScheduleType(ScheduleType.WEEKLY);
		task.setScheduleFactor(1);
		Calendar inOneWeek = (Calendar) inTenSeconds.clone();
		inOneWeek.add(Calendar.WEEK_OF_YEAR, 1);
		Assertions.assertEquals(task.getNextExecutionDate(), inOneWeek.getTime(),
			"Next execution date for EVERY WEEK task is not now");
	}

	@Test
	@DisplayName("Next execution time of EVERY SIX MONTHS task")
	void everySixMonths() {
		Calendar inTenSeconds = Calendar.getInstance();
		inTenSeconds.add(Calendar.SECOND, 10);
		task.setScheduleReference(inTenSeconds.getTime());
		task.setScheduleType(ScheduleType.MONTHLY);
		task.setScheduleFactor(6);
		Calendar inSixMonths = (Calendar) inTenSeconds.clone();
		inSixMonths.add(Calendar.MONTH, 6);
		Assertions.assertEquals(task.getNextExecutionDate(), inSixMonths.getTime(),
			"Next execution date for EVERY SIX MONTHS task is not now");
	}

	@Test
	@DisplayName("Next execution time of EVERY FIRST OF THE MONTH task")
	void everyFirstOfTheMonth() {
		Calendar reference = Calendar.getInstance();
		reference.set(2016, 01, 01, 16, 00, 00);
		reference.set(Calendar.MILLISECOND, 0);
		task.setScheduleReference(reference.getTime());
		task.setScheduleType(ScheduleType.MONTHLY);
		task.setScheduleFactor(1);
		Calendar nextFirst = Calendar.getInstance();
		nextFirst.set(Calendar.DAY_OF_MONTH, 1);
		nextFirst.set(Calendar.HOUR_OF_DAY, 16);
		nextFirst.set(Calendar.MINUTE, 0);
		nextFirst.set(Calendar.SECOND, 0);
		nextFirst.set(Calendar.MILLISECOND, 0);
		if (nextFirst.before(Calendar.getInstance())) {
			nextFirst.add(Calendar.MONTH, 1);
		}
		Assertions.assertEquals(task.getNextExecutionDate(), nextFirst.getTime(),
			"Next execution date for EVERY FIRST OF THE MONTH is not next first of the month");
	}

}
