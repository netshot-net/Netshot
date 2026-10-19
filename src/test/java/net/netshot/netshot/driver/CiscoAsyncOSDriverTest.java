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
import java.util.Set;

import org.hibernate.Session;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import net.netshot.netshot.FakeTaskContext;
import net.netshot.netshot.TestDrivers;
import net.netshot.netshot.WithDeviceSimulatorTest;
import net.netshot.netshot.access.AccessManager;
import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.devicescript.SnapshotDeviceScript;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.task.engine.TaskContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cisco AsyncOS driver test")
@Testcontainers
public class CiscoAsyncOSDriverTest extends WithDeviceSimulatorTest {

	TaskContext taskContext = new FakeTaskContext();

	@Container
	private static final GenericContainer<?> CONTAINER = buildSshDeviceSimulator("Cisco_AsyncOS");

	@Test
	@DisplayName("Cisco AsyncOS Snapshot")
	void snapshot() throws NoSuchMethodException, SecurityException, IOException,
		IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		DeviceSshAccount credentials = new DeviceSshAccount("admin", "admin", "admin", "admin/admin");
		Session nullSession = null;
		Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
		Device device = new Device("CiscoAsyncOS", null, domain, "test");
		DeviceAccess sshAccess = new DeviceAccess(device, "ssh");
		sshAccess.setAddress(CONTAINER.getHost());
		sshAccess.setPort(CONTAINER.getMappedPort(22));
		device.getAccesses().add(sshAccess);
		SnapshotDeviceScript script = new SnapshotDeviceScript(TestDrivers.registry(), this.taskContext);
		AccessManager accessManager = new AccessManager(TestDrivers.registry(), nullSession, device, null, this.taskContext, Set.of(credentials));
		Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", Session.class,
			Device.class, AccessManager.class);
		runMethod.setAccessible(true);
		try {
			runMethod.invoke(script, nullSession, device, accessManager);
			Config config = device.getLastConfig();
			Assertions.assertNotNull(config, "The config doesn't exist");
			Assertions.assertEquals("esa.netshot.lab", device.getName(), "The device name is incorrect");
		}
		finally {
			accessManager.disconnectAll();
		}
	}
}
