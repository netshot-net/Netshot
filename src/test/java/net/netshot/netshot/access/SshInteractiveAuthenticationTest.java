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
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.keyboard.InteractiveChallenge;
import org.apache.sshd.server.auth.keyboard.KeyboardInteractiveAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.protocol.SshConfig;
import net.netshot.netshot.driver.protocol.SshInteractionInstruction;
import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.fake.FakeTaskContext;

/**
 * The SSH client against a server asking for keyboard-interactive or public key authentication: the answers to the
 * prompts (password, driver instructions) and what the task log tells.
 */
@UnitTest
@DisplayName("SSH keyboard-interactive and public key authentication")
class SshInteractiveAuthenticationTest {

	private static SshServer server;

	private static KeyPair goodKey;

	private static KeyPair otherKey;

	/**
	 * Keyboard-interactive: the user "single" gets one "Password: " prompt, the user "pin" one "PIN code: " prompt; the
	 * user "multi" gets "Username: " (echoed) and "Token: " prompts, and must answer "multi" and "good".
	 */
	private static final class Authenticator implements KeyboardInteractiveAuthenticator {
		@Override
		public InteractiveChallenge generateChallenge(ServerSession session, String username, String lang, String subMethods) {
			InteractiveChallenge challenge = new InteractiveChallenge();
			challenge.setInteractionName("Lab login");
			challenge.setInteractionInstruction("Answer the prompts");
			if ("multi".equals(username)) {
				challenge.addPrompt("Username: ", true);
				challenge.addPrompt("Token: ", false);
			}
			else if ("pin".equals(username)) {
				challenge.addPrompt("PIN code: ", false);
			}
			else {
				challenge.addPrompt("Password: ", false);
			}
			return challenge;
		}

		@Override
		public boolean authenticate(ServerSession session, String username, List<String> responses) {
			if ("multi".equals(username)) {
				return responses.equals(List.of("multi", "good"));
			}
			return responses.equals(List.of("good"));
		}
	}

	@BeforeAll
	static void startServer() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		goodKey = generator.generateKeyPair();
		otherKey = generator.generateKeyPair();
		server = SshServer.setUpDefaultServer();
		server.setHost("127.0.0.1");
		server.setPort(0);
		SimpleGeneratorHostKeyProvider hostKey = new SimpleGeneratorHostKeyProvider();
		hostKey.loadKeys(null);
		server.setKeyPairProvider(hostKey);
		server.setPasswordAuthenticator(null);
		server.setKeyboardInteractiveAuthenticator(new Authenticator());
		server.setPublickeyAuthenticator((_, key, _) -> KeyUtils.compareKeys(goodKey.getPublic(), key));
		CoreModuleProperties.WELCOME_BANNER.set(server, "Authorized access only");
		server.start();
	}

	@AfterAll
	static void stopServer() throws IOException {
		server.stop(true);
	}

	/**
	 * Connects with a password, the given interaction instructions of the driver (or none).
	 *
	 * @param username the user name
	 * @param password the password
	 * @param instructions the interaction instructions, or null
	 * @param context the task context
	 * @return the exception which failed the connection, or null
	 */
	private static Exception connect(String username, String password, List<SshInteractionInstruction> instructions,
			FakeTaskContext context) {
		Ssh ssh = new Ssh(TestConnectors.sshClient(), Ssh.Settings.from(TestConfig.store().current()), "127.0.0.1", server.getPort(),
			username, password, context);
		ssh.setConnectionTimeout(5000);
		if (instructions != null) {
			SshConfig config = new SshConfig(Ssh.Settings.from(TestConfig.store().current()));
			config.setInteractionInstructions(instructions);
			ssh.applySshConfig(config);
		}
		return run(ssh);
	}

	private static Exception run(Ssh ssh) {
		try {
			ssh.connect(false);
			return null;
		}
		catch (Exception e) {
			return e;
		}
		finally {
			ssh.disconnect();
		}
	}

	private static String privateKey(KeyPair key) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(key, "lab", null, output);
		return output.toString(StandardCharsets.US_ASCII);
	}

	@Test
	@DisplayName("A single \"Password:\" prompt gets the password (answered by the SSH library); the banner is in the task log")
	void singlePasswordPrompt() {
		FakeTaskContext context = new FakeTaskContext();
		Assertions.assertNull(connect("single", "good", null, context), context.getLog());
		Assertions.assertTrue(context.getLog().contains("[DEBUG] Welcome banner: Authorized access only"), context.getLog());
	}

	@Test
	@DisplayName("Another single hidden prompt gets the password too; the prompts are in the task log")
	void singleOtherPrompt() {
		FakeTaskContext context = new FakeTaskContext();
		Assertions.assertNull(connect("pin", "good", null, context), context.getLog());
		String log = context.getLog();
		Assertions.assertTrue(log.contains("SSH user interactive authentication, name 'Lab login', instruction 'Answer the prompts', "
			+ "lang '', prompts PIN code:  (no echo)"), log);
		Assertions.assertTrue(log.contains("Password requested in user interactive mode (prompt 'PIN code: ')"), log);
	}

	@Test
	@DisplayName("A wrong password at the prompt is invalid credentials")
	void wrongPassword() {
		FakeTaskContext context = new FakeTaskContext();
		Assertions.assertInstanceOf(InvalidCredentialsException.class, connect("single", "bad", null, context), context.getLog());
	}

	@Test
	@DisplayName("Several prompts without driver instructions fail the login, with an error in the task log")
	void multiplePromptsWithoutInstructions() {
		FakeTaskContext context = new FakeTaskContext();
		Assertions.assertNotNull(connect("multi", "good", null, context));
		Assertions.assertTrue(context.getLog().contains("[ERROR] Multiple prompts returned by device in SSH user interactive mode"),
			context.getLog());
	}

	@Test
	@DisplayName("The driver instructions answer each prompt: a planned response (with the user name placeholder) or the password")
	void instructions() {
		List<SshInteractionInstruction> instructions = List.of(
			new SshInteractionInstruction(Pattern.compile("Username: *"), true, DeviceDriver.PLACEHOLDER_USERNAME),
			new SshInteractionInstruction(Pattern.compile("Token: *"), false, null));
		FakeTaskContext context = new FakeTaskContext();
		Assertions.assertNull(connect("multi", "good", instructions, context), context.getLog());
		String log = context.getLog();
		Assertions.assertTrue(log.contains("Will send planned response '" + DeviceDriver.PLACEHOLDER_USERNAME + "'"), log);
		Assertions.assertTrue(log.contains("Will send device password"), log);
		Assertions.assertFalse(log.contains("good"), "The password isn't logged: " + log);
	}

	@Test
	@DisplayName("A prompt without matching instruction (pattern or echo) is answered empty, with a warning")
	void promptWithoutInstruction() {
		List<SshInteractionInstruction> instructions = List.of(
			new SshInteractionInstruction(Pattern.compile("Username: *"), false, DeviceDriver.PLACEHOLDER_USERNAME),
			new SshInteractionInstruction(null, false, null));
		FakeTaskContext context = new FakeTaskContext();
		Assertions.assertNotNull(connect("multi", "good", instructions, context));
		Assertions.assertTrue(context.getLog().contains("[WARN] No driver instruction for SSH user interaction prompt 'Username: '"),
			context.getLog());
	}

	@Test
	@DisplayName("A private key accepted by the server logs in; its type and fingerprint are in the task log")
	void publicKey() throws Exception {
		FakeTaskContext context = new FakeTaskContext();
		Ssh ssh = new Ssh(TestConnectors.sshClient(), Ssh.Settings.from(TestConfig.store().current()), "127.0.0.1", server.getPort(),
			"keyuser", privateKey(goodKey), null, context);
		ssh.setConnectionTimeout(5000);
		Assertions.assertNull(run(ssh), context.getLog());
		Assertions.assertTrue(context.getLog().contains("SSH public key authentication attempt (service ssh-connection), key type ssh-rsa, fingerprint "
			+ KeyUtils.getFingerPrint(goodKey.getPublic())), context.getLog());
	}

	@Test
	@DisplayName("A private key refused by the server is invalid credentials, with the refused key in the task log")
	void refusedKey() throws Exception {
		FakeTaskContext context = new FakeTaskContext();
		Ssh ssh = new Ssh(TestConnectors.sshClient(), Ssh.Settings.from(TestConfig.store().current()), "127.0.0.1", server.getPort(),
			"keyuser", privateKey(otherKey), null, context);
		ssh.setConnectionTimeout(5000);
		Assertions.assertInstanceOf(InvalidCredentialsException.class, run(ssh), context.getLog());
		Assertions.assertTrue(context.getLog().contains("[WARN] SSH public key authentication failed (service ssh-connection), key type ssh-rsa, "
			+ "fingerprint " + KeyUtils.getFingerPrint(otherKey.getPublic())), context.getLog());
	}
}
