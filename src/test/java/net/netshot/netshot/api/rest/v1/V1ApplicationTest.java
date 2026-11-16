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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.api.rest.v1.compliance.ComplianceRefs;
import net.netshot.netshot.api.rest.v1.device.DeviceMapper;
import net.netshot.netshot.api.rest.v1.task.TaskMapper;
import net.netshot.netshot.api.rest.v1.task.TaskResponses;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestWiring;

@DatabaseTest
@DisplayName("v1 web application")
class V1ApplicationTest {

	@Test
	@DisplayName("Makes the wiring injectable into the resources")
	void injection() {
		ApplicationHandler handler = new ApplicationHandler(new V1Application(TestWiring.get().binder(), new V1Links("/api")));
		Transactions injected = handler.getInjectionManager().getInstance(Transactions.class);
		Assertions.assertSame(TestWiring.get().transactions(), injected);
	}

	@Test
	@DisplayName("Injects the links, the mappers and the response helpers, one of each")
	void mappers() {
		V1Links links = new V1Links("/api");
		InjectionManager injection = new ApplicationHandler(new V1Application(TestWiring.get().binder(), links)).getInjectionManager();
		Assertions.assertSame(links, injection.getInstance(V1Links.class));
		for (Class<?> type : List.of(DeviceMapper.class, TaskMapper.class, ComplianceRefs.class, TaskResponses.class)) {
			Object instance = injection.getInstance(type);
			Assertions.assertNotNull(instance, type.getSimpleName());
			Assertions.assertSame(instance, injection.getInstance(type), type.getSimpleName());
		}
		Assertions.assertEquals("/api/v1/devices/3", injection.getInstance(DeviceMapper.class).byId(3).url());
	}
}
