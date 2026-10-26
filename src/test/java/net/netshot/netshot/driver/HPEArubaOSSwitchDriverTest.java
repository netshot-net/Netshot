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
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeTaskContext;

@SimulatorTest
@DisplayName("HPE ArubaOS-Switch driver test")
public class HPEArubaOSSwitchDriverTest {

	private static final String DRIVER = "HPEArubaOSSwitch";

	/**
	 * Runs a snapshot of a simulated device over SSH (admin/admin).
	 * @param taskContext the task context
	 * @param container the (started) simulator container
	 * @return the device, as updated by the snapshot
	 */
	private static DeviceDraft snapshot(TaskContext taskContext, GenericContainer<?> container)
		throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		DeviceSshAccount credentials = DeviceSshAccount.builder().username("admin").password("admin").superPassword("admin").name("admin/admin").build();
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
	@DisplayName("Standalone switch")
	class StandaloneTest {

		TaskContext taskContext = new FakeTaskContext();

		@Container
		private static final GenericContainer<?> CONTAINER = DeviceSimulators.ssh("HPE_ArubaOSSwitch");

		@Test
		@DisplayName("HPE ArubaOS-Switch Snapshot")
		void snapshot() throws Exception {
			DeviceDraft device = HPEArubaOSSwitchDriverTest.snapshot(this.taskContext, CONTAINER);
			Assertions.assertEquals("sw3", device.getName(), "The device name is incorrect");
			Assertions.assertEquals("YA.16.11.0010", device.getSoftwareVersion(), "The software version is incorrect");
			Assertions.assertEquals(NetworkClass.SWITCH, device.getNetworkClass(), "The network class is incorrect");
			Assertions.assertEquals("CN00000001", device.getSerialNumber(), "The serial number is incorrect");
			Assertions.assertEquals("YA.16.10.0012",
				((DeviceTextAttribute) device.getAttribute("secondaryVersion")).text(), "The secondary image version is incorrect");
			Assertions.assertEquals(1, device.getModules().size(), "The number of modules is incorrect");
			Assertions.assertEquals("J9773A 2530-24G-PoEP", device.getModules().get(0).partNumber(),
				"The chassis part number is incorrect");
			Assertions.assertEquals("Uplink to core", device.getNetworkInterface("1").description(),
				"The port 1 description is incorrect");
			Assertions.assertEquals(Network4Address.getNetworkAddress("192.0.2.30", 24),
				device.getNetworkInterface("Vlan1").ip4Addresses().iterator().next(),
				"The Vlan1 interface IP address is incorrect");
		}
	}

	@Nested
	@DisplayName("Stack Management Commander")
	class CommanderTest {

		TaskContext taskContext = new FakeTaskContext();

		@Container
		private static final GenericContainer<?> CONTAINER = DeviceSimulators.ssh("HPE_ArubaOSSwitch", "commander");

		@Test
		@DisplayName("HPE ArubaOS-Switch Stack Commander Snapshot (member selection menu)")
		void snapshot() throws Exception {
			DeviceDraft device = HPEArubaOSSwitchDriverTest.snapshot(this.taskContext, CONTAINER);
			Assertions.assertEquals("sw3", device.getName(), "The device name is incorrect");
			Assertions.assertEquals("CN00000001", device.getSerialNumber(), "The serial number is incorrect");
			Assertions.assertNotNull(device.getNewConfig(), "The config doesn't exist");
		}
	}
}
