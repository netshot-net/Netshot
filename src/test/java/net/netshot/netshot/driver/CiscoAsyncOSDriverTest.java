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
package net.netshot.netshot.driver;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import net.netshot.netshot.access.AccessManager;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.devicescript.SnapshotDeviceScript;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeTaskContext;

@SimulatorTest
@DisplayName("Cisco AsyncOS driver test")
public class CiscoAsyncOSDriverTest {

	TaskContext taskContext = new FakeTaskContext();

	@Container
	private static final GenericContainer<?> CONTAINER = DeviceSimulators.ssh("Cisco_AsyncOS");

	@Test
	@DisplayName("Cisco AsyncOS Snapshot")
	void snapshot() throws NoSuchMethodException, SecurityException, IOException,
		IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		DeviceSshAccount credentials = DeviceSshAccount.builder().username(Secret.local("admin")).password(Secret.local("admin")).superPassword(Secret.local("admin")).name("admin/admin")
			.build();
		Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
		Device.DeviceBuilder deviceBuilder = Device.create("CiscoAsyncOS", null, domain, "test").toBuilder();
		List<DeviceAccess> deviceAccesses = new ArrayList<>();
		Set<String> deviceTags = new HashSet<>();
		DeviceAccess.DeviceAccessBuilder sshAccess = DeviceAccess.of("ssh").toBuilder();
		sshAccess.address(CONTAINER.getHost());
		sshAccess.port(CONTAINER.getMappedPort(22));
		deviceAccesses.add(sshAccess.build());
		DeviceDraft device = new DeviceDraft(deviceBuilder.accesses(deviceAccesses).tags(deviceTags).build());
		SnapshotDeviceScript script = new SnapshotDeviceScript(TestDrivers.registry(), TestConnectors.local(), null, this.taskContext);
		AccessManager accessManager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, this.taskContext, Set.of(credentials));
		Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", DeviceDraft.class, AccessManager.class);
		runMethod.setAccessible(true);
		try {
			runMethod.invoke(script, device, accessManager);
			Config config = device.getNewConfig();
			Assertions.assertNotNull(config, "The config doesn't exist");
			Assertions.assertEquals("esa.netshot.lab", device.getName(), "The device name is incorrect");
		}
		finally {
			accessManager.disconnectAll();
		}
	}
}
