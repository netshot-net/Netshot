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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import net.netshot.netshot.access.AccessManager;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.devicescript.SnapshotDeviceScript;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeTaskContext;

@SimulatorTest
@DisplayName("Cisco Sx driver test")
public class CiscoSxDriverTest {

	private static final String DRIVER = "CiscoSx";

	/**
	 * Runs a snapshot of a simulated device over SSH (admin/admin).
	 * @param taskContext the task context
	 * @param container the (started) simulator container
	 * @return the device, as updated by the snapshot
	 */
	private static DeviceDraft snapshot(TaskContext taskContext, GenericContainer<?> container)
		throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		DeviceSshAccount credentials = DeviceSshAccount.builder().username(Secret.local("admin")).password(Secret.local("admin")).superPassword(Secret.local("admin")).name("admin/admin")
			.build();
		Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
		Device.DeviceBuilder deviceBuilder = Device.create(DRIVER, null, domain, "test").toBuilder();
		List<DeviceAccess> deviceAccesses = new ArrayList<>();
		Set<String> deviceTags = new HashSet<>();
		DeviceAccess.DeviceAccessBuilder sshAccess = DeviceAccess.of("ssh").toBuilder();
		sshAccess.address(container.getHost());
		sshAccess.port(container.getMappedPort(22));
		deviceAccesses.add(sshAccess.build());
		DeviceDraft device = new DeviceDraft(deviceBuilder.accesses(deviceAccesses).tags(deviceTags).build());
		SnapshotDeviceScript script = new SnapshotDeviceScript(TestDrivers.registry(), TestConnectors.local(), null, taskContext);
		AccessManager accessManager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, taskContext, Set.of(credentials));
		Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", DeviceDraft.class, AccessManager.class);
		runMethod.setAccessible(true);
		try {
			runMethod.invoke(script, device, accessManager);
		}
		finally {
			accessManager.disconnectAll();
		}
		return device;
	}

	@Nested
	@DisplayName("SG300")
	class Sx300Test {

		TaskContext taskContext = new FakeTaskContext();

		@Container
		private static final GenericContainer<?> CONTAINER = DeviceSimulators.ssh("Cisco_Sx");

		@Test
		@DisplayName("Cisco Sx (SG300) Snapshot")
		void snapshot() throws Exception {
			DeviceDraft device = CiscoSxDriverTest.snapshot(this.taskContext, CONTAINER);
			Assertions.assertEquals("sw1", device.getName(), "The device name is incorrect");
			Assertions.assertEquals("1.4.11.5", device.getSoftwareVersion(), "The software version is incorrect");
			Assertions.assertEquals("Cisco SG300-28", device.getFamily(), "The device family is incorrect");
			Assertions.assertEquals(NetworkClass.SWITCH, device.getNetworkClass(), "The network class is incorrect");
			Assertions.assertEquals("PSZ00000300", device.getSerialNumber(), "The serial number is incorrect");
			Assertions.assertEquals("Room 42", device.getLocation(), "The location is incorrect");
			Assertions.assertEquals("netshot@example.com", device.getContact(), "The contact is incorrect");
			Assertions.assertEquals("00:00:5e:00:53:20",
				((DeviceTextAttribute) device.getAttribute("systemMac")).text(), "The system MAC is incorrect");
			Assertions.assertTrue(((DeviceBinaryAttribute) device.getAttribute("configurationSaved")).assumption(),
				"The configuration should be seen as saved");
			Config config = device.getNewConfig();
			Assertions.assertNotNull(config, "The config doesn't exist");
			Assertions.assertEquals("1.3.5.06", ((ConfigTextAttribute) config.getAttribute("bootVersion")).text(),
				"The boot version is incorrect");
			Assertions.assertEquals(1, device.getModules().size(), "The number of modules is incorrect");
			Assertions.assertEquals("SG300-28-K9", device.getModules().get(0).partNumber(),
				"The module part number is incorrect");
			Assertions.assertEquals(Network4Address.getNetworkAddress("192.0.2.20", 24),
				device.getNetworkInterface("vlan 1").ip4Addresses().iterator().next(),
				"The vlan 1 interface IP address is incorrect");
			Assertions.assertFalse(device.getNetworkInterface("vlan 10").enabled(), "The vlan 10 interface should be disabled");
		}
	}

	@Nested
	@DisplayName("Sx220")
	class Sx220Test {

		TaskContext taskContext = new FakeTaskContext();

		@Container
		private static final GenericContainer<?> CONTAINER = DeviceSimulators.ssh("Cisco_Sx", "sx220");

		@Test
		@DisplayName("Cisco Sx220 Snapshot")
		void snapshot() throws Exception {
			DeviceDraft device = CiscoSxDriverTest.snapshot(this.taskContext, CONTAINER);
			// No "show system" on Sx220: name from the config, the rest from "show version"
			Assertions.assertEquals("sw2", device.getName(), "The device name is incorrect");
			Assertions.assertEquals("1.1.0.14", device.getSoftwareVersion(), "The software version is incorrect");
			Assertions.assertEquals("Cisco SG220-26P", device.getFamily(), "The device family is incorrect");
			Assertions.assertEquals("PSZ00000220", device.getSerialNumber(), "The serial number is incorrect");
			Assertions.assertEquals("00:00:5e:00:53:20",
				((DeviceTextAttribute) device.getAttribute("systemMac")).text(), "The system MAC is incorrect");
			Config config = device.getNewConfig();
			Assertions.assertNotNull(config, "The config doesn't exist");
			Assertions.assertEquals("1.0.0.6", ((ConfigTextAttribute) config.getAttribute("bootVersion")).text(),
				"The boot version is incorrect");
			Assertions.assertEquals(1, device.getModules().size(), "The number of modules is incorrect");
			Assertions.assertEquals("SG220-26P-K9", device.getModules().get(0).partNumber(),
				"The module part number is incorrect");
			Assertions.assertEquals("PSZ00000220", device.getModules().get(0).serialNumber(),
				"The module serial number is incorrect");
			Assertions.assertEquals(Network4Address.getNetworkAddress("192.0.2.20", 24),
				device.getNetworkInterface("vlan 1").ip4Addresses().iterator().next(),
				"The vlan 1 interface IP address is incorrect");
		}
	}
}
