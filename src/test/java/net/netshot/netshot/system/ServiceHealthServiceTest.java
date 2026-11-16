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
package net.netshot.netshot.system;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.platform.lifecycle.Service;
import net.netshot.netshot.platform.lifecycle.ServiceManager;
import net.netshot.netshot.platform.lifecycle.ServiceState;
import net.netshot.netshot.platform.lifecycle.ServiceStatus;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Service health")
class ServiceHealthServiceTest {

	/**
	 * Builds a service with a health and statistics.
	 *
	 * @param id the ID of the service
	 * @param healthy whether the service is healthy
	 * @return the service
	 */
	private static Service service(String id, boolean healthy) {
		return new Service() {
			@Override
			public String id() {
				return id;
			}

			@Override
			public String name() {
				return "Service " + id;
			}

			@Override
			public void start() {
				// Nothing to start
			}

			@Override
			public void stop() {
				// Nothing to stop
			}

			@Override
			public boolean isHealthy() {
				return healthy;
			}

			@Override
			public Map<String, Object> stats() {
				return Map.of("received", 12);
			}
		};
	}

	@Test
	@DisplayName("Healthy when all the services are started and healthy, with their state and statistics")
	void healthy() throws Exception {
		ServiceManager manager = new ServiceManager(List.of(service("alpha", true), service("beta", true)));
		manager.start();
		ServiceHealthService health = new ServiceHealthService(manager);
		Assertions.assertTrue(health.isHealthy());
		ServiceStatus alpha = health.services().get(0);
		Assertions.assertEquals("alpha", alpha.id());
		Assertions.assertEquals("Service alpha", alpha.name());
		Assertions.assertEquals(ServiceState.RUNNING, alpha.state());
		Assertions.assertEquals(Map.of("received", 12), alpha.stats());
	}

	@Test
	@DisplayName("Unhealthy when a service is unhealthy")
	void unhealthyService() throws Exception {
		ServiceManager manager = new ServiceManager(List.of(service("alpha", true), service("beta", false)));
		manager.start();
		ServiceHealthService health = new ServiceHealthService(manager);
		Assertions.assertFalse(health.isHealthy());
		Assertions.assertFalse(health.services().get(1).healthy());
	}

	@Test
	@DisplayName("Unhealthy when a service is not started, healthy without services")
	void notStarted() {
		Assertions.assertFalse(new ServiceHealthService(new ServiceManager(List.of(service("alpha", true)))).isHealthy());
		Assertions.assertTrue(new ServiceHealthService(new ServiceManager(List.of())).isHealthy());
	}
}
