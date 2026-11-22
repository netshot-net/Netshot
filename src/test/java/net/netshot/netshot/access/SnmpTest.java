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
package net.netshot.netshot.access;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.secret.LocalSecret;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.vault.SecretResolver;

@SimulatorTest
@DisplayName("SNMP device access")
class SnmpTest {

	/** Name of the device, sysName of the simulator. */
	private static final String SYS_NAME = "1.3.6.1.2.1.1.5.0";

	@Container
	private static final GenericContainer<?> CONTAINER = DeviceSimulators.snmp("snmphost");

	private static DeviceSnmpv3Community v3(String user, String password) {
		return DeviceSnmpv3Community.builder().name(user).username(LocalSecret.of(user))
			.authType(DeviceSnmpv3Community.AuthProtocol.SHA).authKey(LocalSecret.of(password))
			.privType(DeviceSnmpv3Community.PrivProtocol.AES128).privKey(LocalSecret.of(password)).build();
	}

	private static Snmp open(DeviceSnmpv3Community community) throws Exception {
		Network4Address address = new Network4Address((Inet4Address) InetAddress.getByName(CONTAINER.getHost()), 32);
		return new Snmp(address, DeviceSimulators.mappedUdpPort(CONTAINER, 161), community, Snmp.Settings.from(TestConfig.store().current()), SecretResolver.LOCAL);
	}

	@Test
	@DisplayName("Two SNMPv3 accesses with different users don't share their users (each has its own USM)")
	void separateUsers() throws Exception {
		Snmp device = open(v3("netshotv3", "admin1234"));
		Snmp other = open(v3("otheruser", "otherpass1"));
		try {
			Assertions.assertEquals("snmphost", device.getAsString(SYS_NAME));
			Assertions.assertThrows(Exception.class, () -> other.getAsString(SYS_NAME), "Unknown user on the device");
			Assertions.assertEquals("snmphost", device.getAsString(SYS_NAME));
		}
		finally {
			device.stop();
			other.stop();
		}
	}

	@Test
	@DisplayName("A walk with an unknown SNMPv3 user fails with the report of the device, instead of returning nothing")
	void walkUnknownUser() throws Exception {
		Snmp device = open(v3("netshotv3", "admin1234"));
		Snmp other = open(v3("otheruser", "otherpass1"));
		try {
			Assertions.assertTrue(device.walkAsString("1.3.6.1.2.1.1").containsValue("snmphost"));
			IOException error = Assertions.assertThrows(IOException.class, () -> other.walkAsString("1.3.6.1.2.1.1"));
			Assertions.assertTrue(error.getMessage().startsWith("SNMP walk of 1.3.6.1.2.1.1 failed: Report: 1.3.6.1.6.3.15.1.1.3.0"), error.getMessage());
		}
		finally {
			device.stop();
			other.stop();
		}
	}
}
