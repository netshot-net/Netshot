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
package net.netshot.netshot.aaa.auth;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestConfig;

/**
 * Tests the RADIUS authentication against a real FreeRADIUS server ({@code dev/radius-server}).
 */
@SimulatorTest
@DisplayName("RADIUS authentication")
class RadiusTest {

	@Container
	private static final GenericContainer<?> SERVER = AaaServers.radius();

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	private Logger radiusLogger;

	@BeforeEach
	void captureLogs() {
		this.radiusLogger = (Logger) LoggerFactory.getLogger(Radius.class);
		this.logs.start();
		this.radiusLogger.addAppender(this.logs);
	}

	@AfterEach
	void reset() {
		this.radiusLogger.detachAppender(this.logs);
		TestConfig.init(new Properties());
	}

	/**
	 * Configures the server as the given RADIUS server number.
	 *
	 * @param config the configuration to complete
	 * @param id the number of the server
	 * @param secret the shared secret to use
	 */
	private static void addServer(Properties config, int id, String secret) {
		config.setProperty("netshot.aaa.radius%d.ip".formatted(id), SERVER.getHost());
		config.setProperty("netshot.aaa.radius%d.authport".formatted(id), Integer.toString(DeviceSimulators.mappedUdpPort(SERVER, 1812)));
		config.setProperty("netshot.aaa.radius%d.acctport".formatted(id), Integer.toString(DeviceSimulators.mappedUdpPort(SERVER, 1813)));
		config.setProperty("netshot.aaa.radius%d.secret".formatted(id), secret);
		config.setProperty("netshot.aaa.radius%d.timeout".formatted(id), "1");
	}

	private static Radius radius(String method) {
		Properties config = new Properties();
		addServer(config, 1, AaaServers.RADIUS_SECRET);
		config.setProperty("netshot.aaa.radius.method", method);
		config.setProperty("netshot.aaa.radius.nasidentifier", "netshot-test");
		TestConfig.init(config);
		return new Radius(TestConfig.store());
	}

	// A server which doesn't answer: jradius throws after its retries, logged as an error
	private long serverErrors() {
		synchronized (this.logs) {
			return this.logs.list.stream()
				.filter(e -> e.getLevel() == Level.ERROR)
				.count();
		}
	}

	@TestFactory
	@DisplayName("Authenticates a user with each method, refuses a wrong password and an unknown user")
	Stream<DynamicTest> methods() {
		return Stream.of("pap", "chap", "mschapv2", "eap-md5", "eap-mschapv2").map(method -> DynamicTest.dynamicTest(method, () -> {
			Radius radius = radius(method);
			UiUser user = radius.authenticate("admin", "AdminPass1", "192.0.2.10");
			Assertions.assertNotNull(user, "Not authenticated with " + method);
			Assertions.assertEquals("admin", user.username());
			Assertions.assertFalse(user.local());
			Assertions.assertEquals(UiUser.LEVEL_ADMIN, user.level());
			Assertions.assertNull(radius.authenticate("admin", "WrongPass1", null), "Wrong password accepted with " + method);
			Assertions.assertNull(radius.authenticate("nobody", "AdminPass1", null), "Unknown user accepted with " + method);
		}));
	}

	/**
	 * A test user and the level its Service-Type gives.
	 *
	 * @param username the user name
	 * @param password the password
	 * @param level the expected level
	 */
	private record Account(String username, String password, int level) {
	}

	@TestFactory
	@DisplayName("Maps the Service-Type of the reply to the level")
	Stream<DynamicTest> levels() {
		return List.of(
			new Account("admin", "AdminPass1", UiUser.LEVEL_ADMIN),
			new Account("outbound", "OutboundPass1", UiUser.LEVEL_EXECUTEREADWRITE),
			new Account("nasprompt", "NasPromptPass1", UiUser.LEVEL_READWRITE),
			new Account("authonly", "AuthOnlyPass1", UiUser.LEVEL_OPERATOR),
			new Account("plain", "PlainPass1", UiUser.LEVEL_READONLY)
		).stream().map(account -> DynamicTest.dynamicTest(account.username(), () -> {
			UiUser user = radius("pap").authenticate(account.username(), account.password(), null);
			Assertions.assertNotNull(user);
			Assertions.assertEquals(account.level(), user.level());
		}));
	}

	@Test
	@DisplayName("Refuses everything with a wrong shared secret")
	void wrongSecret() {
		Properties config = new Properties();
		addServer(config, 1, "not-the-secret");
		config.setProperty("netshot.aaa.radius.method", "pap");
		TestConfig.init(config);
		Assertions.assertNull(new Radius(TestConfig.store()).authenticate("admin", "AdminPass1", null));
	}

	@Test
	@DisplayName("Tries the next server when one doesn't answer, then asks the answering one first")
	void failover() throws Exception {
		try (DatagramSocket silent = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			Properties config = new Properties();
			config.setProperty("netshot.aaa.radius1.ip", "127.0.0.1");
			config.setProperty("netshot.aaa.radius1.authport", Integer.toString(silent.getLocalPort()));
			config.setProperty("netshot.aaa.radius1.secret", AaaServers.RADIUS_SECRET);
			config.setProperty("netshot.aaa.radius1.timeout", "1");
			addServer(config, 2, AaaServers.RADIUS_SECRET);
			config.setProperty("netshot.aaa.radius.method", "pap");
			TestConfig.init(config);
			Radius radius = new Radius(TestConfig.store());

			UiUser user = radius.authenticate("admin", "AdminPass1", null);
			Assertions.assertNotNull(user);
			Assertions.assertEquals(1, this.serverErrors(), "The silent server was asked first");

			Assertions.assertNotNull(radius.authenticate("admin", "AdminPass1", null));
			Assertions.assertEquals(1, this.serverErrors(), "The answering server is asked first now");
			Assertions.assertNull(radius.authenticate("admin", "WrongPass1", null));
			Assertions.assertEquals(1, this.serverErrors(), "A refusal ends the authentication");
		}
	}

	@Test
	@DisplayName("Ignores the servers without secret or with an invalid address")
	void invalidServers() {
		Properties config = new Properties();
		config.setProperty("netshot.aaa.radius1.ip", "127.0.0.1");
		config.setProperty("netshot.aaa.radius2.ip", "not an address");
		config.setProperty("netshot.aaa.radius2.secret", AaaServers.RADIUS_SECRET);
		TestConfig.init(config);
		Radius radius = new Radius(TestConfig.store());
		Assertions.assertFalse(radius.isAvailable());
		Assertions.assertNull(radius.authenticate("admin", "AdminPass1", null));
	}

	@Test
	@DisplayName("Applies a configuration change at the next authentication")
	void reconfigured() {
		Radius radius = radius("pap");
		Assertions.assertNotNull(radius.authenticate("admin", "AdminPass1", null));
		TestConfig.init(new Properties());
		Assertions.assertFalse(radius.isAvailable());
		Assertions.assertNull(radius.authenticate("admin", "AdminPass1", null));
	}
}
