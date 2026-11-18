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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.fake.FakeTaskContext;

@UnitTest
@DisplayName("SCP downloads from the devices")
class SshScpTest {

	@TempDir
	Path tempDir;

	private SshServer server;

	/**
	 * Starts an SSH server which accepts any password, running the given commands.
	 *
	 * @param commands the command factory of the server
	 * @return the port of the server
	 */
	private int startServer(CommandFactory commands) throws IOException {
		this.server = SshServer.setUpDefaultServer();
		this.server.setHost("127.0.0.1");
		this.server.setPort(0);
		KeyPairProvider hostKeys = new SimpleGeneratorHostKeyProvider();
		this.server.setKeyPairProvider(hostKeys);
		this.server.setPasswordAuthenticator((_, _, _) -> true);
		this.server.setCommandFactory(commands);
		this.server.setFileSystemFactory(new VirtualFileSystemFactory(this.tempDir));
		this.server.start();
		return this.server.getPort();
	}

	/** A command which never answers, nor exits (a device which doesn't run the scp command). */
	private static final class SilentCommand implements Command {

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
			// Never exits
		}

		@Override
		public void start(ChannelSession channel, Environment env) {
			// Nothing to start
		}

		@Override
		public void destroy(ChannelSession channel) {
			// Nothing to stop
		}
	}

	@AfterEach
	void stopServer() throws IOException {
		if (this.server != null) {
			this.server.stop(true);
		}
	}

	private static Ssh ssh(int port) {
		Ssh ssh = new Ssh(TestConnectors.sshClient(), Ssh.Settings.from(TestConfig.store().current()), "127.0.0.1", port, "admin", "any",
			new FakeTaskContext());
		ssh.setConnectionTimeout(5000);
		ssh.setReceiveTimeout(1000);
		return ssh;
	}

	@Test
	@DisplayName("A file is downloaded")
	void download() throws IOException {
		Files.writeString(this.tempDir.resolve("config.txt"), "hostname router1\n", StandardCharsets.UTF_8);
		Ssh ssh = ssh(this.startServer(new ScpCommandFactory()));
		try {
			ssh.connect(false);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ssh.scpDownload("config.txt", out, false);
			Assertions.assertEquals("hostname router1\n", out.toString(StandardCharsets.UTF_8));
		}
		finally {
			ssh.disconnect();
		}
	}

	@Test
	@DisplayName("A download from a device which doesn't answer the scp command fails after the receive timeout")
	void receiveTimeout() throws IOException {
		Ssh ssh = ssh(this.startServer((_, _) -> new SilentCommand()));
		try {
			ssh.connect(false);
			IOException e = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(15),
				() -> Assertions.assertThrows(IOException.class, () -> ssh.scpDownload("config.txt", new ByteArrayOutputStream(), false)));
			Assertions.assertTrue(e.getMessage().contains("Timeout (1000)"), e.getMessage());
		}
		finally {
			ssh.disconnect();
		}
	}

	@Test
	@DisplayName("Through a new session too")
	void receiveTimeoutInNewSession() throws IOException {
		Ssh ssh = ssh(this.startServer((_, _) -> new SilentCommand()));
		Assertions.assertTimeoutPreemptively(Duration.ofSeconds(15),
			() -> Assertions.assertThrows(IOException.class, () -> ssh.scpDownload("config.txt", new ByteArrayOutputStream(), true)));
	}
}
