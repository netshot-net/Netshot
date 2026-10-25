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
import java.util.HashMap;
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
import net.netshot.netshot.devicescript.UserDeviceScript;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.task.engine.TaskContext;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeTaskContext;

@SimulatorTest
@DisplayName("CiscoIOS12 driver test")
public class CiscoIOS12DriverTest {

	TaskContext taskContext = new FakeTaskContext();

	@Container
	private static final GenericContainer<?> CONTAINER = DeviceSimulators.ssh("Cisco_IOS");

	@Test
	@DisplayName("CiscoIOS12 Snapshot")
	void snapshot() throws NoSuchMethodException, SecurityException, IOException,
		IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		DeviceSshAccount credentials = DeviceSshAccount.builder().username("admin").password("admin").superPassword("admin").name("admin/admin").build();
		Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
		Device.DeviceBuilder deviceBuilder = Device.create("CiscoIOS12", null, domain, "test").toBuilder();
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
			Assertions.assertEquals("router1", device.getName(), "The device name is incorrect");
			Assertions.assertEquals("15.5(3)S7b", device.getSoftwareVersion(), "The software version is incorrect");
			Assertions.assertEquals("Cisco CSR1000V", device.getFamily(), "The device family is incorrect");
			Assertions.assertEquals("SNMPLOCATION", device.getLocation(), "The location is incorrect");
			Assertions.assertEquals("SNMPCONTACT", device.getContact(), "The contact is incorrect");
			Assertions.assertEquals(NetworkClass.ROUTER, device.getNetworkClass(), "The network class is incorrect");
			Assertions.assertEquals(1071.0,
				((DeviceNumericAttribute) device.getAttribute("mainMemorySize")).number().doubleValue(),
				"The memory size is incorrect");
			Assertions.assertEquals("0x2102",
				((DeviceTextAttribute) device.getAttribute("configRegister")).text(),
				"The config register is incorrect");
			Assertions.assertEquals(
				Boolean.TRUE,
				((DeviceBinaryAttribute) device.getAttribute("configurationSaved")).assumption(),
				"The configuration is not seen as saved");
			Config config = device.getNewConfig();
			Assertions.assertNotNull(config, "The config doesn't exist");
			Assertions.assertEquals("admin", config.author(), "The config author is incorrect");
			Assertions.assertEquals("bootflash:packages.conf",
				((ConfigTextAttribute) config.getAttribute("iosImageFile")).text(),
				"The IOS image file is incorrect");
			Assertions.assertTrue(((ConfigLongTextAttribute) config.getAttribute("runningConfig"))
				.text().contains("ip ssh version 2"), "The running config is not correct");
			Assertions.assertEquals("96NETS96HOT",
				device.getModules().get(0).serialNumber(), "The first module serial number is incorrect");
			Assertions.assertEquals(Network4Address.getNetworkAddress("192.168.200.101", 24),
				device.getNetworkInterface("GigabitEthernet1").ip4Addresses().iterator().next(),
				"The first interface IP address is incorrect");
		}
		finally {
			accessManager.disconnectAll();
		}
	}

	@Test
	@DisplayName("CiscoIOS12 run script managing device tags and comments")
	void runScriptWithTags() throws NoSuchMethodException, SecurityException,
		IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		DeviceSshAccount credentials = DeviceSshAccount.builder().username("admin").password("admin").superPassword("admin").name("admin/admin").build();
		Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
		Device.DeviceBuilder deviceBuilder = Device.create("CiscoIOS12", null, domain, "test").toBuilder();
		List<DeviceAccess> deviceAccesses = new ArrayList<>();
		Set<String> deviceTags = new HashSet<>();
		deviceTags.add("old");
		deviceTags.add("kept");
		DeviceAccess.DeviceAccessBuilder sshAccess = DeviceAccess.of("ssh").toBuilder();
		sshAccess.address(CONTAINER.getHost());
		sshAccess.port(CONTAINER.getMappedPort(22));
		deviceAccesses.add(sshAccess.build());
		DeviceDraft device = new DeviceDraft(deviceBuilder.accesses(deviceAccesses).tags(deviceTags).build());
		String code = """
			function run(cli, device, config) {
				const tags = device.get("tags");
				if (tags.includes("old")) {
					device.remove("tag", "old");
				}
				device.add("tag", "scripted");
				device.set("comments", "Tags: " + device.get("tags").join(","));
			}
			""";
		UserDeviceScript script = new UserDeviceScript(TestDrivers.registry(), "CiscoIOS12", code, this.taskContext);
		script.setUserInputValues(new HashMap<>());
		AccessManager accessManager = new AccessManager(TestDrivers.registry(), null, device, null, this.taskContext, Set.of(credentials));
		Method runMethod = UserDeviceScript.class.getDeclaredMethod("run", DeviceDraft.class, AccessManager.class);
		runMethod.setAccessible(true);
		try {
			runMethod.invoke(script, device, accessManager);
			Assertions.assertEquals(Set.of("kept", "scripted"), device.getTags(), "The device tags are incorrect");
			Assertions.assertEquals("Tags: kept,scripted", device.getComments(), "The device comments are incorrect");
		}
		finally {
			accessManager.disconnectAll();
		}
	}
}
