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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.lifecycle.ServiceState;
import net.netshot.netshot.platform.lifecycle.ServiceStatus;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDatabase;
import net.netshot.netshot.testing.TestDrivers;

@DatabaseTest
@DisplayName("Application")
class ApplicationTest {

	private static Map<String, ServiceState> states(Application application) {
		Map<String, ServiceState> states = new LinkedHashMap<>();
		for (ServiceStatus status : application.services().status()) {
			states.put(status.id(), status.state());
		}
		return states;
	}

	@Test
	@DisplayName("Is built without any I/O: nothing is started, even with an unreachable database")
	void createWithoutSideEffect() {
		Properties properties = new Properties();
		properties.setProperty("netshot.db.url", "jdbc:postgresql://127.0.0.1:1/unreachable");
		Application application = Application.create(new ConfigStore(Config.of(properties)), TestDrivers.registry());
		Assertions.assertTrue(states(application).values().stream().allMatch(ServiceState.NEW::equals));
		application.close();
	}

	@Test
	@DisplayName("Starts the services it is asked for, and stops them on close (once)")
	void startSomeAndClose(TestDatabase database) throws Exception {
		Application application = Application.create(new ConfigStore(Config.of(database.netshotConfig())), TestDrivers.registry());
		application.start("database");
		Assertions.assertEquals(ServiceState.RUNNING, states(application).get("database"));
		Assertions.assertEquals(ServiceState.NEW, states(application).get("task-manager"));
		Assertions.assertEquals(1, (int) application.wiring().core().transactions().read(dsl -> dsl.selectOne().fetchOne(0, Integer.class)));
		application.close();
		application.close();
		Assertions.assertEquals(ServiceState.STOPPED, states(application).get("database"));
		Assertions.assertEquals(List.of(), states(application).entrySet().stream()
			.filter(e -> e.getValue() == ServiceState.RUNNING).toList());
	}
}
