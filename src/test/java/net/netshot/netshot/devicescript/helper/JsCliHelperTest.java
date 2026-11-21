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
package net.netshot.netshot.devicescript.helper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.access.AccessManager;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeTaskContext;

/**
 * Tests the CLI helper of the drivers ({@link JsCliHelper}) against an SSH server playing a device: a prompt, and a
 * few commands. Covers the injection of the credentials into the commands, the expects, the clean-up of the
 * output, the errors (timeout, connection failure) and the credential sets tried in turn.
 */
@ScriptEngineTest
@DisplayName("CLI helper of the drivers")
class JsCliHelperTest {

	/** The password the device accepts. */
	private static final String GOOD_PASSWORD = "Good-Password-1";

	private static final String[] PROMPT = { "router# $" };

	private SshServer server;

	private FakeTaskContext taskContext;

	private AccessManager accessManager;

	/**
	 * A device shell: writes a prompt, then answers each line received. "show secret ..." returns its arguments,
	 * "colors" a colored text, "hang" nothing at all (no prompt), anything else "ok".
	 */
	private static final class DeviceShell implements Command, Runnable {

		private InputStream in;
		private OutputStream out;
		private ExitCallback exitCallback;
		private Thread thread;

		@Override
		public void setInputStream(InputStream input) {
			this.in = input;
		}

		@Override
		public void setOutputStream(OutputStream output) {
			this.out = output;
		}

		@Override
		public void setErrorStream(OutputStream err) {
			// Not used
		}

		@Override
		public void setExitCallback(ExitCallback callback) {
			this.exitCallback = callback;
		}

		@Override
		public void start(ChannelSession channel, Environment env) {
			this.thread = Thread.ofVirtual().start(this);
		}

		private void write(String text) throws IOException {
			this.out.write(text.getBytes(StandardCharsets.UTF_8));
			this.out.flush();
		}

		@Override
		public void run() {
			try {
				this.write("router# ");
				StringBuilder line = new StringBuilder();
				int c;
				while ((c = this.in.read()) >= 0) {
					if (c != '\r' && c != '\n') {
						line.append((char) c);
						continue;
					}
					String command = line.toString();
					line.setLength(0);
					if (command.isEmpty()) {
						continue;
					}
					if (command.equals("hang")) {
						continue;
					}
					if (command.startsWith("show secret ")) {
						this.write("\r\nsecret: " + command.substring("show secret ".length()));
					}
					else if (command.equals("colors")) {
						this.write("\r\n\u001b[31mred\u001b[0m text");
					}
					else {
						this.write("\r\nok");
					}
					this.write("\r\nrouter# ");
				}
			}
			catch (IOException e) {
				// Closed
			}
			this.exitCallback.onExit(0);
		}

		@Override
		public void destroy(ChannelSession channel) {
			if (this.thread != null) {
				this.thread.interrupt();
			}
		}
	}

	@BeforeEach
	void startDevice() throws IOException {
		TestConfig.init(new Properties());
		this.server = SshServer.setUpDefaultServer();
		this.server.setHost("127.0.0.1");
		this.server.setPort(0);
		this.server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
		this.server.setPasswordAuthenticator((_, password, _) -> GOOD_PASSWORD.equals(password));
		this.server.setShellFactory(_ -> new DeviceShell());
		this.server.start();
		this.taskContext = new FakeTaskContext();
	}

	@AfterEach
	void stopDevice() throws IOException {
		if (this.accessManager != null) {
			this.accessManager.disconnectAll();
		}
		this.server.stop(true);
	}

	private static DeviceCredentialSet account(String name, String password) {
		return DeviceSshAccount.builder().name(name).username(Secret.local("netshot")).password(Secret.local(password))
			.superPassword(Secret.local("Enable-Secret-1")).build();
	}

	/**
	 * Builds the CLI helper of a device reached on the given port, trying the given credential sets in order.
	 *
	 * @param port the SSH port of the device
	 * @param autoTryCredentials whether the next credential sets are tried automatically
	 * @param credentialSets the credential sets, in order
	 * @return the CLI helper
	 */
	private JsCliHelper cli(int port, boolean autoTryCredentials, DeviceCredentialSet... credentialSets) throws Exception {
		Domain domain = Domain.builder().name("Lab").description("Lab domain").build();
		DeviceAccess access = DeviceAccess.of("ssh").toBuilder().address("127.0.0.1").port(port).build();
		DeviceDraft device = new DeviceDraft(Device.create("CiscoIOS12", null, domain, "test").toBuilder().accesses(List.of(access)).build());
		this.accessManager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, this.taskContext,
			new LinkedHashSet<>(List.of(credentialSets)));
		DeviceDriver driver = TestDrivers.registry().forDevice(device);
		return new JsCliHelper(this.accessManager, driver.getDefaultCliAccessDefinitions(), autoTryCredentials, this.taskContext);
	}

	private JsCliHelper cli(boolean autoTryCredentials, DeviceCredentialSet... credentialSets) throws Exception {
		return this.cli(this.server.getPort(), autoTryCredentials, credentialSets);
	}

	@Test
	@DisplayName("Inserts the credentials into the command, and traces it without the passwords")
	void credentialsInCommand() throws Exception {
		JsCliHelper cli = this.cli(true, account("good", GOOD_PASSWORD));
		Assertions.assertEquals("ssh", cli.getResolvedAccessName());
		Assertions.assertNotNull(cli.send(PROMPT));
		String output = cli.send("show secret $$NetshotUsername$$ $$NetshotPassword$$ $$NetshotSuperPassword$$\r", PROMPT, 2000);

		Assertions.assertEquals("\r\nsecret: netshot " + GOOD_PASSWORD + " Enable-Secret-1\r\n", output);
		Assertions.assertFalse(cli.isErrored());
		Assertions.assertEquals("router# ", cli.getLastExpectMatch());
		Assertions.assertEquals(0, cli.getLastExpectMatchIndex());
		Assertions.assertEquals(PROMPT[0], cli.getLastExpectMatchPattern());
		Assertions.assertTrue(cli.getLastFullOutput().endsWith("router# "));
		String log = this.taskContext.getLog();
		Assertions.assertTrue(log.contains("show secret netshot $$NetshotPassword$$ $$NetshotSuperPassword$$"), log);
		Assertions.assertFalse(log.contains("About to send the following (secrets not inserted):\n[TRACE] show secret netshot " + GOOD_PASSWORD), log);
	}

	@Test
	@DisplayName("Returns the groups of the matched expect, and cleans up the output as asked")
	void expectsAndCleanUp() throws Exception {
		JsCliHelper cli = this.cli(true, account("good", GOOD_PASSWORD));
		cli.send(PROMPT);
		String output = cli.send("colors\r", new String[] { "^(\\w+)# $" }, 2000,
			new String[] { "STRIP_ANSI_CODES", "NORMALIZE_LINE_ENDINGS", "NOT_AN_ACTION" }, 50);

		Assertions.assertEquals("\nred text\n", output);
		Assertions.assertEquals("router", cli.getLastExpectMatchGroup(1));
		Assertions.assertNull(cli.getLastExpectMatchGroup(2));
		Assertions.assertEquals("colors\r", cli.getLastCommand());
	}

	@Test
	@DisplayName("Reports an error, with the received data in the trace, when the expect isn't found in time")
	void timeout() throws Exception {
		JsCliHelper cli = this.cli(true, account("good", GOOD_PASSWORD));
		cli.send(PROMPT);
		Assertions.assertNull(cli.send("hang\r", PROMPT, 300));
		Assertions.assertTrue(cli.isErrored());
		Assertions.assertFalse(cli.isConnectionFailed());
		String log = this.taskContext.getLog();
		Assertions.assertTrue(log.contains("I/O error: Timeout waiting for the command output."), log);
		Assertions.assertTrue(log.contains("The receive buffer is:"), log);
		// The next command works again
		Assertions.assertEquals("\r\nok\r\n", cli.send("next\r", PROMPT, 2000));
		Assertions.assertFalse(cli.isErrored());
	}

	@Test
	@DisplayName("Reports a connection failure")
	void connectionFailure() throws Exception {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			closedPort = socket.getLocalPort();
		}
		JsCliHelper cli = this.cli(closedPort, true, account("good", GOOD_PASSWORD));
		Assertions.assertNull(cli.send(PROMPT));
		Assertions.assertTrue(cli.isErrored());
		Assertions.assertTrue(cli.isConnectionFailed());
		Assertions.assertTrue(this.taskContext.getLog().contains("Unable to connect"), this.taskContext.getLog());
	}

	@Test
	@DisplayName("Tries the next credential set when the authentication fails")
	void credentialFallback() throws Exception {
		JsCliHelper cli = this.cli(true, account("wrong", "Wrong-Password"), account("good", GOOD_PASSWORD));
		Assertions.assertNotNull(cli.send(PROMPT));
		Assertions.assertEquals("\r\nsecret: " + GOOD_PASSWORD + "\r\n", cli.send("show secret $$NetshotPassword$$\r", PROMPT, 2000));
		String log = this.taskContext.getLog();
		Assertions.assertTrue(log.indexOf("using credentials 'wrong'") < log.indexOf("using credentials 'good'"), log);
	}

	@Test
	@DisplayName("Without autoTryCredentials, reports the failure and lets the driver try the next credential set")
	void manualCredentials() throws Exception {
		JsCliHelper cli = this.cli(false, account("wrong", "Wrong-Password"), account("good", GOOD_PASSWORD));
		Assertions.assertNull(cli.send(PROMPT));
		Assertions.assertTrue(cli.isErrored());
		Assertions.assertFalse(cli.isConnectionFailed(), "Only the credential set failed");

		Assertions.assertTrue(cli.tryNextCredentials());
		Assertions.assertNotNull(cli.send(PROMPT));
		Assertions.assertFalse(cli.isErrored());

		Assertions.assertFalse(cli.tryNextCredentials());
		Assertions.assertTrue(cli.isConnectionFailed());
	}

	@Test
	@DisplayName("After an in-band authentication failure, connects again with the next credential set")
	void inBandAuthFailure() throws Exception {
		JsCliHelper cli = this.cli(true, account("first", GOOD_PASSWORD), account("second", GOOD_PASSWORD));
		cli.reconnect();
		cli.resetForInBandAuthFailure();
		cli.reconnect();
		Assertions.assertNotNull(cli.send(PROMPT));
		Assertions.assertTrue(this.taskContext.getLog().contains("using credentials 'second'"), this.taskContext.getLog());
	}

	@Test
	@DisplayName("Removes the echo of the command from the output")
	void removeEcho() throws Exception {
		JsCliHelper cli = this.cli(true, account("good", GOOD_PASSWORD));
		Assertions.assertEquals("result\nrouter# ", cli.removeEcho("show version  \r\nresult\nrouter# ", "show version\r\n"));
		Assertions.assertEquals("other output", cli.removeEcho("other output", "show version"));
	}
}
