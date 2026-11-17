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
package net.netshot.netshot.platform.database;

import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Database service")
class DatabaseServiceTest {

	@Test
	@DisplayName("Gives the maximum size of the main connection pool (netshot.db.pooler.maxpoolsize, 50 by default)")
	void maxPoolSize() {
		Assertions.assertEquals(50, new DatabaseService(new ConfigStore(Config.empty())).maxPoolSize());
		Properties properties = new Properties();
		properties.setProperty("netshot.db.pooler.maxpoolsize", "80");
		Assertions.assertEquals(80, new DatabaseService(new ConfigStore(Config.of(properties))).maxPoolSize());
	}
}
