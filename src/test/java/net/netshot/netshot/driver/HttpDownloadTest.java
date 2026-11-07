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
package net.netshot.netshot.driver;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.access.AccessManager;
import net.netshot.netshot.access.Http;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.devicescript.SnapshotDeviceScript;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.DeviceDriver.Location;
import net.netshot.netshot.driver.DeviceDriver.LocationType;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeTaskContext;
import net.netshot.netshot.vault.SecretResolver;

/**
 * Tests for {@code Http.download()}/{@code http.download(...)}: the
 * binary-safe counterpart to {@code Http.request()}/{@code http.request(...)},
 * added because the latter always decodes the response body as a
 * (charset-decoded) {@link String} - lossy for arbitrary binary content
 * such as a backup archive (see {@link net.netshot.netshot.access.Http}
 * and {@link net.netshot.netshot.devicescript.helper.JsHttpHelper}).
 */
@ScriptEngineTest
@DisplayName("HTTP client binary download test")
public class HttpDownloadTest {

	private HttpServer fakeServer;
	private int port;

	/**
	 * All 256 possible byte values, including sequences that are not valid UTF-8 - proves byte-exact transfer.
	 * @return the 256 bytes
	 */
	private byte[] binaryPayload() {
		byte[] data = new byte[256];
		for (int i = 0; i < 256; i++) {
			data[i] = (byte) i;
		}
		return data;
	}

	private void serveBinary(String path, byte[] payload) {
		this.fakeServer.createContext(path, exchange -> {
			exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
			exchange.sendResponseHeaders(200, payload.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(payload);
			}
		});
	}

	@BeforeEach
	void setUp() throws IOException {
		// Binary file attributes (ConfigBinaryFileAttribute) need a storage
		// folder configured - point it at a fresh temporary directory for
		// each test rather than relying on the default (/var/local/netshot),
		// which won't exist on a test machine.
		Path storagePath = Files.createTempDirectory("netshot-test-binary-");
		Properties props = TestConfig.properties();
		props.setProperty("netshot.snapshots.binary.path", storagePath.toString());
		TestConfig.init(props);

		this.fakeServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.port = this.fakeServer.getAddress().getPort();
		this.fakeServer.start();
	}

	@AfterEach
	void tearDown() {
		this.fakeServer.stop(0);
	}

	@Test
	@DisplayName("Http.download() streams the exact response bytes to disk")
	void downloadIsByteExact() throws Exception {
		byte[] payload = this.binaryPayload();
		this.serveBinary("/binary.bin", payload);

		TaskContext taskContext = new FakeTaskContext();
		Http http = new Http("127.0.0.1", this.port, false, taskContext, SecretResolver.LOCAL);
		Path target = Files.createTempFile("netshot-test-download-", ".bin");
		try {
			Http.HttpDownloadResult result =
				http.download("GET", "/binary.bin", null, null, null, null, null, null, target);

			Assertions.assertEquals(200, result.getStatus());
			Assertions.assertEquals(payload.length, result.getSize());
			Assertions.assertArrayEquals(payload, Files.readAllBytes(target),
				"Downloaded bytes must exactly match the server response, including non-UTF-8 byte sequences");
		}
		finally {
			Files.deleteIfExists(target);
		}
	}

	@Test
	@DisplayName("Unlike download(), request()'s String-decoded body does not preserve arbitrary bytes")
	void requestBodyIsNotBinarySafe() throws Exception {
		// Documents *why* download() exists: bytes 0x80-0xFF are not valid
		// UTF-8 on their own, so decoding the response as a String (what
		// request() does) and re-encoding it loses information.
		byte[] payload = this.binaryPayload();
		this.serveBinary("/binary2.bin", payload);

		TaskContext taskContext = new FakeTaskContext();
		Http http = new Http("127.0.0.1", this.port, false, taskContext, SecretResolver.LOCAL);
		Http.HttpResult result = http.request("GET", "/binary2.bin", null, null, null, null, null, null);

		Assertions.assertEquals(200, result.getStatus());
		byte[] roundTripped = result.getBody().getBytes(java.nio.charset.StandardCharsets.UTF_8);
		Assertions.assertFalse(Arrays.equals(payload, roundTripped),
			"request()'s String round-trip is expected to corrupt arbitrary binary content");
	}

	@Test
	@DisplayName("Http.download() can carry an explicit Content-Type header on a bodyless GET")
	void downloadCanSendContentTypeHeaderWithoutABody() throws Exception {
		// Some servers (observed against a real Infoblox NIOS Grid, whose
		// database.bak download endpoint 415s a GET with no Content-Type
		// header) unusually insist on seeing one even without a request
		// body - this reproduces that behavior in miniature.
		byte[] payload = this.binaryPayload();
		final String[] receivedContentType = new String[1];
		this.fakeServer.createContext("/needs-content-type.bin", exchange -> {
			receivedContentType[0] = exchange.getRequestHeaders().getFirst("Content-Type");
			if (receivedContentType[0] == null) {
				exchange.sendResponseHeaders(415, -1);
				exchange.close();
				return;
			}
			exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
			exchange.sendResponseHeaders(200, payload.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(payload);
			}
		});

		TaskContext taskContext = new FakeTaskContext();
		Http http = new Http("127.0.0.1", this.port, false, taskContext, SecretResolver.LOCAL);
		Path target = Files.createTempFile("netshot-test-download-", ".bin");
		try {
			Map<String, String> headers = new HashMap<>();
			headers.put("Content-Type", "application/octet-stream");
			Http.HttpDownloadResult result =
				http.download("GET", "/needs-content-type.bin", headers, null, null, null, null, null, target);

			Assertions.assertEquals("application/octet-stream", receivedContentType[0],
				"The Content-Type header set on a bodyless GET must reach the server, not be silently dropped");
			Assertions.assertEquals(200, result.getStatus());
			Assertions.assertArrayEquals(payload, Files.readAllBytes(target));
		}
		finally {
			Files.deleteIfExists(target);
		}
	}

	@Test
	@DisplayName("End-to-end: driver downloads over HTTP and commits the result as a BinaryFile config attribute")
	void driverDownloadsAndCommitsBinaryFile() throws Exception {
		byte[] payload = this.binaryPayload();
		this.serveBinary("/backup.tar.gz", payload);

		String driverJs = """
			var Info = {
				name: "HttpDownloadTestDriver",
				author: "test",
				description: "Test driver for HTTP binary download",
				version: "1.0"
			};
			var Config = {
				"backupArchive": { type: "BinaryFile", title: "Backup" },
			};
			var Device = {};
			var HTTP = { http: {} };

			function snapshot(client, device, config) {
				var http = client.create("http");
				http.download("backupArchive", "/backup.tar.gz", { storeFileName: "backup.tar.gz" });
			}
			""";
		DeviceDriver driver = new DeviceDriver(new StringReader(driverJs), "HttpDownloadTestDriver.js",
			new Location(LocationType.EMBEDDED, "HttpDownloadTestDriver.js"));
		TestDrivers.registry().getDrivers().put("HttpDownloadTestDriver", driver);

		TaskContext taskContext = new FakeTaskContext();
		Http fakeHttp = new Http("127.0.0.1", this.port, false, taskContext, SecretResolver.LOCAL);
		DeviceHttpAccount credentials = DeviceHttpAccount.builder().username(Secret.local("user")).password(Secret.local("pass")).name("test-http-account").build();
		Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
		Device.DeviceBuilder deviceBuilder = Device.create("HttpDownloadTestDriver", null, domain, "test").toBuilder();
		DeviceDraft device = new DeviceDraft(deviceBuilder.build());
		SnapshotDeviceScript script = new SnapshotDeviceScript(TestDrivers.registry(), TestConnectors.local(), null, taskContext);
		AccessManager accessManager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, taskContext, null);
		accessManager.forceClientForTest(fakeHttp, credentials);
		Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", DeviceDraft.class, AccessManager.class);
		runMethod.setAccessible(true);
		runMethod.invoke(script, device, accessManager);

		Config config = device.getNewConfig();
		Assertions.assertNotNull(config, "The config doesn't exist");
		ConfigBinaryFileAttribute fileAttribute = (ConfigBinaryFileAttribute) config.getAttribute("backupArchive");
		Assertions.assertNotNull(fileAttribute, "The backupArchive attribute wasn't set");
		Assertions.assertEquals(payload.length, fileAttribute.fileSize(), "The stored file size is incorrect");
		Assertions.assertArrayEquals(payload, Files.readAllBytes(fileAttribute.getPendingFilePath(device.getId())),
			"The stored file content doesn't match the server response");
		Assertions.assertEquals(
			".tmp.%d_cfg0_backupArchive_%s.data".formatted(device.getId(), fileAttribute.uid()),
			fileAttribute.getPendingFilePath(device.getId()).getFileName().toString(),
			"The pending file name should embed the device id, cfg0 placeholder, attribute name and uid");
	}

	@Test
	@DisplayName("client.create(...) 'http.auth' advanced option overrides the driver's declared auth scheme")
	void clientCreateAuthOverride() throws Exception {
		byte[] payload = this.binaryPayload();
		this.fakeServer.createContext("/protected.bin", exchange -> {
			String apiKey = exchange.getRequestHeaders().getFirst("X-Api-Key");
			if (!"pass".equals(apiKey)) {
				exchange.sendResponseHeaders(401, -1);
				exchange.close();
				return;
			}
			exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
			exchange.sendResponseHeaders(200, payload.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(payload);
			}
		});

		// The driver declares no auth at all for its HTTP access: without the client.create(...)
		// override below, every request would come back 401 and the download would fail.
		String driverJs = """
			var Info = {
				name: "HttpAuthOverrideTestDriver",
				author: "test",
				description: "Test driver for the client.create(...) 'http.auth' advanced option",
				version: "1.0"
			};
			var Config = {
				"backupArchive": { type: "BinaryFile", title: "Backup" },
			};
			var Device = {};
			var HTTP = { http: {} };

			function snapshot(client, device, config) {
				var http = client.create("http", {
					http: { auth: { type: "apiKey", in: "header", name: "X-Api-Key" } },
				});
				http.download("backupArchive", "/protected.bin", { storeFileName: "backup.tar.gz" });
			}
			""";
		DeviceDriver driver = new DeviceDriver(new StringReader(driverJs), "HttpAuthOverrideTestDriver.js",
			new Location(LocationType.EMBEDDED, "HttpAuthOverrideTestDriver.js"));
		TestDrivers.registry().getDrivers().put("HttpAuthOverrideTestDriver", driver);

		TaskContext taskContext = new FakeTaskContext();
		// The apiKey value sent is the credential set's password ("pass") - see Http.applyAuth().
		DeviceHttpAccount credentials = DeviceHttpAccount.builder().username(Secret.local("user")).password(Secret.local("pass")).name("test-http-account").build();
		Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
		Device.DeviceBuilder deviceBuilder = Device.create("HttpAuthOverrideTestDriver", null, domain, "test").toBuilder();
		DeviceAccess httpAccess = DeviceAccess.of("http").toBuilder().address("127.0.0.1").port(this.port).build();
		DeviceDraft device = new DeviceDraft(deviceBuilder.accesses(List.of(httpAccess)).build());
		SnapshotDeviceScript script = new SnapshotDeviceScript(TestDrivers.registry(), TestConnectors.local(), null, taskContext);
		AccessManager accessManager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, taskContext, Set.of(credentials));
		Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", DeviceDraft.class, AccessManager.class);
		runMethod.setAccessible(true);
		try {
			runMethod.invoke(script, device, accessManager);
		}
		finally {
			accessManager.disconnectAll();
		}

		Config config = device.getNewConfig();
		Assertions.assertNotNull(config,
			"The config doesn't exist - the client.create(...) 'http.auth' override probably didn't take effect");
		ConfigBinaryFileAttribute fileAttribute = (ConfigBinaryFileAttribute) config.getAttribute("backupArchive");
		Assertions.assertNotNull(fileAttribute, "The backupArchive attribute wasn't set");
		Assertions.assertEquals(payload.length, fileAttribute.fileSize(), "The stored file size is incorrect");
	}
}
