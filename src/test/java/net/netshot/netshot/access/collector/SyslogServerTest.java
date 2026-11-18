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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestNetwork;

@ScriptEngineTest
@DisplayName("Syslog server service")
class SyslogServerTest {

	@AfterEach
	void resetConfig() {
		TestConfig.init(new Properties());
	}

	private static SyslogServer server(int port) {
		Properties config = new Properties();
		config.setProperty("netshot.syslog.port", Integer.toString(port));
		TestConfig.init(config);
		return new SyslogServer(TestDrivers.registry(), (_, _) -> { }, TestConfig.store());
	}

	private static void configure(int port, boolean disabled) {
		Properties config = new Properties();
		config.setProperty("netshot.syslog.port", Integer.toString(port));
		config.setProperty("netshot.syslog.listenaddress", "127.0.0.1");
		config.setProperty("netshot.syslog.enabled", Boolean.toString(!disabled));
		TestConfig.init(config);
	}

	private static void await(BooleanSupplier condition, String message) throws InterruptedException {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
		while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
			Thread.sleep(50);
		}
		Assertions.assertTrue(condition.getAsBoolean(), message);
	}

	private static void send(int port, String message) throws Exception {
		try (DatagramSocket socket = new DatagramSocket()) {
			byte[] data = message.getBytes(StandardCharsets.UTF_8);
			socket.send(new DatagramPacket(data, data.length, InetAddress.getLoopbackAddress(), port));
		}
	}

	@Test
	@DisplayName("Receives the messages, and releases the port when stopped")
	void receiveThenStop() throws Exception {
		int port = TestNetwork.freePort();
		SyslogServer server = server(port);
		server.start();
		await(server::isHealthy, "The server isn't listening");
		send(port, "<189>Oct 25 10:00:00 router1 Some message which no driver matches");
		await(() -> (Long) server.stats().get("received") == 1L, "The message wasn't received");
		Assertions.assertEquals(0L, server.stats().get("matched"));
		server.stop();
		Assertions.assertFalse(server.isHealthy());
		// The port is free again
		try (DatagramSocket socket = new DatagramSocket(port)) {
			Assertions.assertTrue(socket.isBound());
		}
	}

	@Test
	@DisplayName("Keeps trying to open the port while it's used, and is unhealthy meanwhile")
	void retriesWhilePortUsed() throws Exception {
		int port = TestNetwork.freePort();
		SyslogServer server = server(port);
		try (DatagramSocket _ = new DatagramSocket(port)) {
			server.start();
			await(() -> (Long) server.stats().get("errors") >= 1L, "The bind error wasn't reported");
			Assertions.assertFalse(server.isHealthy());
		}
		await(server::isHealthy, "The server didn't open the port once released");
		server.stop();
	}

	@Test
	@DisplayName("Applies a change of the port live")
	void portChangedLive() throws Exception {
		int firstPort = TestNetwork.freePort();
		SyslogServer server = server(firstPort);
		server.start();
		try {
			await(server::isHealthy, "The server isn't listening");
			int secondPort = TestNetwork.freePort();
			configure(secondPort, false);
			await(() -> server.isHealthy() && (Integer) server.stats().get("port") == secondPort, "The server didn't move to the new port");
			send(secondPort, "<189>Oct 25 10:00:00 router1 Some message which no driver matches");
			await(() -> (Long) server.stats().get("received") == 1L, "The message wasn't received on the new port");
			// The former port is free
			try (DatagramSocket socket = new DatagramSocket(firstPort)) {
				Assertions.assertTrue(socket.isBound());
			}
			Assertions.assertEquals(0L, server.stats().get("errors"), "A configuration change isn't a socket error");
		}
		finally {
			server.stop();
		}
	}

	@Test
	@DisplayName("Disabled live: closes its port and stays healthy, then listens again once enabled")
	void disabledLive() throws Exception {
		int port = TestNetwork.freePort();
		SyslogServer server = server(port);
		server.start();
		try {
			await(server::isHealthy, "The server isn't listening");
			configure(port, true);
			await(() -> !(Boolean) server.stats().get("listening"), "The server didn't close its port");
			Assertions.assertTrue(server.isHealthy(), "A disabled server is healthy");
			Assertions.assertEquals(false, server.stats().get("enabled"));
			try (DatagramSocket socket = new DatagramSocket(port)) {
				Assertions.assertTrue(socket.isBound());
			}
			configure(port, false);
			await(() -> (Boolean) server.stats().get("listening"), "The server didn't listen again");
			Assertions.assertEquals(true, server.stats().get("enabled"));
		}
		finally {
			server.stop();
		}
	}
}
