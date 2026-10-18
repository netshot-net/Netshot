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
package net.netshot.netshot.boot;

import java.util.Properties;

import org.glassfish.jersey.server.ApplicationHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.WithDatabaseTest;
import net.netshot.netshot.api.rest.NetshotWebApplication;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Transactions;

@TestInstance(Lifecycle.PER_CLASS)
public class WiringTest extends WithDatabaseTest {

	@BeforeAll
	void initDb() throws Exception {
		Properties config = getFreshDatabaseConfig("wiringtest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "WARN");
		TestConfig.init(config);
		Database.update();
	}

	@Test
	@DisplayName("Wiring.init() builds the wiring, available through Wiring.get()")
	void getAfterInit() {
		Wiring wiring = Wiring.init();
		Assertions.assertSame(wiring, Wiring.get());
		Assertions.assertNotNull(wiring.transactions());
	}

	@Test
	@DisplayName("The web application makes the wiring injectable into the resources")
	void injection() {
		Wiring wiring = Wiring.init();
		ApplicationHandler handler = new ApplicationHandler(new NetshotWebApplication(wiring.binder()));
		Transactions injected = handler.getInjectionManager().getInstance(Transactions.class);
		Assertions.assertSame(wiring.transactions(), injected);
	}
}
