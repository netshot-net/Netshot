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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.vault.SecretResolver;

@UnitTest
@DisplayName("SNMP client settings")
class SnmpSettingsTest {

	@Test
	@DisplayName("Timeout and retries: defaults, configured values, range")
	void settings() {
		Assertions.assertEquals(new Snmp.Settings(1500, 2), Snmp.Settings.from(Config.of(new Properties(), Map.of())));
		Properties properties = new Properties();
		properties.setProperty("netshot.snmp.timeout", "3000");
		properties.setProperty("netshot.snmp.retries", "0");
		Assertions.assertEquals(new Snmp.Settings(3000, 0), Snmp.Settings.from(Config.of(properties, Map.of())));
		properties.setProperty("netshot.snmp.timeout", "0");
		properties.setProperty("netshot.snmp.retries", "-1");
		Assertions.assertEquals(new Snmp.Settings(1500, 2), Snmp.Settings.from(Config.of(properties, Map.of())), "Out of range");
	}

	@Test
	@DisplayName("A request without response is sent again after the timeout, as many times as the retries")
	void retries() throws Exception {
		DeviceSnmpv2cCommunity community = DeviceSnmpv2cCommunity.builder().name("test").community(Secret.local("public")).build();
		try (DatagramSocket device = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			device.setSoTimeout(2000);
			Snmp snmp = new Snmp(new Network4Address("127.0.0.1"), device.getLocalPort(), community, new Snmp.Settings(200, 2),
				SecretResolver.LOCAL);
			long start = System.nanoTime();
			try {
				Assertions.assertThrows(IOException.class, () -> snmp.getAsString("1.3.6.1.2.1.1.5.0"), "No response");
			}
			finally {
				snmp.stop();
			}
			long elapsedMs = (System.nanoTime() - start) / 1_000_000;
			Assertions.assertTrue(elapsedMs >= 500, "3 timeouts of 200 ms (with the timer granularity of SNMP4J), took " + elapsedMs + " ms");
			int received = 0;
			try {
				while (true) {
					device.receive(new DatagramPacket(new byte[1500], 1500));
					received++;
					device.setSoTimeout(300);
				}
			}
			catch (SocketTimeoutException _) {
				// No more request
			}
			Assertions.assertEquals(3, received, "The request and 2 retries");
		}
	}
}
