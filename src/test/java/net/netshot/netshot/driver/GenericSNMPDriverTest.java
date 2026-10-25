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
import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.devicescript.SnapshotDeviceScript;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeTaskContext;
import net.netshot.netshot.vault.SecretResolver;

@SimulatorTest
@DisplayName("GenericSNMP driver test")
public class GenericSNMPDriverTest {

	TaskContext taskContext = new FakeTaskContext();

	@Container
	private static final GenericContainer<?> CONTAINER = DeviceSimulators.snmp("myhost");

	@Test
	@DisplayName("GenericSNMP Snapshot (real snmpd CONTAINER)")
	void snapshot() throws NoSuchMethodException, SecurityException, IOException,
		IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		DeviceSnmpv2cCommunity community = DeviceSnmpv2cCommunity.builder().community("public").name("community1").build();
		int port = DeviceSimulators.mappedUdpPort(CONTAINER, 161);
		Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
		Device.DeviceBuilder deviceBuilder = Device.create("GenericSNMP", null, domain, "test").toBuilder();
		List<DeviceAccess> deviceAccesses = new ArrayList<>();
		Set<String> deviceTags = new HashSet<>();
		DeviceAccess.DeviceAccessBuilder snmpAccess = DeviceAccess.of("snmpv2c").toBuilder();
		snmpAccess.address(CONTAINER.getHost());
		snmpAccess.port(port);
		deviceAccesses.add(snmpAccess.build());
		DeviceDraft device = new DeviceDraft(deviceBuilder.accesses(deviceAccesses).tags(deviceTags).build());
		SnapshotDeviceScript script = new SnapshotDeviceScript(TestDrivers.registry(), SecretResolver.LOCAL, null, this.taskContext);

		AccessManager accessManager = new AccessManager(TestDrivers.registry(), SecretResolver.LOCAL, null, device, null, this.taskContext, Set.of(community));
		Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", DeviceDraft.class, AccessManager.class);
		runMethod.setAccessible(true);
		runMethod.invoke(script, device, accessManager);

		Assertions.assertEquals("myhost", device.getName(), "The device name is incorrect");
		Assertions.assertEquals("Rack 1", device.getLocation(), "The location is incorrect");
		Assertions.assertEquals("someone@example.com", device.getContact(), "The contact is incorrect");
		Assertions.assertEquals(NetworkClass.UNKNOWN, device.getNetworkClass(), "The network class is incorrect");
		Assertions.assertEquals("1.3.6.1.4.1.8072.3.2.10",
			((DeviceTextAttribute) device.getAttribute("sysObjectId")).text(), "The sysObjectId is incorrect");
		Assertions.assertNotNull(device.getNetworkInterface("eth0"), "The eth0 interface should have been created");
		Assertions.assertNotEquals(0, device.getNetworkInterface("eth0").physicalAddress().getAddress(),
			"The eth0 MAC address should have been set");
	}
}
