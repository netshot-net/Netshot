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
package net.netshot.netshot.driver;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeTaskContext;

@ScriptEngineTest
@DisplayName("Driver SNMP auto-discovery")
class SnmpAutoDiscoverTest {

	private static boolean discovers(String driverName, String sysObjectId, String sysDesc) {
		DeviceDriver driver = TestDrivers.registry().getDriverByName(driverName);
		Assertions.assertNotNull(driver, "Driver " + driverName + " not found");
		return driver.snmpAutoDiscover(sysObjectId, sysDesc, new FakeTaskContext());
	}

	@Test
	@DisplayName("HPE ArubaOS-Switch discovers HP-branded switches")
	void arubaOsSwitchHp() {
		Assertions.assertTrue(discovers("HPEArubaOSSwitch", "1.3.6.1.4.1.11.2.3.7.11.181",
			"HP J9772A 2530-48G-PoEP Switch, revision YA.16.10.0010, ROM YA.15.20 (/ws/swbuildm/rel_venice_qaoff/code/build/lakes(swbuildm_rel_venice_qaoff_rel_venice))"));
	}

	@Test
	@DisplayName("HPE ArubaOS-Switch discovers older ProCurve-branded switches (GitHub #54)")
	void arubaOsSwitchProCurve() {
		Assertions.assertTrue(discovers("HPEArubaOSSwitch", "1.3.6.1.4.1.11.2.3.7.11.88",
			"ProCurve J9279A Switch 2510G-24, revision Y.11.41, ROM N.10.02 (/sw/code/build/cod)"));
	}

	@Test
	@DisplayName("HPE ArubaOS-Switch ignores other HP devices")
	void arubaOsSwitchOther() {
		Assertions.assertFalse(discovers("HPEArubaOSSwitch", "1.3.6.1.4.1.11.2.3.7.11.88", "Linux printer"));
		Assertions.assertFalse(discovers("HPEArubaOSSwitch", "1.3.6.1.4.1.11.2.3.9.1",
			"ProCurve J9279A Switch 2510G-24, revision Y.11.41, ROM N.10.02 (/sw/code/build/cod)"));
	}
}
