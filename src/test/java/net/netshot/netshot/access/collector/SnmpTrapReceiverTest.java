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
package net.netshot.netshot.access.collector;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.OctetString;

import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.TestNetwork;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("SNMP trap receiver")
class SnmpTrapReceiverTest {

	@Test
	@DisplayName("No SNMPv3 user without netshot.snmptrap.user")
	void noUser() {
		Assertions.assertEquals(List.of(), SnmpTrapReceiver.parseUsers(null));
		Assertions.assertEquals(List.of(), SnmpTrapReceiver.parseUsers(""));
		Assertions.assertEquals(List.of(), SnmpTrapReceiver.parseUsers("  "));
	}

	@Test
	@DisplayName("SNMPv3 users with their protocols and keys")
	void users() {
		List<UsmUser> users = SnmpTrapReceiver.parseUsers(" user1 AES128|SHA authpass1 privpass1 user2 SHA authpass2 ");
		Assertions.assertEquals(2, users.size());
		Assertions.assertEquals("user1", users.get(0).getSecurityName().toString());
		Assertions.assertEquals(AuthSHA.ID, users.get(0).getAuthenticationProtocol());
		Assertions.assertEquals(PrivAES128.ID, users.get(0).getPrivacyProtocol());
		Assertions.assertEquals("user2", users.get(1).getSecurityName().toString());
		Assertions.assertNull(users.get(1).getPrivacyProtocol());
	}

	@AfterEach
	void resetConfig() {
		TestConfig.init(new Properties());
	}

	private static Properties properties(int port, boolean disabled, String communities) {
		Properties config = new Properties();
		config.setProperty("netshot.snmptrap.port", Integer.toString(port));
		config.setProperty("netshot.snmptrap.listenaddress", "127.0.0.1");
		config.setProperty("netshot.snmptrap.disabled", Boolean.toString(disabled));
		config.setProperty("netshot.snmptrap.community", communities);
		return config;
	}

	/**
	 * Whether a UDP port of the loopback address is used (by the receiver).
	 *
	 * @param port the port
	 * @return true if the port can't be bound
	 */
	private static boolean used(int port) {
		try (DatagramSocket _ = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), port))) {
			return false;
		}
		catch (SocketException e) {
			return true;
		}
	}

	@Test
	@DisplayName("Settings: communities split on spaces, configured engine ID, what needs the receiver to be opened again")
	void settings() {
		Properties properties = properties(1162, false, " public  private ");
		properties.setProperty("netshot.snmptrap.engineid", "80:00:1f:88:80");
		Config config = Config.of(properties);
		SnmpTrapReceiver.Settings settings = SnmpTrapReceiver.Settings.from(config);
		Assertions.assertTrue(settings.enabled());
		Assertions.assertEquals(Set.of("public", "private"), settings.communities());
		Assertions.assertEquals(OctetString.fromHexString("80:00:1f:88:80"), settings.engineId());
		properties.setProperty("netshot.snmptrap.engineid", "not hex");
		Assertions.assertNull(SnmpTrapReceiver.Settings.from(Config.of(properties)).engineId());

		Properties changed = properties(1162, false, "other");
		Assertions.assertFalse(SnmpTrapReceiver.Settings.from(Config.of(changed)).needsRestart(settings), "Communities: applied in place");
		changed.setProperty("netshot.snmptrap.threadcount", "4");
		Assertions.assertTrue(SnmpTrapReceiver.Settings.from(Config.of(changed)).needsRestart(settings), "Thread count");
		Assertions.assertTrue(SnmpTrapReceiver.Settings.from(Config.of(properties(1163, false, ""))).needsRestart(settings), "Port");
	}

	@Test
	@DisplayName("Applies a change of the port live, closes its port when disabled (and stays healthy), and releases it when stopped")
	void appliedLive() throws Exception {
		int port = TestNetwork.freePort();
		TestConfig.init(properties(port, false, "public"));
		SnmpTrapReceiver receiver = new SnmpTrapReceiver(null, (_, _) -> { }, TestConfig.store());
		receiver.start();
		try {
			Assertions.assertTrue(receiver.isHealthy());
			Assertions.assertTrue(used(port));
			Assertions.assertEquals(port, receiver.stats().get("port"));

			int newPort = TestNetwork.freePort();
			TestConfig.init(properties(newPort, false, "public"));
			Assertions.assertEquals(newPort, receiver.stats().get("port"));
			Assertions.assertTrue(used(newPort));
			Assertions.assertFalse(used(port));

			TestConfig.init(properties(newPort, true, "public"));
			Assertions.assertTrue(receiver.isHealthy());
			Assertions.assertEquals(false, receiver.stats().get("enabled"));
			Assertions.assertFalse(used(newPort));

			TestConfig.init(properties(newPort, false, "public"));
			Assertions.assertTrue(used(newPort));
		}
		finally {
			receiver.stop();
		}
		Assertions.assertFalse(receiver.isHealthy());
		Assertions.assertFalse(used(port));
	}

	@Test
	@DisplayName("Starts unhealthy, without failing, when its port is used")
	void portUsed() throws Exception {
		int port = TestNetwork.freePort();
		try (DatagramSocket _ = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), port))) {
			TestConfig.init(properties(port, false, "public"));
			SnmpTrapReceiver receiver = new SnmpTrapReceiver(null, (_, _) -> { }, TestConfig.store());
			receiver.start();
			try {
				Assertions.assertFalse(receiver.isHealthy());
				Assertions.assertEquals(false, receiver.stats().get("listening"));
			}
			finally {
				receiver.stop();
			}
		}
	}
}
