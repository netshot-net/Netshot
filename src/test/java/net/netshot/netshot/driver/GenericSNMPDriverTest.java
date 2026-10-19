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
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.devicescript.SnapshotDeviceScript;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.task.engine.TaskContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GenericSNMP driver test")
@Testcontainers
public class GenericSNMPDriverTest extends WithDeviceSimulatorTest {

	TaskContext taskContext = new FakeTaskContext();

	@Container
	private static final GenericContainer<?> CONTAINER = buildSnmpDeviceSimulator("myhost");

	@Test
	@DisplayName("GenericSNMP Snapshot (real snmpd CONTAINER)")
	void snapshot() throws NoSuchMethodException, SecurityException, IOException,
		IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		DeviceSnmpv2cCommunity community = new DeviceSnmpv2cCommunity("public", "community1");
		int port = getMappedUdpPort(CONTAINER, 161);
		Session nullSession = null;
		Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
		Device device = new Device("GenericSNMP", null, domain, "test");
		DeviceAccess snmpAccess = new DeviceAccess(device, "snmpv2c");
		snmpAccess.setAddress(CONTAINER.getHost());
		snmpAccess.setPort(port);
		device.getAccesses().add(snmpAccess);
		SnapshotDeviceScript script = new SnapshotDeviceScript(TestDrivers.registry(), this.taskContext);

		AccessManager accessManager = new AccessManager(TestDrivers.registry(), nullSession, device, null, this.taskContext, Set.of(community));
		Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", Session.class,
			Device.class, AccessManager.class);
		runMethod.setAccessible(true);
		runMethod.invoke(script, nullSession, device, accessManager);

		Assertions.assertEquals("myhost", device.getName(), "The device name is incorrect");
		Assertions.assertEquals("Rack 1", device.getLocation(), "The location is incorrect");
		Assertions.assertEquals("someone@example.com", device.getContact(), "The contact is incorrect");
		Assertions.assertEquals(NetworkClass.UNKNOWN, device.getNetworkClass(), "The network class is incorrect");
		Assertions.assertEquals("1.3.6.1.4.1.8072.3.2.10",
			((DeviceTextAttribute) device.getAttribute("sysObjectId")).getText(), "The sysObjectId is incorrect");
		Assertions.assertNotNull(device.getNetworkInterface("eth0"), "The eth0 interface should have been created");
		Assertions.assertNotEquals(0, device.getNetworkInterface("eth0").getPhysicalAddress().getAddress(),
			"The eth0 MAC address should have been set");
	}
}
