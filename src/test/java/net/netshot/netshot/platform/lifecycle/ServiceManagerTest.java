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
package net.netshot.netshot.platform.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Service manager")
class ServiceManagerTest {

	/** The start and stop calls, in order. */
	private final List<String> calls = new ArrayList<>();

	private Service service(String name, boolean failStart, boolean failStop) {
		return new Service() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public void start() {
				calls.add("start " + name);
				if (failStart) {
					throw new IllegalStateException("Start failure of " + name);
				}
			}

			@Override
			public void stop() {
				calls.add("stop " + name);
				if (failStop) {
					throw new IllegalStateException("Stop failure of " + name);
				}
			}

			@Override
			public Map<String, Object> stats() {
				return Map.of("name", name);
			}
		};
	}

	@Test
	@DisplayName("Services are started in order, stopped in the reverse order, once")
	void startAndStopInOrder() throws Exception {
		ServiceManager manager = new ServiceManager(List.of(
			service("a", false, false), service("b", false, false), service("c", false, false)));
		manager.start();
		Assertions.assertTrue(manager.isHealthy());
		Assertions.assertEquals(List.of(ServiceState.RUNNING, ServiceState.RUNNING, ServiceState.RUNNING),
			manager.status().stream().map(ServiceStatus::state).toList());
		Assertions.assertEquals(Map.of("name", "b"), manager.status().get(1).stats());
		manager.stop();
		manager.stop();
		Assertions.assertEquals(List.of("start a", "start b", "start c", "stop c", "stop b", "stop a"), this.calls);
		Assertions.assertEquals(List.of(ServiceState.STOPPED, ServiceState.STOPPED, ServiceState.STOPPED),
			manager.status().stream().map(ServiceStatus::state).toList());
		Assertions.assertFalse(manager.isHealthy());
	}

	@Test
	@DisplayName("A start failure stops the services already started, and nothing more is started")
	void startFailureStopsStarted() {
		ServiceManager manager = new ServiceManager(List.of(
			service("a", false, false), service("b", false, false), service("c", true, false), service("d", false, false)));
		ServiceStartException thrown = Assertions.assertThrows(ServiceStartException.class, manager::start);
		Assertions.assertTrue(thrown.getMessage().contains("c"));
		Assertions.assertEquals(List.of("start a", "start b", "start c", "stop b", "stop a"), this.calls);
		List<ServiceStatus> status = manager.status();
		Assertions.assertEquals(ServiceState.FAILED, status.get(2).state());
		Assertions.assertEquals("Start failure of c", status.get(2).error());
		Assertions.assertEquals(ServiceState.NEW, status.get(3).state());
	}

	@Test
	@DisplayName("A stop failure doesn't prevent the other services from being stopped")
	void stopFailureContinues() throws Exception {
		ServiceManager manager = new ServiceManager(List.of(
			service("a", false, false), service("b", false, true), service("c", false, false)));
		manager.start();
		manager.stop();
		Assertions.assertEquals(List.of("start a", "start b", "start c", "stop c", "stop b", "stop a"), this.calls);
		Assertions.assertEquals(ServiceState.FAILED, manager.status().get(1).state());
		Assertions.assertEquals(ServiceState.STOPPED, manager.status().get(0).state());
	}

	@Test
	@DisplayName("The shutdown hook stops the services")
	void shutdownHook() throws Exception {
		ServiceManager manager = new ServiceManager(List.of(service("a", false, false)));
		manager.start();
		Thread hook = manager.shutdownHook();
		hook.start();
		hook.join();
		Assertions.assertEquals(List.of("start a", "stop a"), this.calls);
	}
}
