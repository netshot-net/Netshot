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

import java.util.List;

import org.glassfish.jersey.server.ApplicationHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.api.rest.v0.V0Application;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.lifecycle.Service;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.DatabaseTest.Boot;
import net.netshot.netshot.testing.TestWiring;

@DatabaseTest(boot = Boot.MIGRATED)
@TestInstance(Lifecycle.PER_CLASS)
public class WiringTest {

	@Test
	@DisplayName("Wiring.init() builds the wiring")
	void init() {
		Wiring wiring = TestWiring.set(Wiring.init());
		Assertions.assertNotNull(wiring.transactions());
	}

	@Test
	@DisplayName("The services, in start order")
	void services() {
		Wiring wiring = TestWiring.set(Wiring.init());
		Assertions.assertEquals(List.of("database", "device drivers", "OIDC IdP discovery", "task lock manager",
			"Vault token refresher", "SSH client", "task manager", "Syslog server", "SNMP trap receiver", "SSH/SCP/SFTP server",
			"Web/REST server"), wiring.services().stream().map(Service::name).toList());
	}

	@Test
	@DisplayName("The web application makes the wiring injectable into the resources")
	void injection() {
		Wiring wiring = TestWiring.set(Wiring.init());
		ApplicationHandler handler = new ApplicationHandler(new V0Application(wiring.binder()));
		Transactions injected = handler.getInjectionManager().getInstance(Transactions.class);
		Assertions.assertSame(wiring.transactions(), injected);
	}
}
