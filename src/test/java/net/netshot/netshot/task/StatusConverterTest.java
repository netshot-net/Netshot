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

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.testing.UnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

@UnitTest
@DisplayName("Task.Status persisted value (StatusConverter)")
public class StatusConverterTest {

	@BeforeAll
	static void initNetshot() {
		TestConfig.init(new Properties());
	}

	private final Status.StatusConverter converter = new Status.StatusConverter();

	@Test
	@DisplayName("Every status converts to its fixed, explicit value (not declaration order)")
	void convertsToFixedValue() {
		Assertions.assertEquals(0, converter.convertToDatabaseColumn(Status.CANCELLED));
		Assertions.assertEquals(1, converter.convertToDatabaseColumn(Status.FAILURE));
		Assertions.assertEquals(2, converter.convertToDatabaseColumn(Status.NEW));
		Assertions.assertEquals(3, converter.convertToDatabaseColumn(Status.RUNNING));
		Assertions.assertEquals(4, converter.convertToDatabaseColumn(Status.SCHEDULED));
		Assertions.assertEquals(5, converter.convertToDatabaseColumn(Status.SUCCESS));
		Assertions.assertEquals(6, converter.convertToDatabaseColumn(Status.WAITING));
		Assertions.assertEquals(7, converter.convertToDatabaseColumn(Status.DELAYED));
	}

	@Test
	@DisplayName("Round-trips through the persisted value")
	void roundTrips() {
		for (Status status : Status.values()) {
			int value = converter.convertToDatabaseColumn(status);
			Assertions.assertEquals(status, converter.convertToEntityAttribute(value));
		}
	}

	@Test
	@DisplayName("null converts both ways to null")
	void handlesNull() {
		Assertions.assertNull(converter.convertToDatabaseColumn(null));
		Assertions.assertNull(converter.convertToEntityAttribute(null));
	}

	@Test
	@DisplayName("Unknown persisted value is rejected rather than silently mismatched")
	void rejectsUnknownValue() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> Status.fromValue(99));
	}
}
