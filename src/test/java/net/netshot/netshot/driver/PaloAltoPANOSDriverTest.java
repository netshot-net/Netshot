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
import java.text.ParseException;
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
import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.devicescript.SnapshotDeviceScript;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.device.PhysicalAddress;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.task.engine.TaskContext;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeTaskContext;

@SimulatorTest
@DisplayName("Palo Alto PAN-OS driver test")
public class PaloAltoPANOSDriverTest {

	TaskContext taskContext = new FakeTaskContext();

	@Container
	private static final GenericContainer<?> CONTAINER = DeviceSimulators.ssh("Palo-Alto_PAN-OS");

	@Test
	@DisplayName("Palo Alto PAN-OS Snapshot")
	void snapshot() throws NoSuchMethodException, SecurityException, IOException,
		IllegalAccessException, IllegalArgumentException, InvocationTargetException, ParseException {
		DeviceSshAccount credentials = DeviceSshAccount.builder().username("admin").password("admin").superPassword("admin").name("admin/admin").build();
		Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
		Device.DeviceBuilder deviceBuilder = Device.create("PaloAltoPANOS", null, domain, "test").toBuilder();
		List<DeviceAccess> deviceAccesses = new ArrayList<>();
		Set<String> deviceTags = new HashSet<>();
		DeviceAccess.DeviceAccessBuilder sshAccess = DeviceAccess.of("ssh").toBuilder();
		sshAccess.address(CONTAINER.getHost());
		sshAccess.port(CONTAINER.getMappedPort(22));
		deviceAccesses.add(sshAccess.build());
		DeviceDraft device = new DeviceDraft(deviceBuilder.accesses(deviceAccesses).tags(deviceTags).build());
		SnapshotDeviceScript script = new SnapshotDeviceScript(TestDrivers.registry(), null, this.taskContext);
		AccessManager accessManager = new AccessManager(TestDrivers.registry(), null, device, null, this.taskContext, Set.of(credentials));
		Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", DeviceDraft.class, AccessManager.class);
		runMethod.setAccessible(true);
		try {
			runMethod.invoke(script, device, accessManager);
			Assertions.assertEquals("fw1", device.getName(), "The device name is incorrect");
			Assertions.assertEquals("11.1.6-h3", device.getSoftwareVersion(), "The software version is incorrect");
			Assertions.assertEquals("400", device.getFamily(), "The device family is incorrect");
			Assertions.assertEquals(NetworkClass.FIREWALL, device.getNetworkClass(), "The network class is incorrect");
			Assertions.assertEquals("012345678901", device.getSerialNumber(), "The serial number is incorrect");
			Config config = device.getNewConfig();
			Assertions.assertNotNull(config, "The config doesn't exist");
			Assertions.assertTrue(((ConfigLongTextAttribute) config.getAttribute("configuration"))
				.text().startsWith("config {"), "The configuration is not correct");
			Assertions.assertTrue(((ConfigLongTextAttribute) config.getAttribute("mergedConfiguration"))
				.text().contains("PANORAMA-NTP"), "The merged configuration is not correct");
			Assertions.assertEquals("11.1.6-h3", ((ConfigTextAttribute) config.getAttribute("panOsVersion")).text(),
				"The PAN-OS version config attribute is incorrect");
			Assertions.assertEquals(1, device.getModules().size(), "The number of modules is incorrect");
			Assertions.assertEquals("012345678901", device.getModules().get(0).serialNumber(),
				"The chassis module serial number is incorrect");
			Assertions.assertEquals(8, device.getNetworkInterfaces().size(), "The number of interfaces is incorrect");
			// First row of the table, used to be skipped
			Assertions.assertEquals(Network4Address.getNetworkAddress("198.51.100.2", 29),
				device.getNetworkInterface("ethernet1/1").ip4Addresses().iterator().next(),
				"The ethernet1/1 interface IP address is incorrect");
			Assertions.assertEquals(new PhysicalAddress("00:00:5e:00:53:11"),
				device.getNetworkInterface("ethernet1/1").physicalAddress(),
				"The ethernet1/1 interface MAC address is incorrect");
			Assertions.assertEquals("1", device.getNetworkInterface("ethernet1/1").virtualDevice(),
				"The ethernet1/1 interface vsys is incorrect");
			Assertions.assertTrue(device.getNetworkInterface("ethernet1/2").ip4Addresses().isEmpty(),
				"The ethernet1/2 interface shouldn't have any IP address");
			Assertions.assertEquals(Network4Address.getNetworkAddress("172.16.10.1", 26),
				device.getNetworkInterface("ethernet1/2.810").ip4Addresses().iterator().next(),
				"The ethernet1/2.810 interface IP address is incorrect");
			// Interface without zone (empty column), used to lose its IP address
			Assertions.assertEquals(Network4Address.getNetworkAddress("10.0.0.11", 24),
				device.getNetworkInterface("ethernet1/7").ip4Addresses().iterator().next(),
				"The ethernet1/7 interface IP address is incorrect");
			Assertions.assertEquals(new PhysicalAddress("00:00:5e:00:53:17"),
				device.getNetworkInterface("ethernet1/7").physicalAddress(),
				"The ethernet1/7 interface MAC address is incorrect");
			Assertions.assertTrue(device.getNetworkInterface("tunnel").ip4Addresses().isEmpty(),
				"The tunnel interface shouldn't have any IP address");
		}
		finally {
			accessManager.disconnectAll();
		}
	}
}
