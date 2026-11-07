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
package net.netshot.netshot.compliance;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.testing.UnitTest;

@UnitTest
public class ExemptionTest {

	@Test
	@DisplayName("An exemption is active until its expiration date, and never without date")
	void active() {
		Instant now = Instant.parse("2026-11-07T12:00:00Z");
		Assertions.assertTrue(new Exemption(1, 2, now.plus(Duration.ofDays(1))).isActive(now));
		Assertions.assertFalse(new Exemption(1, 2, now).isActive(now));
		Assertions.assertFalse(new Exemption(1, 2, now.minus(Duration.ofDays(1))).isActive(now));
		Assertions.assertFalse(new Exemption(1, 2, null).isActive(now));
	}

}
