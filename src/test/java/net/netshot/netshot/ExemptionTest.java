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
package net.netshot.netshot;

import java.util.Date;

import net.netshot.netshot.compliance.rules.TextRule;
import net.netshot.netshot.device.Device;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ExemptionTest {

	@Test
	@DisplayName("A device is exempted until the expiration date, and never by an exemption without date")
	void exempted() {
		Device device = FakeDeviceFactory.getFakeCiscoIosDevice();
		TextRule future = new TextRule("Future", null);
		TextRule past = new TextRule("Past", null);
		TextRule noDate = new TextRule("No date", null);
		future.setId(1);
		past.setId(2);
		noDate.setId(3);
		device.addComplianceException(future, new Date(System.currentTimeMillis() + 86_400_000L));
		device.addComplianceException(past, new Date(System.currentTimeMillis() - 86_400_000L));
		device.addComplianceException(noDate, null);
		Assertions.assertTrue(device.isExempted(future));
		Assertions.assertFalse(device.isExempted(past));
		Assertions.assertFalse(device.isExempted(noDate));
	}

}
