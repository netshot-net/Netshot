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

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.netshot.netshot.platform.net.NetworkAddress;
import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.TestNetwork;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("SSH server service")
class SshServerTest {

	@TempDir
	Path tempDir;

	@AfterEach
	void resetConfig() {
		TestConfig.init(new Properties());
	}

	@Test
	@DisplayName("A port in use fails the start (the service manager then stops the server); nothing is read before the start")
	void portUsed() throws Exception {
		try (ServerSocket used = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
			Properties config = new Properties();
			config.setProperty("netshot.sshserver.port", Integer.toString(used.getLocalPort()));
			config.setProperty("netshot.sshserver.listenaddress", "127.0.0.1");
			config.setProperty("netshot.sshserver.hostkeypath", this.tempDir.toString());
			TestConfig.init(config);
			SshServer server = new SshServer(TestConfig.store());
			Assertions.assertNull(server.settings(), "Settings read before the start");
			Assertions.assertThrows(IOException.class, server::start);
			server.stop();
			Assertions.assertFalse(server.isHealthy());
			Assertions.assertFalse(server.isRunning());
		}
	}

	/**
	 * An upload ticket accepting one password, valid or not.
	 *
	 * @param username the username of the ticket
	 * @param password the accepted password
	 * @param valid whether the ticket is still valid
	 * @param rootPath the folder of the uploaded files
	 * @return the ticket
	 */
	private static UploadTicket ticket(String username, String password, boolean valid, Path rootPath) {
		return new UploadTicket() {
			@Override
			public Owner getOwner() {
				return null;
			}

			@Override
			public Set<TransferProtocol> getAllowedProtocols() {
				return Set.of(TransferProtocol.SCP, TransferProtocol.SFTP);
			}

			@Override
			public String getUsername() {
				return username;
			}

			@Override
			public boolean checkPassword(NetworkAddress source, String candidate) {
				return password.equals(candidate);
			}

			@Override
			public Path getRootPath() {
				return rootPath;
			}

			@Override
			public boolean isValid() {
				return valid;
			}

			@Override
			public void onSessionStarted(SshServer.SessionLogBuffer logBuffer) {
				// Nothing to do
			}

			@Override
			public void onSessionRejected(SshServer.SessionLogBuffer logBuffer) {
				// Nothing to do
			}

			@Override
			public void onProtocolRefused(TransferProtocol protocol) {
				// Nothing to do
			}

			@Override
			public void onSessionStopped() {
				// Nothing to do
			}

			@Override
			public boolean onFileWritten(Path filePath) {
				return true;
			}
		};
	}

	private static boolean login(SshClient client, int port, String username, String password) throws IOException {
		try (ClientSession session = client.connect(username, "127.0.0.1", port).verify(30000).getSession()) {
			session.addPasswordIdentity(password);
			session.auth().verify(30000);
			return true;
		}
		catch (IOException e) {
			return false;
		}
	}

	@Test
	@DisplayName("A device can't log in with an unknown ticket, a ticket which isn't valid anymore, or a wrong password; unknown tickets and wrong passwords are audited")
	void ticketRefusals() throws Exception {
		int port = TestNetwork.freePort();
		Properties config = new Properties();
		config.setProperty("netshot.sshserver.port", Integer.toString(port));
		config.setProperty("netshot.sshserver.listenaddress", "127.0.0.1");
		config.setProperty("netshot.sshserver.hostkeypath", this.tempDir.toString());
		TestConfig.init(config);
		Logger aaa = (Logger) LoggerFactory.getLogger("AAA");
		ListAppender<ILoggingEvent> audit = new ListAppender<>();
		audit.start();
		aaa.addAppender(audit);
		SshServer server = new SshServer(TestConfig.store());
		SshClient client = SshClient.setUpDefaultClient();
		client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
		try {
			server.start();
			client.start();
			server.registerUploadTicket(ticket("valid-ticket", "secret", true, this.tempDir));
			server.registerUploadTicket(ticket("old-ticket", "secret", false, this.tempDir));
			Assertions.assertThrows(IOException.class, () -> server.registerUploadTicket(ticket("valid-ticket", "other", true, this.tempDir)),
				"a ticket name is unique");

			// The accepted login first: it also waits for the server to be ready
			Assertions.assertTrue(login(client, port, "valid-ticket", "secret"));
			Assertions.assertFalse(login(client, port, "unknown-ticket", "secret"), "unknown ticket");
			Assertions.assertFalse(login(client, port, "old-ticket", "secret"), "invalid ticket");
			Assertions.assertFalse(login(client, port, "valid-ticket", "wrong"), "wrong password");

			List<String> messages = audit.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
			Assertions.assertTrue(messages.stream().anyMatch(m -> m.contains(": unknown ticket unknown-ticket")), messages.toString());
			Assertions.assertTrue(messages.stream().anyMatch(m -> m.contains(": credentials rejected for ticket valid-ticket")), messages.toString());
		}
		finally {
			aaa.detachAppender(audit);
			client.stop();
			server.stop();
		}
	}
}
