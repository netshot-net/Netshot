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

import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestConfig;

/**
 * Tests the TACACS+ authentication, authorization and accounting against a real tac_plus-ng server
 * ({@code dev/tacacs-server}).
 */
@SimulatorTest
@DisplayName("TACACS+ authentication")
class TacacsTest {

	@Container
	private static final GenericContainer<?> SERVER = AaaServers.tacacs();

	@AfterEach
	void reset() {
		TestConfig.init(new Properties());
	}

	private static Properties config(String key) {
		Properties config = new Properties();
		config.setProperty("netshot.aaa.tacacs1.ip", SERVER.getHost());
		config.setProperty("netshot.aaa.tacacs1.port", Integer.toString(SERVER.getMappedPort(49)));
		config.setProperty("netshot.aaa.tacacs1.secret", key);
		config.setProperty("netshot.aaa.tacacs.timeout", "2");
		return config;
	}

	private static Tacacs tacacs(Properties config) {
		TestConfig.init(config);
		return new Tacacs(TestConfig.store());
	}

	/**
	 * A test user and the level the role returned by the server gives.
	 *
	 * @param username the user name
	 * @param password the password
	 * @param level the expected level
	 */
	private record Account(String username, String password, int level) {
	}

	@TestFactory
	@DisplayName("Maps the role returned by the authorization to the level")
	Stream<DynamicTest> roles() {
		Tacacs tacacs = tacacs(config(AaaServers.TACACS_KEY));
		return List.of(
			new Account("admin", "AdminPass1", UiUser.LEVEL_ADMIN),
			new Account("operator", "OperatorPass1", UiUser.LEVEL_OPERATOR),
			new Account("norole", "NoRolePass1", UiUser.LEVEL_READONLY),
			new Account("custom", "CustomPass1", UiUser.LEVEL_READONLY)
		).stream().map(account -> DynamicTest.dynamicTest(account.username(), () -> {
			UiUser user = tacacs.authenticate(account.username(), account.password(), "192.0.2.10");
			Assertions.assertNotNull(user);
			Assertions.assertEquals(account.username(), user.username());
			Assertions.assertFalse(user.local());
			Assertions.assertEquals(account.level(), user.level());
		}));
	}

	@Test
	@DisplayName("Maps a configured role name, read from the configured attribute")
	void configuredRoles() {
		Properties config = config(AaaServers.TACACS_KEY);
		config.setProperty("netshot.aaa.tacacs.role.adminrole", "netshot-superuser");
		Tacacs tacacs = tacacs(config);
		Assertions.assertEquals(UiUser.LEVEL_ADMIN, tacacs.authenticate("custom", "CustomPass1", "192.0.2.10").level());
		// "admin" isn't the admin role anymore
		Assertions.assertEquals(UiUser.LEVEL_READONLY, tacacs.authenticate("admin", "AdminPass1", "192.0.2.10").level());

		config.setProperty("netshot.aaa.tacacs.role.attributename", "group");
		tacacs = tacacs(config);
		Assertions.assertEquals(UiUser.LEVEL_READONLY, tacacs.authenticate("custom", "CustomPass1", "192.0.2.10").level());
	}

	@Test
	@DisplayName("Refuses a wrong password, an unknown user, and a user the authorization denies")
	void refused() {
		Tacacs tacacs = tacacs(config(AaaServers.TACACS_KEY));
		Assertions.assertNull(tacacs.authenticate("admin", "WrongPass1", "192.0.2.10"));
		Assertions.assertNull(tacacs.authenticate("nobody", "AdminPass1", "192.0.2.10"));
		Assertions.assertNull(tacacs.authenticate("denied", "DeniedPass1", "192.0.2.10"));
		// Still working after the refusals
		Assertions.assertNotNull(tacacs.authenticate("admin", "AdminPass1", "192.0.2.10"));
	}

	@Test
	@DisplayName("Refuses everything with a wrong key")
	void wrongKey() {
		Assertions.assertNull(tacacs(config("not-the-key")).authenticate("admin", "AdminPass1", "192.0.2.10"));
	}

	@Test
	@DisplayName("Uses the next server when the first one is down")
	void failover() throws Exception {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			closedPort = socket.getLocalPort();
		}
		Properties config = config(AaaServers.TACACS_KEY);
		config.setProperty("netshot.aaa.tacacs2.ip", config.getProperty("netshot.aaa.tacacs1.ip"));
		config.setProperty("netshot.aaa.tacacs2.port", config.getProperty("netshot.aaa.tacacs1.port"));
		config.setProperty("netshot.aaa.tacacs2.secret", AaaServers.TACACS_KEY);
		config.setProperty("netshot.aaa.tacacs1.ip", "127.0.0.1");
		config.setProperty("netshot.aaa.tacacs1.port", Integer.toString(closedPort));
		UiUser user = tacacs(config).authenticate("admin", "AdminPass1", "192.0.2.10");
		Assertions.assertNotNull(user);
		Assertions.assertEquals(UiUser.LEVEL_ADMIN, user.level());
	}

	@Test
	@DisplayName("Ignores the servers without key or with an invalid address")
	void invalidServers() {
		Properties config = new Properties();
		config.setProperty("netshot.aaa.tacacs1.ip", "127.0.0.1");
		config.setProperty("netshot.aaa.tacacs2.ip", "not an address");
		config.setProperty("netshot.aaa.tacacs2.secret", AaaServers.TACACS_KEY);
		Tacacs tacacs = tacacs(config);
		Assertions.assertFalse(tacacs.isAvailable());
		Assertions.assertNull(tacacs.authenticate("admin", "AdminPass1", "192.0.2.10"));
	}

	@Test
	@DisplayName("Sends the accounting messages when enabled")
	void accounting() throws Exception {
		Tacacs tacacs = tacacs(config(AaaServers.TACACS_KEY));
		tacacs.account("PUT", "/api/v1/devices/1", "admin", "200", "192.0.2.10");

		Properties config = config(AaaServers.TACACS_KEY);
		config.setProperty("netshot.aaa.tacacs.accounting", "true");
		tacacs = tacacs(config);
		tacacs.account("PATCH", "/api/v1/devices/2", "admin", "200", "192.0.2.10");

		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		String log = "";
		while (Instant.now().isBefore(deadline) && !log.contains("/api/v1/devices/2")) {
			ExecResult result = SERVER.execInContainer("cat", "/tmp/accounting.log");
			log = result.getStdout();
			Thread.sleep(200);
		}
		Assertions.assertTrue(log.contains("PATCH /api/v1/devices/2 => 200"), log);
		Assertions.assertFalse(log.contains("/api/v1/devices/1"), "Accounting while disabled: " + log);
	}
}
