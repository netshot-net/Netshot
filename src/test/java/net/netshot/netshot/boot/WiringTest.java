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
package net.netshot.netshot.boot;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.platform.lifecycle.ServiceStatus;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.DatabaseTest.Boot;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestWiring;

@DatabaseTest(boot = Boot.MIGRATED)
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Wiring")
class WiringTest {

	@Test
	@DisplayName("The application builds the wiring, and starts the database access")
	void create() throws Exception {
		Application application = TestWiring.set(Application.create(Netshot.config(), TestDrivers.registry()));
		application.start("database");
		Assertions.assertEquals(1, (int) application.wiring().transactions().read(dsl -> dsl.selectOne().fetchOne(0, Integer.class)));
	}

	@Test
	@DisplayName("The services, in start order")
	void services() {
		Application application = Application.create(Netshot.config(), TestDrivers.registry());
		Assertions.assertEquals(List.of("database", "device-drivers", "oidc-discovery", "task-lock-manager",
			"vault-token-refresher", "ssh-client", "task-manager", "syslog-server", "snmp-trap-receiver", "ssh-server",
			"rest-server"), application.services().status().stream().map(ServiceStatus::id).toList());
	}
}
