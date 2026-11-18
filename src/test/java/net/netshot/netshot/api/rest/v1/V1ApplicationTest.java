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
package net.netshot.netshot.api.rest.v1;

import java.util.List;

import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.server.ApplicationHandler;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.api.rest.v1.compliance.ComplianceRefs;
import net.netshot.netshot.api.rest.v1.device.DeviceMapper;
import net.netshot.netshot.api.rest.v1.task.TaskMapper;
import net.netshot.netshot.api.rest.v1.task.TaskResponses;
import net.netshot.netshot.api.rest.ApiEndpoint;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestWiring;

@DatabaseTest
@DisplayName("v1 web application")
class V1ApplicationTest {

	@Test
	@DisplayName("Makes the wiring injectable into the resources")
	void injection() {
		ApplicationHandler handler = new ApplicationHandler(new V1Application(TestWiring.get().api().restDependencies().binder(),
			new ApiEndpoint("/api", "NetshotSessionID")));
		ConfigStore injected = handler.getInjectionManager().getInstance(ConfigStore.class);
		Assertions.assertSame(TestWiring.get().core().config(), injected);
	}

	@Test
	@DisplayName("Injects the links, the mappers and the response helpers, one of each")
	void mappers() {
		ApiEndpoint api = new ApiEndpoint("/api", "NetshotSessionID");
		InjectionManager injection = new ApplicationHandler(new V1Application(TestWiring.get().api().restDependencies().binder(), api))
			.getInjectionManager();
		Assertions.assertSame(api, injection.getInstance(ApiEndpoint.class));
		Assertions.assertEquals("/api/v1", injection.getInstance(V1Links.class).base());
		Assertions.assertSame(injection.getInstance(V1Links.class), injection.getInstance(V1Links.class));
		for (Class<?> type : List.of(DeviceMapper.class, TaskMapper.class, ComplianceRefs.class, TaskResponses.class)) {
			Object instance = injection.getInstance(type);
			Assertions.assertNotNull(instance, type.getSimpleName());
			Assertions.assertSame(instance, injection.getInstance(type), type.getSimpleName());
		}
		Assertions.assertEquals("/api/v1/devices/3", injection.getInstance(DeviceMapper.class).byId(3).url());
	}

	@Test
	@DisplayName("Every resource gets its dependencies: the REST dependencies of the wiring hold all that the resources inject")
	void everyResourceIsInjectable() {
		ApplicationHandler handler = new ApplicationHandler(new V1Application(TestWiring.get().api().restDependencies().binder(),
			new ApiEndpoint("/api", "NetshotSessionID")));
		List<Class<?>> resources = handler.getConfiguration().getClasses().stream()
			.filter(type -> type.isAnnotationPresent(Path.class)).toList();
		Assertions.assertTrue(resources.size() > 20, resources.toString());
		for (Class<?> resource : resources) {
			Assertions.assertNotNull(handler.getInjectionManager().createAndInitialize(resource), resource.getSimpleName());
		}
	}
}
