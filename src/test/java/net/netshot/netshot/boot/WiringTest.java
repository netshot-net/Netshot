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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.lifecycle.Service;
import net.netshot.netshot.platform.lifecycle.ServiceStatus;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.DatabaseTest.Boot;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.TestConfig;

@DatabaseTest(boot = Boot.MIGRATED)
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Wiring")
class WiringTest {

	@Test
	@DisplayName("The application builds the wiring, and starts the database access")
	void create() throws Exception {
		Application application = TestWiring.set(Application.create(TestConfig.store(), TestDrivers.registry()));
		application.start("database");
		Assertions.assertEquals(1, (int) application.wiring().core().transactions().read(dsl -> dsl.selectOne().fetchOne(0, Integer.class)));
	}

	@Test
	@DisplayName("The services, in start order")
	void services() {
		Application application = Application.create(TestConfig.store(), TestDrivers.registry());
		Assertions.assertEquals(List.of("database", "device-drivers", "oidc-discovery", "task-lock-manager",
			"vault-token-refresher", "ssh-client", "task-manager", "syslog-server", "snmp-trap-receiver", "ssh-server",
			"rest-server"), application.wiring().services().status().stream().map(ServiceStatus::id).toList());
	}

	@Test
	@DisplayName("Every service of the modules is managed, once, with a unique kebab-case ID")
	void everyServiceIsManaged() throws Exception {
		Properties properties = TestConfig.properties();
		// Clustering enabled (the SSH server and the REST server are by default): all the optional services are built
		properties.setProperty("netshot.cluster.enabled", "true");
		Wiring wiring = Wiring.create(new ConfigStore(Config.of(properties)), TestDrivers.registry());
		Set<Service> found = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Method accessor : Wiring.class.getDeclaredMethods()) {
			if (Modifier.isPublic(accessor.getModifiers()) && accessor.getReturnType().isRecord() && accessor.getParameterCount() == 0) {
				Object module = accessor.invoke(wiring);
				for (RecordComponent component : module.getClass().getRecordComponents()) {
					Object value = component.getAccessor().invoke(module);
					if (value instanceof Optional<?> optional) {
						value = optional.orElse(null);
					}
					if (value instanceof Service service) {
						found.add(service);
					}
				}
			}
		}
		List<String> ids = wiring.services().status().stream().map(ServiceStatus::id).toList();
		Assertions.assertEquals(found.size(), ids.size(), "managed services: " + ids);
		Assertions.assertEquals(found.stream().map(Service::id).sorted().toList(), ids.stream().sorted().toList());
		Assertions.assertEquals(ids.size(), Set.copyOf(ids).size());
		Assertions.assertTrue(ids.stream().allMatch(id -> id.matches("[a-z]+(-[a-z]+)*")), ids.toString());
		Assertions.assertTrue(ids.contains("cluster-manager"), ids.toString());
	}
}
