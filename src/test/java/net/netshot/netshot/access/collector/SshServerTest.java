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
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.testing.TestConfig;
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
}
