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
import java.security.PublicKey;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.access.DeviceHostKeyVerifier;
import net.netshot.netshot.access.Ssh;
import net.netshot.netshot.access.collector.SshServer;
import net.netshot.netshot.access.collector.TransferProtocol;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess.SshHostKeyVerification;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.testing.TestConnectors;
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

	/**
	 * Builds a helper for a device of a domain whose server address (the address of Netshot as the devices see it)
	 * is the loopback address.
	 *
	 * @param sshServer the SSH server
	 * @return the helper
	 */
	private JsConfigHelper deviceHelper(SshServer sshServer) throws Exception {
		Domain domain = Domain.builder().id(1).name("Lab").description("Lab domain")
			.server4Address(new Network4Address("127.0.0.1", 32)).build();
		DeviceDraft device = new DeviceDraft(Device.create("CiscoIOS12", "10.0.0.1", domain, "tester"));
		return new JsConfigHelper(null, device, null, null, null, sshServer, new FakeTaskContext());
	}

	/**
	 * Starts the embedded SSH server on the loopback interface, with its host keys in the temporary folder.
	 *
	 * @param port the port of the server
	 * @return the started server
	 */
	private SshServer startServer(int port) throws Exception {
		Properties config = new Properties();
		config.setProperty("netshot.sshserver.port", Integer.toString(port));
		config.setProperty("netshot.sshserver.listenaddress", "127.0.0.1");
		config.setProperty("netshot.sshserver.hostkeypath", this.tempDir.toString());
		config.setProperty("netshot.snapshots.binary.path", this.tempDir.toString());
		TestConfig.init(config);
		SshServer sshServer = new SshServer(Netshot.config());
		sshServer.start();
		return sshServer;
	}

	/**
	 * Gets the public host keys of the SSH server, as the tickets give them.
	 *
	 * @param sshServer the SSH server
	 * @return the base64 data of the keys, by key algorithm
	 */
	private static Map<String, String> hostKeys(SshServer sshServer) throws Exception {
		Map<String, String> keys = new TreeMap<>();
		for (PublicKey key : sshServer.getPublicHostKeys()) {
			String[] parts = PublicKeyEntry.toString(key).split(" ");
			keys.put(parts[0], parts[1]);
		}
		return keys;
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
		SshServer sshServer = this.startServer(TestNetwork.freePort());
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

	@Test
	@DisplayName("The ticket given to the driver holds the address, port, credentials and host keys of the SSH server")
	void ticketForDriver() throws Exception {
		int port = TestNetwork.freePort();
		SshServer sshServer = this.startServer(port);
		try {
			ProxyObject ticket = this.deviceHelper(sshServer).requestUpload("scp", null);
			Assertions.assertEquals("127.0.0.1", ticket.getMember("host"));
			Assertions.assertEquals(port, ticket.getMember("port"));
			Assertions.assertNotNull(ticket.getMember("username"));
			Assertions.assertNotNull(ticket.getMember("password"));
			ProxyObject hostKeys = (ProxyObject) ticket.getMember("hostkeys");
			Map<String, String> expected = hostKeys(sshServer);
			Assertions.assertFalse(expected.isEmpty());
			Assertions.assertEquals(expected.size(), ((ProxyArray) hostKeys.getMemberKeys()).getSize());
			for (Map.Entry<String, String> key : expected.entrySet()) {
				Assertions.assertEquals(key.getValue(), hostKeys.getMember(key.getKey()), key.getKey());
			}

			// A device trusting only these keys logs in with the ticket
			StringBuilder trusted = new StringBuilder();
			expected.forEach((algorithm, data) -> trusted.append(algorithm).append(' ').append(data).append('\n'));
			Ssh device = new Ssh(TestConnectors.sshClient(), "127.0.0.1", port, (String) ticket.getMember("username"),
				(String) ticket.getMember("password"), new FakeTaskContext());
			device.setConnectionTimeout(5000);
			device.setHostKeyVerifier(new DeviceHostKeyVerifier(SshHostKeyVerification.TRUST_KNOWN, trusted.toString(), null));
			try {
				device.connect(false);
				Assertions.assertNull(device.getLearnedSshHostKeys(), "A key not in the ticket was presented");
			}
			finally {
				device.disconnect();
			}
		}
		finally {
			sshServer.stop();
		}
	}

	@Test
	@DisplayName("The host keys are kept in netshot.sshserver.hostkeypath: the same after a restart")
	void hostKeysKeptAcrossRestarts() throws Exception {
		SshServer first = this.startServer(TestNetwork.freePort());
		Map<String, String> keys;
		try {
			keys = hostKeys(first);
		}
		finally {
			first.stop();
		}
		SshServer second = this.startServer(TestNetwork.freePort());
		try {
			Assertions.assertEquals(keys, hostKeys(second));
		}
		finally {
			second.stop();
		}
	}
}
