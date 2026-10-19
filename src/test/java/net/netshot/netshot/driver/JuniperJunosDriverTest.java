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
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.task.engine.TaskContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JuniperJunos driver test")
@Testcontainers
public class JuniperJunosDriverTest extends WithDeviceSimulatorTest {

	TaskContext taskContext = new FakeTaskContext();

	@Container
	private static final GenericContainer<?> CONTAINER = buildSshDeviceSimulator("Juniper_Junos");

	@Test
	@DisplayName("JuniperJunos Snapshot")
	void snapshot() throws NoSuchMethodException, SecurityException, IOException,
		IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		DeviceSshAccount credentials = new DeviceSshAccount("admin", "admin", "admin", "admin/admin");
		Session nullSession = null;
		Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
		Device device = new Device("JuniperJunos", null, domain, "test");
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
			Assertions.assertEquals("mx1", device.getName(), "The device name is incorrect");
			Assertions.assertEquals("21.4R3.15", device.getSoftwareVersion(), "The software version is incorrect");
			Assertions.assertEquals("Juniper MX240", device.getFamily(), "The device family is incorrect");
			Assertions.assertEquals("Room 42", device.getLocation(), "The location is incorrect");
			Assertions.assertEquals("netshot@example.com", device.getContact(), "The contact is incorrect");
			Assertions.assertEquals(NetworkClass.ROUTER, device.getNetworkClass(), "The network class is incorrect");
			Config config = device.getLastConfig();
			Assertions.assertNotNull(config, "The config doesn't exist");
			Assertions.assertEquals("admin", config.getAuthor(), "The config author is incorrect");
			Assertions.assertTrue(((ConfigLongTextAttribute) config.getAttribute("configuration"))
				.getLongText().getText().contains("host-name mx1"), "The configuration is not correct");
			Assertions.assertEquals(2, device.getModules().size(), "The number of modules is incorrect");
			Assertions.assertEquals("JN123F456789", device.getModules().get(0).getSerialNumber(),
				"The chassis module serial number is incorrect");
			Assertions.assertEquals(Network4Address.getNetworkAddress("10.0.0.1", 30),
				device.getNetworkInterface("ge-0/0/0.0").getIp4Addresses().iterator().next(),
				"The ge-0/0/0.0 interface IP address is incorrect");
		}
		finally {
			accessManager.disconnectAll();
		}
	}
}
