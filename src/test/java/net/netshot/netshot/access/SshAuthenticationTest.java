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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.access.AccessManager.Resolution;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.DeviceDriver.AccessDefinition;
import net.netshot.netshot.driver.DeviceDriver.DriverProtocol;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.fake.FakeTaskContext;

/**
 * The outcome of the SSH authentication: a rejected password is invalid credentials (the next credential set is tried), a
 * server which doesn't answer in time is not (the other credential sets of the access are not tried).
 */
@UnitTest
@DisplayName("SSH authentication outcome")
public class SshAuthenticationTest {

	/** How long the server takes to check a password of the "slow" user (ms), longer than the timeout of the client. */
	private static final int SLOW_CHECK = 5000;

	/** The connection timeout of the client (ms). */
	private static final int TIMEOUT = 2000;

	private static SshServer server;

	@BeforeAll
	static void startServer() throws IOException {
		server = SshServer.setUpDefaultServer();
		server.setHost("127.0.0.1");
		server.setPort(0);
		SimpleGeneratorHostKeyProvider hostKey = new SimpleGeneratorHostKeyProvider();
		// Generated now rather than during the first key exchange, which the authentication time of the client includes
		hostKey.loadKeys(null);
		server.setKeyPairProvider(hostKey);
		server.setPasswordAuthenticator((username, password, _) -> {
			if ("slow".equals(username)) {
				try {
					Thread.sleep(SLOW_CHECK);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			return "good".equals(password);
		});
		server.start();
	}

	@AfterAll
	static void stopServer() throws IOException {
		server.stop(true);
	}

	private static Ssh ssh(String username, String password) {
		Ssh ssh = new Ssh(TestConnectors.sshClient(), "127.0.0.1", server.getPort(), username, password, new FakeTaskContext());
		ssh.setConnectionTimeout(TIMEOUT);
		return ssh;
	}

	@Test
	@DisplayName("A rejected password is invalid credentials")
	void rejectedPassword() {
		Ssh ssh = ssh("admin", "bad");
		try {
			Assertions.assertThrows(InvalidCredentialsException.class, () -> ssh.connect(false));
		}
		finally {
			ssh.disconnect();
		}
	}

	@Test
	@DisplayName("An authentication which times out is a connection failure, not invalid credentials")
	void authenticationTimeout() {
		Ssh ssh = ssh("slow", "good");
		try {
			IOException e = Assertions.assertThrows(IOException.class, () -> ssh.connect(false));
			Assertions.assertFalse(e instanceof InvalidCredentialsException, "Reported as invalid credentials: " + e);
		}
		finally {
			ssh.disconnect();
		}
	}

	/**
	 * A credential set, and the user and password the SSH client uses for it.
	 *
	 * @param credentialSet the credential set
	 * @param username the user
	 * @param password the password
	 */
	private record Login(DeviceCredentialSet credentialSet, String username, String password) {
		Login(String username, String password) {
			this(DeviceSshAccount.builder().username(Secret.local(username)).password(Secret.local(password)).superPassword(null)
				.name(username + "/" + password).build(), username, password);
		}
	}

	/**
	 * Gets the access manager of a device whose SSH access has credential sets, tried in order through real SSH clients.
	 *
	 * @param logins the credential sets, in order
	 * @return the access manager
	 */
	private static AccessManager accessManager(List<Login> logins) {
		Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
		DeviceDraft device = new DeviceDraft(Device.create("CiscoIOS12", null, domain, "test"));
		return new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, new FakeTaskContext(),
			new LinkedHashSet<>(logins.stream().map(Login::credentialSet).toList()));
	}

	private static Resolution resolution(AccessManager manager, List<Login> logins, List<Login> attempts) {
		return manager.newResolution(List.of(new AccessDefinition("ssh", DriverProtocol.SSH, null, null, null, null, 22)),
			(_, credentialSet) -> {
				Login login = logins.stream().filter(l -> l.credentialSet() == credentialSet).findFirst().orElseThrow();
				attempts.add(login);
				return ssh(login.username(), login.password());
			});
	}

	@Test
	@DisplayName("After a rejected password, the next credential set of the access is tried")
	void rejectedPasswordTriesNextCredentials() throws IOException {
		Login wrong = new Login("admin", "bad");
		Login right = new Login("admin", "good");
		List<Login> attempts = new ArrayList<>();
		AccessManager manager = accessManager(List.of(wrong, right));
		try {
			Resolution resolution = resolution(manager, List.of(wrong, right), attempts);
			Assertions.assertNotNull(resolution.ensureResolved(true));
			Assertions.assertEquals(right.credentialSet(), resolution.getCurrentCredentialSet());
			Assertions.assertEquals(List.of(wrong, right), attempts);
		}
		finally {
			manager.disconnectAll();
		}
	}

	@Test
	@DisplayName("After an authentication timeout, the other credential sets of the access are not tried")
	void timeoutSkipsOtherCredentials() {
		Login slow = new Login("slow", "good");
		Login right = new Login("admin", "good");
		List<Login> attempts = new ArrayList<>();
		AccessManager manager = accessManager(List.of(slow, right));
		try {
			Resolution resolution = resolution(manager, List.of(slow, right), attempts);
			IOException e = Assertions.assertThrows(IOException.class, () -> resolution.ensureResolved(true));
			Assertions.assertFalse(e instanceof InvalidCredentialsException, "Reported as invalid credentials: " + e);
			Assertions.assertEquals(List.of(slow), attempts, "The other credential set of the access was tried");
		}
		finally {
			manager.disconnectAll();
		}
	}
}
