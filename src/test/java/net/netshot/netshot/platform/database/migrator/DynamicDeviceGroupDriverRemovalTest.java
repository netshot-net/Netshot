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
package net.netshot.netshot.platform.database.migrator;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestDrivers;

/**
 * The driver descriptions used by {@link DynamicDeviceGroupDriverRemoval}: a database upgraded today from 0.23 or
 * older runs this migration with the current drivers, whose descriptions the migrated group queries must name.
 * The migration of the rows themselves is tested by DatabaseMigrationTest.
 */
@ScriptEngineTest
@DisplayName("Driver descriptions of the dynamic group driver removal migration")
class DynamicDeviceGroupDriverRemovalTest {

	@Test
	@DisplayName("Each well-known driver which is still shipped has the description the migration gives")
	void descriptionsOfTheShippedDrivers() {
		DriverRegistry drivers = TestDrivers.registry();
		int shipped = 0;
		for (Map.Entry<String, String> driver : DynamicDeviceGroupDriverRemoval.WELL_KNOWN_DRIVERS.entrySet()) {
			DeviceDriver current = drivers.getDriverByName(driver.getKey());
			if (current == null) {
				continue;
			}
			shipped++;
			Assertions.assertEquals(current.getDescription(), driver.getValue(), "Description of the driver " + driver.getKey());
		}
		Assertions.assertTrue(shipped > 30, "Most well-known drivers are still shipped");
	}
}
