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
package net.netshot.netshot.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestWiring;

@DatabaseTest
@DisplayName("Demo data creator")
class DemoDataCreatorTest {

	@AfterEach
	void cleanUp() {
		TestDevices.deleteAll();
	}

	@Test
	@DisplayName("Fails (so the tool exits with an error) when the database already has data")
	void existingData() {
		TestDevices.addDomain("Existing", "Domain already in the database");
		Wiring wiring = TestWiring.get();
		DemoDataCreator creator = new DemoDataCreator(wiring.core().transactions(), wiring.core().dynamicGroups(), wiring.core().repositories());
		IllegalStateException e = Assertions.assertThrows(IllegalStateException.class, creator::createData);
		Assertions.assertEquals("Data already exist in the DB (domain found)", e.getMessage());
	}
}
