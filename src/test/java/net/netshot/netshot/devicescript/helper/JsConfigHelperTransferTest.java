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
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.scp.client.ScpClientCreator;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.client.extensions.CopyFileExtension;
import org.apache.sshd.sftp.client.extensions.openssh.OpenSSHFsyncExtension;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.access.AccessManager;
import net.netshot.netshot.access.Ssh;
import net.netshot.netshot.access.collector.Collector;
import net.netshot.netshot.access.collector.SshServer;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DeviceDriver.Location;
import net.netshot.netshot.driver.DeviceDriver.LocationType;
import net.netshot.netshot.inventory.config.ConfigDraft;
import net.netshot.netshot.inventory.config.attribute.BinaryFileStorage;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestNetwork;
import net.netshot.netshot.testing.fake.FakeTaskContext;

/**
 * Transfers of binary files into the configuration attributes, through the configuration helper: downloads from
 * the device (SCP, SFTP), and uploads by the device to the embedded SSH server (upload tickets).
 */
@ScriptEngineTest
@DisplayName("Binary file transfers of the configuration helper")
@TestInstance(Lifecycle.PER_CLASS)
class JsConfigHelperTransferTest {

	/** The driver of the test device, with a binary file and a text configuration attribute. */
	private static final String DRIVER = "TransferTestDriver";

	/** Content of the transferred file: all the byte values. */
	private static final byte[] PAYLOAD = new byte[256];

	static {
		for (int i = 0; i < PAYLOAD.length; i++) {
			PAYLOAD[i] = (byte) i;
		}
	}

	@TempDir
	Path tempDir;

	Path storageDir;
	DeviceDraft device;
	ConfigDraft config;
	FakeTaskContext taskContext;

	@BeforeAll
	void registerDriver() throws Exception {
		String driverJs = """
			var Info = { name: "%s", author: "test", description: "Test driver for the file transfers", version: "1.0" };
			var Config = {
				"backupArchive": { type: "BinaryFile", title: "Backup" },
				"version": { type: "Text", title: "Version" },
			};
			var Device = {};
			var CLI = { ssh: {} };
			function snapshot(cli, device, config) {
			}
			""".formatted(DRIVER);
		DeviceDriver driver = new DeviceDriver(new StringReader(driverJs), DRIVER + ".js", new Location(LocationType.EMBEDDED, DRIVER + ".js"));
		TestDrivers.registry().getDrivers().put(DRIVER, driver);
	}

	@BeforeEach
	void setUp() throws Exception {
		this.storageDir = Files.createDirectories(this.tempDir.resolve("storage"));
		Properties properties = new Properties();
		properties.setProperty("netshot.snapshots.binary.path", this.storageDir.toString());
		properties.setProperty("netshot.sshserver.listenaddress", "127.0.0.1");
		properties.setProperty("netshot.sshserver.hostkeypath", Files.createDirectories(this.tempDir.resolve("keys")).toString());
		properties.setProperty("netshot.sshserver.port", Integer.toString(TestNetwork.freePort()));
		TestConfig.init(properties);
		Domain domain = Domain.builder().id(1).name("Lab").description("Lab domain")
			.server4Address(new Network4Address("127.0.0.1", 32)).build();
		this.device = new DeviceDraft(Device.create(DRIVER, "127.0.0.1", domain, "tester"));
		this.config = new ConfigDraft(this.device.getId());
		this.taskContext = new FakeTaskContext();
	}

	@AfterEach
	void resetConfig() {
		TestConfig.init(new Properties());
	}

	private JsConfigHelper helper(JsCliHelper cli, SshServer sshServer) {
		return new JsConfigHelper(TestDrivers.registry(), this.device, this.config, null, cli, sshServer,
			Collector.Settings.from(TestConfig.store().current()), new BinaryFileStorage(TestConfig.store()), this.taskContext);
	}

	private ConfigBinaryFileAttribute backupAttribute() {
		return (ConfigBinaryFileAttribute) this.config.getAttributeMap().get("backupArchive");
	}

	/**
	 * Checks the binary file attribute: its size, checksum and stored file.
	 *
	 * @param originalName the expected original name of the file
	 */
	private void assertStoredPayload(String originalName) throws IOException {
		ConfigBinaryFileAttribute attribute = this.backupAttribute();
		Assertions.assertNotNull(attribute, "The binary file attribute is set");
		Assertions.assertEquals(originalName, attribute.originalName());
		Assertions.assertEquals(PAYLOAD.length, attribute.fileSize());
		Assertions.assertEquals("sha256:" + DigestUtils.sha256Hex(PAYLOAD), attribute.checksum());
		Path stored = new BinaryFileStorage(TestConfig.store()).pendingFilePath(attribute, this.device.getId());
		Assertions.assertArrayEquals(PAYLOAD, Files.readAllBytes(stored));
	}

	/**
	 * Lists the pending files of the storage folder (the temporary folders of the upload tickets apart).
	 *
	 * @return the files
	 */
	private List<Path> pendingFiles() throws IOException {
		try (var files = Files.list(this.storageDir)) {
			return files.filter(Files::isRegularFile).toList();
		}
	}

	@Nested
	@DisplayName("Download from the device")
	@TestInstance(Lifecycle.PER_CLASS)
	class Download {

		org.apache.sshd.server.SshServer deviceServer;
		Path remoteDir;
		Ssh ssh;
		JsCliHelper cli;

		/** Starts an SSH server (the device) serving its files by SCP and SFTP, and connects to it. */
		@BeforeEach
		void startDevice() throws Exception {
			this.remoteDir = Files.createDirectories(tempDir.resolve("device"));
			Files.write(this.remoteDir.resolve("backup.tgz"), PAYLOAD);
			this.deviceServer = org.apache.sshd.server.SshServer.setUpDefaultServer();
			this.deviceServer.setHost("127.0.0.1");
			this.deviceServer.setPort(0);
			this.deviceServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
			this.deviceServer.setPasswordAuthenticator((_, _, _) -> true);
			this.deviceServer.setCommandFactory(new ScpCommandFactory());
			this.deviceServer.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
			this.deviceServer.setFileSystemFactory(new VirtualFileSystemFactory(this.remoteDir));
			this.deviceServer.start();
			this.ssh = new Ssh(TestConnectors.sshClient(), Ssh.Settings.from(TestConfig.store().current()), "127.0.0.1",
				this.deviceServer.getPort(), "admin", "admin", taskContext);
			this.ssh.setConnectionTimeout(5000);
			this.ssh.connect(false);
			AccessManager accessManager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, taskContext, null);
			accessManager.forceClientForTest(this.ssh, DeviceSshAccount.builder().username(Secret.local("admin"))
				.password(Secret.local("admin")).name("test-ssh-account").build());
			this.cli = new JsCliHelper(accessManager, List.of(), true, taskContext);
		}

		@AfterEach
		void stopDevice() throws IOException {
			this.ssh.disconnect();
			this.deviceServer.stop(true);
		}

		@Test
		@DisplayName("By SCP, checked against a SHA256 checksum, stored under the remote file name")
		void scpWithSha256() throws Exception {
			helper(this.cli, null).download("backupArchive", "scp", "backup.tgz", null, false, DigestUtils.sha256Hex(PAYLOAD).toUpperCase());

			assertStoredPayload("backup.tgz");
		}

		@Test
		@DisplayName("By SFTP in a new session, checked against an MD5 checksum, stored under the given name")
		void sftpWithMd5() throws Exception {
			helper(this.cli, null).download("backupArchive", "sftp", "backup.tgz", "  archive.tgz ", true, DigestUtils.md5Hex(PAYLOAD));

			assertStoredPayload("archive.tgz");
		}

		@Test
		@DisplayName("Without checksum, the file is stored as is")
		void withoutChecksum() throws Exception {
			helper(this.cli, null).download("backupArchive", "sftp", "backup.tgz", null, false, null);

			assertStoredPayload("backup.tgz");
		}

		@Test
		@DisplayName("A checksum mismatch fails the download, the file is removed")
		void checksumMismatch() throws Exception {
			JsConfigHelper helper = helper(this.cli, null);

			IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper.download("backupArchive", "scp", "backup.tgz", null, false, DigestUtils.sha256Hex("other")));

			Assertions.assertTrue(thrown.getMessage().contains("Invalid computed SHA256 hash"), thrown.getMessage());
			Assertions.assertNull(backupAttribute());
			Assertions.assertEquals(List.of(), pendingFiles(), "The downloaded file is removed");
		}

		@Test
		@DisplayName("A checksum of an unknown kind fails the download")
		void unknownChecksumKind() {
			JsConfigHelper helper = helper(this.cli, null);

			IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper.download("backupArchive", "scp", "backup.tgz", null, false, "abcdef"));

			Assertions.assertTrue(thrown.getMessage().contains("checksum length (6 characters)"), thrown.getMessage());
			Assertions.assertNull(backupAttribute());
		}

		@Test
		@DisplayName("A missing remote file fails the download, without leftover file")
		void missingRemoteFile() throws Exception {
			JsConfigHelper helper = helper(this.cli, null);

			Assertions.assertThrows(IOException.class, () -> helper.download("backupArchive", "sftp", "missing.tgz", null, false, null));

			Assertions.assertNull(backupAttribute());
			Assertions.assertEquals(List.of(), pendingFiles());
		}

		@Test
		@DisplayName("Only into a known binary file attribute, with a supported method and a remote file name")
		void invalidRequests() {
			JsConfigHelper helper = helper(this.cli, null);

			Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper.download("unknown", "scp", "backup.tgz", null, false, null), "Unknown attribute");
			Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper.download("version", "scp", "backup.tgz", null, false, null), "Not a binary file attribute");
			Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper.download("backupArchive", "ftp", "backup.tgz", null, false, null), "Unsupported method");
			Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper.download("backupArchive", null, "backup.tgz", null, false, null), "No method");
			Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper.download("backupArchive", "scp", null, null, false, null), "No remote file");
			Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper.download("backupArchive", "scp", "/", null, false, null), "No usable store name");
			Assertions.assertNull(backupAttribute());
		}

		@Test
		@DisplayName("Not without CLI access")
		void withoutCli() {
			IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper(null, null).download("backupArchive", "scp", "backup.tgz", null, false, null));

			Assertions.assertTrue(thrown.getMessage().contains("no CLI access"), thrown.getMessage());
		}
	}

	@Nested
	@DisplayName("Upload by the device")
	@TestInstance(Lifecycle.PER_CLASS)
	class Upload {

		SshServer sshServer;
		SshClient deviceClient;

		@BeforeEach
		void startServer() throws Exception {
			this.sshServer = new SshServer(TestConfig.store());
			this.sshServer.start();
			this.deviceClient = SshClient.setUpDefaultClient();
			this.deviceClient.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
			this.deviceClient.start();
		}

		@AfterEach
		void stopServer() throws IOException {
			this.deviceClient.stop();
			this.sshServer.stop();
		}

		/**
		 * Logs in to the embedded SSH server with the credentials of a ticket, as the device does.
		 *
		 * @param ticket the ticket given to the driver
		 * @return the authenticated session
		 */
		private ClientSession login(ProxyObject ticket) throws IOException {
			ClientSession session = this.deviceClient.connect((String) ticket.getMember("username"),
				(String) ticket.getMember("host"), (Integer) ticket.getMember("port")).verify(5000).getSession();
			session.addPasswordIdentity((String) ticket.getMember("password"));
			session.auth().verify(5000);
			return session;
		}

		@Test
		@DisplayName("A file uploaded by SCP is listed once the session is over, then committed to the attribute")
		void scpUpload() throws Exception {
			JsConfigHelper helper = helper(null, this.sshServer);
			ProxyObject ticket = helper.requestUpload("scp", null);
			long ticketId = (Long) ticket.getMember("id");
			Path local = Files.write(tempDir.resolve("local.tgz"), PAYLOAD);
			try (ClientSession session = this.login(ticket)) {
				ScpClientCreator.instance().createScpClient(session).upload(local, "backup.tgz");
			}

			ProxyObject result = helper.awaitUpload(ticketId, 10000);

			Assertions.assertEquals(true, result.getMember("completed"));
			Assertions.assertEquals(1, result.getMember("fileCount"));
			ProxyObject file = (ProxyObject) ((ProxyArray) result.getMember("files")).get(0);
			Assertions.assertEquals("backup.tgz", file.getMember("name"));
			Assertions.assertEquals((long) PAYLOAD.length, file.getMember("size"));

			helper.commitUpload(ticketId, (Long) file.getMember("id"), "backupArchive", null, DigestUtils.sha256Hex(PAYLOAD));

			assertStoredPayload("backup.tgz");
			helper.release();
			Assertions.assertEquals(0, this.sshServer.stats().get("tickets"), "The ticket is cleared with the helper");
		}

		@Test
		@DisplayName("A file uploaded by SFTP is committed under the given name; a checksum mismatch is refused")
		void sftpUpload() throws Exception {
			JsConfigHelper helper = helper(null, this.sshServer);
			ProxyObject ticket = helper.requestUpload("sftp", "127.0.0.1");
			long ticketId = (Long) ticket.getMember("id");
			try (ClientSession session = this.login(ticket);
				SftpClient sftp = SftpClientFactory.instance().createSftpClient(session);
				OutputStream out = sftp.write("upload.bin")) {
				out.write(PAYLOAD);
			}
			ProxyObject result = helper.awaitUpload(ticketId, 10000);
			long fileId = (Long) ((ProxyObject) ((ProxyArray) result.getMember("files")).get(0)).getMember("id");

			Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper.commitUpload(ticketId, fileId, "backupArchive", null, DigestUtils.md5Hex("other")));
			Assertions.assertNull(backupAttribute());

			// The file is still there
			helper.commitUpload(ticketId, fileId, "backupArchive", "archive.bin", null);
			assertStoredPayload("archive.bin");
			helper.release();
		}

		/**
		 * Lists the files this process holds open (Linux only: empty elsewhere).
		 *
		 * @param suffix the end of the names of the files to list
		 * @return the paths of the open files
		 */
		private static List<String> openFiles(String suffix) throws IOException {
			Path fds = Path.of("/proc/self/fd");
			if (!Files.isDirectory(fds)) {
				return List.of();
			}
			List<String> open = new java.util.ArrayList<>();
			try (var links = Files.list(fds)) {
				for (Path link : links.toList()) {
					try {
						String target = Files.readSymbolicLink(link).toString();
						if (target.endsWith(suffix)) {
							open.add(target);
						}
					}
					catch (IOException e) {
						// Closed meanwhile
					}
				}
			}
			return open;
		}

		@Test
		@DisplayName("In an SFTP session, the device can only write files: other changes are refused, permissions ignored, and it can't leave its folder")
		void sftpRestrictions() throws Exception {
			JsConfigHelper helper = helper(null, this.sshServer);
			ProxyObject ticket = helper.requestUpload("sftp", null);
			long ticketId = (Long) ticket.getMember("id");
			try (ClientSession session = this.login(ticket);
				SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
				try (OutputStream out = sftp.write("upload.bin")) {
					out.write(PAYLOAD);
				}
				try (OutputStream out = sftp.write("../../escape.bin")) {
					out.write(PAYLOAD);
				}
				Assertions.assertThrows(IOException.class, () -> sftp.mkdir("folder"), "mkdir");
				Assertions.assertThrows(IOException.class, () -> sftp.remove("upload.bin"), "rm");
				Assertions.assertThrows(IOException.class, () -> sftp.rename("upload.bin", "moved.bin"), "rename");
				Assertions.assertThrows(IOException.class, () -> sftp.symLink("link.bin", "upload.bin"), "symlink");
				int permissions = sftp.stat("upload.bin").getPermissions() & 0777;
				sftp.setStat("upload.bin", new SftpClient.Attributes().perms(0777));
				Assertions.assertEquals(permissions, sftp.stat("upload.bin").getPermissions() & 0777, "chmod is ignored");
				Assertions.assertEquals(0, sftp.stat(".").getPermissions() & 0077, "The folder of the ticket is private");
				Assertions.assertEquals(PAYLOAD.length, sftp.stat("upload.bin").getSize(), "The file is still there");
			}
			ProxyObject result = helper.awaitUpload(ticketId, 10000);
			Assertions.assertEquals(2, result.getMember("fileCount"));
			Assertions.assertEquals(List.of(), openFiles(".bin"), "The received files are closed");
			try (var files = Files.walk(tempDir)) {
				List<Path> written = files.filter(f -> f.getFileName().toString().endsWith(".bin")).toList();
				Assertions.assertEquals(2, written.size(), "Written once each " + written);
				Assertions.assertEquals(written.get(0).getParent(), written.get(1).getParent(), "Both in the folder of the ticket: " + written);
			}
			helper.release();
		}

		@Test
		@DisplayName("In an SFTP session, the links, copies, locks, syncs and time changes are refused; the folder can be listed")
		void sftpOtherOperations() throws Exception {
			JsConfigHelper helper = helper(null, this.sshServer);
			ProxyObject ticket = helper.requestUpload("sftp", null);
			try (ClientSession session = this.login(ticket);
				SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
				try (OutputStream out = sftp.write("upload.bin")) {
					out.write(PAYLOAD);
				}
				List<String> names = new ArrayList<>();
				sftp.readDir(".").forEach(entry -> names.add(entry.getFilename()));
				Assertions.assertTrue(names.contains("upload.bin"), names.toString());

				Assertions.assertThrows(IOException.class, () -> sftp.link("upload.bin", "hard.bin", false), "hard link");
				Assertions.assertThrows(IOException.class, () -> sftp.readLink("upload.bin"), "readlink");
				Assertions.assertThrows(IOException.class,
					() -> sftp.setStat("upload.bin", new SftpClient.Attributes().modifyTime(1000)), "time change");
				Assertions.assertThrows(IOException.class,
					() -> sftp.getExtension(CopyFileExtension.class).copyFile("upload.bin", "copy.bin", false), "copy");
				try (SftpClient.CloseableHandle handle = sftp.open("upload.bin", SftpClient.OpenMode.Read)) {
					Assertions.assertThrows(IOException.class, () -> sftp.lock(handle, 0, 1, 0), "lock");
				}
				try (SftpClient.CloseableHandle handle = sftp.open("upload.bin", SftpClient.OpenMode.Write)) {
					Assertions.assertThrows(IOException.class, () -> sftp.getExtension(OpenSSHFsyncExtension.class).fsync(handle), "fsync");
				}
				Assertions.assertThrows(IOException.class, () -> sftp.stat("hard.bin"), "Hard link created");
				Assertions.assertThrows(IOException.class, () -> sftp.stat("copy.bin"), "Copy created");
			}
			helper.release();
		}

		@Test
		@DisplayName("The device can't download a file by SCP")
		void scpDownloadRefused() throws Exception {
			JsConfigHelper helper = helper(null, this.sshServer);
			ProxyObject ticket = helper.requestUpload("scp", null);
			Path local = Files.write(tempDir.resolve("local.tgz"), PAYLOAD);
			try (ClientSession session = this.login(ticket)) {
				var scp = ScpClientCreator.instance().createScpClient(session);
				scp.upload(local, "backup.tgz");
				Assertions.assertThrows(IOException.class, () -> scp.download("backup.tgz", tempDir.resolve("back.tgz")));
			}
			Assertions.assertFalse(Files.exists(tempDir.resolve("back.tgz")));
			helper.release();
		}

		@Test
		@DisplayName("The device can only use the protocol of the ticket, the refusal is in the task log")
		void ticketProtocol() throws Exception {
			JsConfigHelper helper = helper(null, this.sshServer);
			Path local = Files.write(tempDir.resolve("local.tgz"), PAYLOAD);

			ProxyObject sftpTicket = helper.requestUpload("sftp", null);
			try (ClientSession session = this.login(sftpTicket)) {
				var scp = ScpClientCreator.instance().createScpClient(session);
				Assertions.assertThrows(IOException.class, () -> scp.upload(local, "backup.tgz"));
			}
			ProxyObject scpTicket = helper.requestUpload("scp", null);
			try (ClientSession session = this.login(scpTicket)) {
				Assertions.assertThrows(IOException.class, () -> SftpClientFactory.instance().createSftpClient(session));
			}

			for (ProxyObject ticket : List.of(sftpTicket, scpTicket)) {
				ProxyObject result = helper.awaitUpload((Long) ticket.getMember("id"), 10000);
				Assertions.assertEquals(0L, ((Number) result.getMember("fileCount")).longValue(), "No file received");
			}
			String log = taskContext.getLog();
			Assertions.assertTrue(log.contains("Upload with SCP refused for ticket 1, which allows [SFTP] only."), log);
			Assertions.assertTrue(log.contains("Upload with SFTP refused for ticket 2, which allows [SCP] only."), log);
			helper.release();
		}

		@Test
		@DisplayName("A ticket allows the requested protocols the server runs, each disabled one is logged; none of them is an error")
		void disabledProtocol() throws Exception {
			this.sshServer.stop();
			Properties properties = TestConfig.properties();
			properties.setProperty("netshot.sshserver.scp.enabled", "false");
			properties.setProperty("netshot.sshserver.port", Integer.toString(TestNetwork.freePort()));
			TestConfig.init(properties);
			this.sshServer = new SshServer(TestConfig.store());
			this.sshServer.start();
			JsConfigHelper helper = helper(null, this.sshServer);

			IOException thrown = Assertions.assertThrows(IOException.class, () -> helper.requestUpload("scp", null));
			Assertions.assertEquals("None of the requested transfer protocols [SCP] is enabled on the SSH server.", thrown.getMessage());
			Assertions.assertTrue(taskContext.getLog().contains(
				"[WARN] Upload ticket requested with transfer protocol SCP, which is not enabled on the SSH server."), taskContext.getLog());

			// SCP and SFTP requested: SFTP only
			ProxyObject ticket = helper.requestUpload((String) null, null);
			Path local = Files.write(tempDir.resolve("local.tgz"), PAYLOAD);
			try (ClientSession session = this.login(ticket)) {
				var scp = ScpClientCreator.instance().createScpClient(session);
				Assertions.assertThrows(IOException.class, () -> scp.upload(local, "backup.tgz"));
			}
			try (ClientSession session = this.login(ticket);
				SftpClient sftp = SftpClientFactory.instance().createSftpClient(session);
				OutputStream out = sftp.write("upload.bin")) {
				out.write(PAYLOAD);
			}
			ProxyObject result = helper.awaitUpload((Long) ticket.getMember("id"), 10000);
			Assertions.assertEquals(1L, ((Number) result.getMember("fileCount")).longValue());
			helper.release();
		}

		@Test
		@DisplayName("A device from another address than the expected one can't log in")
		void wrongSourceAddress() throws Exception {
			JsConfigHelper helper = helper(null, this.sshServer);
			ProxyObject ticket = helper.requestUpload("scp", "10.9.9.9");

			Assertions.assertThrows(IOException.class, () -> this.login(ticket).close());
			helper.release();
		}

		@Test
		@DisplayName("Waiting fails after the timeout when the device doesn't upload")
		void awaitTimeout() throws Exception {
			JsConfigHelper helper = helper(null, this.sshServer);
			long ticketId = (Long) helper.requestUpload((String) null, null).getMember("id");

			IOException thrown = Assertions.assertThrows(IOException.class, () -> helper.awaitUpload(ticketId, 200));

			Assertions.assertTrue(thrown.getMessage().contains("Timeout"), thrown.getMessage());
			helper.release();
		}

		@Test
		@DisplayName("Unknown tickets, files or attributes are refused")
		void invalidRequests() throws Exception {
			JsConfigHelper helper = helper(null, this.sshServer);
			ProxyObject ticket = helper.requestUpload("scp", null);
			long ticketId = (Long) ticket.getMember("id");
			Path local = Files.write(tempDir.resolve("local.tgz"), PAYLOAD);
			try (ClientSession session = this.login(ticket)) {
				ScpClientCreator.instance().createScpClient(session).upload(local, "backup.tgz");
			}
			helper.awaitUpload(ticketId, 10000);

			Assertions.assertNull(helper.awaitUpload(ticketId + 1, 100), "Unknown ticket");
			Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper.commitUpload(ticketId + 1, 1, "backupArchive", null, null), "Unknown ticket");
			Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper.commitUpload(ticketId, 2, "backupArchive", null, null), "Unknown file");
			Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper.commitUpload(ticketId, 1, "unknown", null, null), "Unknown attribute");
			Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper.commitUpload(ticketId, 1, "version", null, null), "Not a binary file attribute");
			Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper.requestUpload("ftp", null), "Unsupported method");
			Assertions.assertThrows(IllegalArgumentException.class,
				() -> helper.requestUpload("scp", "not an address"), "Invalid source address");
			Assertions.assertNull(backupAttribute());
			helper.release();
		}
	}
}
