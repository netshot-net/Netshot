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
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.access.collector.SshServer;
import net.netshot.netshot.access.collector.TransferProtocol;
import net.netshot.netshot.testing.TestNetwork;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.fake.FakeTaskContext;

@UnitTest
@DisplayName("Upload tickets of the configuration helper")
class JsConfigHelperUploadTest {

	@TempDir
	Path tempDir;

	@AfterEach
	void resetConfig() {
		TestConfig.init(new Properties());
	}

	private JsConfigHelper helper(SshServer sshServer) {
		return new JsConfigHelper(null, null, null, null, null, sshServer, new FakeTaskContext());
	}

	@Test
	@DisplayName("Without SSH server, an upload ticket can't be requested")
	void noSshServer() {
		IOException thrown = Assertions.assertThrows(IOException.class,
			() -> this.helper(null).requestUpload(Set.of(TransferProtocol.SCP), null));
		Assertions.assertTrue(thrown.getMessage().contains("not running"));
	}

	@Test
	@DisplayName("With the SSH server, the ticket is registered, then cleared when the helper is released")
	void ticketRegistered() throws Exception {
		Properties config = new Properties();
		config.setProperty("netshot.sshserver.port", Integer.toString(TestNetwork.freePort()));
		config.setProperty("netshot.sshserver.listenaddress", "127.0.0.1");
		config.setProperty("netshot.sshserver.hostkeypath", this.tempDir.toString());
		config.setProperty("netshot.snapshots.binary.path", this.tempDir.toString());
		TestConfig.init(config);
		SshServer sshServer = new SshServer(Netshot.config());
		sshServer.start();
		try {
			Assertions.assertTrue(sshServer.isRunning());
			JsConfigHelper helper = this.helper(sshServer);
			Assertions.assertNotNull(helper.requestUpload(Set.of(TransferProtocol.SCP), null));
			Assertions.assertEquals(1, sshServer.stats().get("tickets"));
			helper.release();
			Assertions.assertEquals(0, sshServer.stats().get("tickets"));
		}
		finally {
			sshServer.stop();
		}
		Assertions.assertFalse(sshServer.isRunning());
	}
}
