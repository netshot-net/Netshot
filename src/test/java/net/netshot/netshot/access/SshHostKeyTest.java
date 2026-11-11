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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import net.netshot.netshot.access.AccessManager.Resolution;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.DeviceDriver.AccessDefinition;
import net.netshot.netshot.driver.DeviceDriver.DriverProtocol;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceAccess.SshHostKeyVerification;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.fake.FakeTaskContext;

/**
 * The verification of the SSH host keys on real connections, against an SSH server on the loopback interface
 * whose host key each test chooses: what is learned and saved on the access of the device, what is refused,
 * and the new sessions opened for the file downloads (the decisions themselves are tested by
 * {@link DeviceHostKeyVerifierTest}).
 */
@UnitTest
@DisplayName("SSH host key verification of the connections")
class SshHostKeyTest {

	private static final KeyPair HOST_KEY = generate();
	private static final KeyPair OTHER_HOST_KEY = generate();

	private static final AccessDefinition SSH = new AccessDefinition("ssh", DriverProtocol.SSH, null, null, null, null, 22);

	private final List<SshServer> servers = new ArrayList<>();

	private static KeyPair generate() {
		try {
			return KeyUtils.generateKeyPair(KeyPairProvider.ECDSA_SHA2_NISTP256, 256);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Starts an SSH server presenting a host key, which accepts the password "good".
	 *
	 * @param hostKey the host key of the server
	 * @return the port of the server
	 */
	private int startServer(KeyPair hostKey) throws IOException {
		SshServer server = SshServer.setUpDefaultServer();
		server.setHost("127.0.0.1");
		server.setPort(0);
		server.setKeyPairProvider(KeyPairProvider.wrap(hostKey));
		server.setPasswordAuthenticator((_, password, _) -> "good".equals(password));
		// The access manager opens a shell, and its disconnection waits for the device to close it
		server.setShellFactory(_ -> new ExitingShell());
		server.start();
		this.servers.add(server);
		return server.getPort();
	}

	/** A shell which exits at once, as a device does after the commands of a driver. */
	private static final class ExitingShell implements Command {

		private ExitCallback exitCallback;

		@Override
		public void setInputStream(InputStream in) {
			// Nothing is read
		}

		@Override
		public void setOutputStream(OutputStream out) {
			// Nothing is written
		}

		@Override
		public void setErrorStream(OutputStream err) {
			// Nothing is written
		}

		@Override
		public void setExitCallback(ExitCallback callback) {
			this.exitCallback = callback;
		}

		@Override
		public void start(ChannelSession channel, Environment env) {
			this.exitCallback.onExit(0);
		}

		@Override
		public void destroy(ChannelSession channel) {
			// Nothing to stop
		}
	}

	@AfterEach
	void stopServers() throws IOException {
		for (SshServer server : this.servers) {
			server.stop(true);
		}
	}

	private static String line(KeyPair key) {
		return PublicKeyEntry.toString(key.getPublic());
	}

	private static Ssh ssh(int port, String password, DeviceHostKeyVerifier verifier) {
		Ssh ssh = new Ssh(TestConnectors.sshClient(), "127.0.0.1", port, "admin", password, new FakeTaskContext());
		ssh.setConnectionTimeout(5000);
		ssh.setHostKeyVerifier(verifier);
		return ssh;
	}

	private static DeviceHostKeyVerifier trustKnown(String keys) {
		return new DeviceHostKeyVerifier(SshHostKeyVerification.TRUST_KNOWN, keys, null);
	}

	@Test
	@DisplayName("The first connection learns the host key")
	void learnsHostKey() throws IOException {
		Ssh ssh = ssh(this.startServer(HOST_KEY), "good", trustKnown(null));
		try {
			ssh.connect(false);
			Assertions.assertEquals(line(HOST_KEY), ssh.getLearnedSshHostKeys());
		}
		finally {
			ssh.disconnect();
		}
	}

	@Test
	@DisplayName("A connection to a server presenting the trusted key succeeds, without learning anything")
	void trustedHostKey() throws IOException {
		Ssh ssh = ssh(this.startServer(HOST_KEY), "good", trustKnown(line(HOST_KEY)));
		try {
			ssh.connect(false);
			Assertions.assertNull(ssh.getLearnedSshHostKeys());
		}
		finally {
			ssh.disconnect();
		}
	}

	@Test
	@DisplayName("A connection to a server presenting another key is refused, with the reason")
	void changedHostKey() throws IOException {
		Ssh ssh = ssh(this.startServer(OTHER_HOST_KEY), "good", trustKnown(line(HOST_KEY)));
		try {
			IOException e = Assertions.assertThrows(IOException.class, () -> ssh.connect(false));
			Assertions.assertTrue(e.getMessage().startsWith("SSH host key verification failed: "), e.getMessage());
			Assertions.assertTrue(e.getMessage().contains("does not match"), e.getMessage());
		}
		finally {
			ssh.disconnect();
		}
	}

	@Test
	@DisplayName("Trust any: a connection to a server presenting another key succeeds")
	void trustAny() throws IOException {
		Ssh ssh = ssh(this.startServer(OTHER_HOST_KEY), "good",
			new DeviceHostKeyVerifier(SshHostKeyVerification.TRUST_ANY, line(HOST_KEY), null));
		try {
			ssh.connect(false);
		}
		finally {
			ssh.disconnect();
		}
	}

	@Test
	@DisplayName("A download in a new session verifies the host key as the first session does")
	// Without the verification, the download would wait forever for the server (which doesn't serve files)
	@Timeout(30)
	void newSessionVerifiesHostKey() throws IOException {
		Ssh ssh = ssh(this.startServer(OTHER_HOST_KEY), "good", trustKnown(line(HOST_KEY)));
		IOException scp = Assertions.assertThrows(IOException.class,
			() -> ssh.scpDownload("/etc/config", new ByteArrayOutputStream(), true));
		Assertions.assertTrue(scp.getMessage().startsWith("SSH host key verification failed: "), scp.getMessage());
		IOException sftp = Assertions.assertThrows(IOException.class,
			() -> ssh.sftpDownload("/etc/config", new ByteArrayOutputStream(), true));
		Assertions.assertTrue(sftp.getMessage().startsWith("SSH host key verification failed: "), sftp.getMessage());
	}

	@Test
	@DisplayName("A new session for a download keeps the timeouts of the first one")
	void newSessionKeepsTimeouts() {
		Ssh ssh = ssh(22, "good", trustKnown(null));
		ssh.setConnectionTimeout(1234);
		ssh.setReceiveTimeout(2345);
		ssh.setCommandTimeout(3456);
		Ssh copy = new Ssh(ssh);
		Assertions.assertEquals(1234, copy.getConnectionTimeout());
		Assertions.assertEquals(2345, copy.getReceiveTimeout());
		Assertions.assertEquals(3456, copy.getCommandTimeout());
	}

	// ------------------------------------------------------------------
	// Through the access manager, which saves the learned keys on the device
	// ------------------------------------------------------------------

	/**
	 * Gets the password of a credential set: the right one if its name starts with "good".
	 *
	 * @param name the name of the credential set
	 * @return the password
	 */
	private static String password(String name) {
		return name.startsWith("good") ? "good" : "bad";
	}

	private static DeviceCredentialSet account(String name) {
		return DeviceSshAccount.builder().username(Secret.local("admin")).password(Secret.local(password(name)))
			.superPassword(null).name(name).build();
	}

	private static AccessManager accessManager(DeviceDraft device, List<DeviceCredentialSet> credentialSets, FakeTaskContext taskContext) {
		return new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, taskContext,
			new LinkedHashSet<>(credentialSets));
	}

	/**
	 * Resolves the SSH access of a device, as the device scripts do (the verifier comes from the access manager).
	 *
	 * @param manager the access manager
	 * @param port the port of the SSH server
	 * @param attempts the credential sets tried, in order
	 * @return the resolution
	 */
	private static Resolution resolution(AccessManager manager, int port, List<DeviceCredentialSet> attempts) {
		return manager.newResolution(List.of(SSH), (accessDef, credentialSet) -> {
			attempts.add(credentialSet);
			return ssh(port, password(credentialSet.name()), manager.resolveSshHostKeyVerifier(accessDef));
		});
	}

	/**
	 * Builds a device with given accesses.
	 *
	 * @param accesses the accesses (none for a device whose SSH access has no settings)
	 * @return the device
	 */
	private static DeviceDraft device(DeviceAccess... accesses) {
		Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
		return new DeviceDraft(Device.create("CiscoIOS12", null, domain, "test").toBuilder().accesses(List.of(accesses)).build());
	}

	/**
	 * Builds a device whose SSH access verifies the host keys (trust known), with given trusted keys.
	 *
	 * @param trustedKeys the trusted keys, or null for none yet
	 * @return the device
	 */
	private static DeviceDraft trustKnownDevice(String trustedKeys) {
		return device(DeviceAccess.of("ssh").toBuilder().sshHostKeyVerification(SshHostKeyVerification.TRUST_KNOWN)
			.sshTrustedHostKeys(trustedKeys).build());
	}

	/**
	 * Resolves the SSH access of a device, through a new access manager.
	 *
	 * @param device the device
	 * @param port the port of the SSH server
	 * @param taskContext the task context
	 * @return null if the access was resolved, the failure otherwise
	 */
	private static Exception connect(DeviceDraft device, int port, FakeTaskContext taskContext) {
		AccessManager manager = accessManager(device, List.of(account("good")), taskContext);
		try {
			resolution(manager, port, new ArrayList<>()).ensureResolved(true);
			return null;
		}
		catch (Exception e) {
			return e;
		}
		finally {
			manager.disconnectAll();
		}
	}

	@Test
	@DisplayName("Trust any (the default mode of an access): any host key is accepted, none is saved")
	void trustAnyAccess() throws IOException {
		DeviceDraft device = device(DeviceAccess.of("ssh"));
		Assertions.assertEquals(SshHostKeyVerification.TRUST_ANY, device.getDeviceAccess("ssh").sshHostKeyVerification());
		Assertions.assertNull(connect(device, this.startServer(HOST_KEY), new FakeTaskContext()));
		Assertions.assertNull(connect(device, this.startServer(OTHER_HOST_KEY), new FakeTaskContext()));
		Assertions.assertNull(device.getDeviceAccess("ssh").sshTrustedHostKeys());
	}

	@Test
	@DisplayName("An access without settings (one-time credentials) accepts any host key, and saves none")
	void accessWithoutSettings() throws IOException {
		DeviceDraft device = device();
		Assertions.assertNull(connect(device, this.startServer(HOST_KEY), new FakeTaskContext()));
		Assertions.assertNull(connect(device, this.startServer(OTHER_HOST_KEY), new FakeTaskContext()));
		DeviceAccess access = device.getDeviceAccess("ssh");
		Assertions.assertTrue(access == null || access.sshTrustedHostKeys() == null, String.valueOf(access));
	}

	@Test
	@DisplayName("Trust known: a key set by hand is verified from the first connection, nothing is learned")
	void keySetByHand() throws IOException {
		DeviceDraft device = trustKnownDevice(line(HOST_KEY));
		Assertions.assertNull(connect(device, this.startServer(HOST_KEY), new FakeTaskContext()));
		FakeTaskContext taskContext = new FakeTaskContext();
		Assertions.assertNotNull(connect(device, this.startServer(OTHER_HOST_KEY), taskContext));
		Assertions.assertTrue(taskContext.getLog().contains("does not match the key trusted so far"), taskContext.getLog());
		Assertions.assertEquals(line(HOST_KEY), device.getDeviceAccess("ssh").sshTrustedHostKeys());
		Assertions.assertNull(device.getDeviceAccess("ssh").sshTrustedHostKeysSince(), "A key was learned");
	}

	@Test
	@DisplayName("Trust known: once the known key is cleared, the next connection learns the key again")
	void clearedKeyIsLearnedAgain() throws IOException {
		DeviceDraft device = trustKnownDevice(line(HOST_KEY));
		int otherPort = this.startServer(OTHER_HOST_KEY);
		Assertions.assertNotNull(connect(device, otherPort, new FakeTaskContext()), "Changed key accepted");
		DeviceDraft cleared = trustKnownDevice(null);
		Assertions.assertNull(connect(cleared, otherPort, new FakeTaskContext()));
		Assertions.assertEquals(line(OTHER_HOST_KEY), cleared.getDeviceAccess("ssh").sshTrustedHostKeys());
	}

	@Test
	@DisplayName("Trust known: the host key learned by a successful connection is saved on the access of the device, then verified")
	void savesLearnedHostKey() throws IOException {
		int port = this.startServer(HOST_KEY);
		DeviceDraft device = trustKnownDevice(null);
		AccessManager manager = accessManager(device, List.of(account("good")), new FakeTaskContext());
		try {
			Assertions.assertNotNull(resolution(manager, port, new ArrayList<>()).ensureResolved(true));
		}
		finally {
			manager.disconnectAll();
		}
		DeviceAccess access = device.getDeviceAccess("ssh");
		Assertions.assertEquals(line(HOST_KEY), access.sshTrustedHostKeys());
		Assertions.assertNotNull(access.sshTrustedHostKeysSince());

		// A new connection to the same server: accepted, nothing changes
		Assertions.assertNull(connect(device, port, new FakeTaskContext()));
		Assertions.assertEquals(access, device.getDeviceAccess("ssh"));

		// Another server answering on the address of the device: refused, the other credential sets aren't tried,
		// the reason is in the task log
		int otherPort = this.startServer(OTHER_HOST_KEY);
		List<DeviceCredentialSet> attempts = new ArrayList<>();
		FakeTaskContext taskContext = new FakeTaskContext();
		AccessManager next = accessManager(device, List.of(account("good-1"), account("good-2")), taskContext);
		try {
			Assertions.assertThrows(IOException.class, () -> resolution(next, otherPort, attempts).ensureResolved(true));
			Assertions.assertTrue(taskContext.getLog().contains("does not match the key trusted so far"), taskContext.getLog());
			Assertions.assertEquals(1, attempts.size(), "Other credential sets tried after a host key failure");
			Assertions.assertEquals(line(HOST_KEY), device.getDeviceAccess("ssh").sshTrustedHostKeys());
		}
		finally {
			next.disconnectAll();
		}
	}

	@Test
	@DisplayName("A host key seen by a connection which failed to log in is not saved")
	void failedLoginSavesNothing() throws IOException {
		int port = this.startServer(HOST_KEY);
		DeviceDraft device = trustKnownDevice(null);
		AccessManager manager = accessManager(device, List.of(account("bad")), new FakeTaskContext());
		try {
			Assertions.assertThrows(Exception.class, () -> resolution(manager, port, new ArrayList<>()).ensureResolved(true));
		}
		finally {
			manager.disconnectAll();
		}
		Assertions.assertNull(device.getDeviceAccess("ssh").sshTrustedHostKeys());
	}
}
