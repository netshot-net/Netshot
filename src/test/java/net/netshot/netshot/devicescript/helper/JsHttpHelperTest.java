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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.netshot.netshot.access.AccessManager;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.devicescript.SnapshotDeviceScript;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DeviceDriver.Location;
import net.netshot.netshot.driver.DeviceDriver.LocationType;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.BinaryFileStorage;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
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
 * Tests the HTTP client of the drivers ({@code client.create("http")}, {@link JsHttpHelper}): the requests as
 * the driver builds them, the authentication with the credential sets (fallback on the first request only, manual
 * mode), the status validation and the trace log, against a local HTTP server playing the device.
 */
@ScriptEngineTest
@DisplayName("HTTP client of the drivers")
class JsHttpHelperTest {

	/** The password the device accepts. */
	private static final String GOOD_PASSWORD = "Good-Password-1";

	/**
	 * A received request.
	 *
	 * @param method the HTTP method
	 * @param path the path
	 * @param query the query string
	 * @param headers the headers
	 * @param body the body
	 */
	private record Received(String method, String path, String query, Map<String, List<String>> headers, String body) {
	}

	private HttpServer server;

	private final List<Received> received = new CopyOnWriteArrayList<>();

	private FakeTaskContext taskContext;

	@BeforeEach
	void startServer() throws IOException {
		Properties properties = new Properties();
		properties.setProperty("netshot.snapshots.binary.path", Files.createTempDirectory("netshot-test-binary-").toString());
		TestConfig.init(properties);
		this.taskContext = new FakeTaskContext();
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", this::handle);
		this.server.start();
	}

	@AfterEach
	void stopServer() {
		this.server.stop(0);
		TestConfig.init(new Properties());
	}

	private static String basic(String password) {
		return "Basic " + Base64.getEncoder().encodeToString(("netshot:" + password).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * The device: 401 without the good Basic credentials, else answers by path.
	 *
	 * @param exchange the HTTP exchange
	 * @throws IOException if the response can't be written
	 */
	private void handle(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		String path = exchange.getRequestURI().getPath();
		this.received.add(new Received(exchange.getRequestMethod(), path, exchange.getRequestURI().getQuery(),
			Map.copyOf(exchange.getRequestHeaders()), body));
		int status;
		String response;
		if (!basic(GOOD_PASSWORD).equals(exchange.getRequestHeaders().getFirst("Authorization")) || path.equals("/api/forbidden")) {
			status = 401;
			response = "{\"error\":\"unauthorized\"}";
		}
		else if (path.equals("/api/info")) {
			status = 200;
			response = "{\"version\":\"7.4.1\"}";
		}
		else if (path.equals("/api/items") && "POST".equals(exchange.getRequestMethod())) {
			status = 201;
			response = "created " + body;
		}
		else {
			status = 404;
			response = "not found";
		}
		byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}

	private static DeviceHttpAccount account(String name, String password) {
		return DeviceHttpAccount.builder().name(name).username(Secret.local("netshot")).password(Secret.local(password)).build();
	}

	/**
	 * Registers a driver with the given snapshot function body, an HTTP access with Basic authentication, and
	 * text configuration attributes.
	 *
	 * @param name the driver name
	 * @param snapshotBody the body of the snapshot function
	 * @throws Exception if the driver can't be loaded
	 */
	private static void register(String name, String snapshotBody) throws Exception {
		String driverJs = """
			var Info = { name: "%s", author: "test", description: "Test driver of the HTTP client", version: "1.0" };
			var Config = {
				"version": { type: "Text", title: "Version" },
				"report": { type: "Text", title: "Report" },
			};
			var Device = {};
			var HTTP = { http: { auth: { type: "http", scheme: "basic" } } };
			function snapshot(client, device, config) {
			%s
			}
			""".formatted(name, snapshotBody);
		TestDrivers.registry().getDrivers().put(name,
			new DeviceDriver(new StringReader(driverJs), name + ".js", new Location(LocationType.EMBEDDED, name + ".js")));
	}

	/**
	 * Takes a snapshot of a device of the given driver, trying the given credential sets in order.
	 *
	 * @param driverName the driver of the device
	 * @param credentialSets the credential sets, in order
	 * @return the new configuration
	 * @throws Exception if the snapshot fails
	 */
	private Config snapshot(String driverName, DeviceCredentialSet... credentialSets) throws Exception {
		Domain domain = Domain.builder().name("Lab").description("Lab domain").build();
		DeviceAccess access = DeviceAccess.of("http").toBuilder().address("127.0.0.1").port(this.server.getAddress().getPort()).build();
		DeviceDraft device = new DeviceDraft(Device.create(driverName, null, domain, "test").toBuilder().accesses(List.of(access)).build());
		Set<DeviceCredentialSet> ordered = new LinkedHashSet<>(List.of(credentialSets));
		AccessManager accessManager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, this.taskContext, ordered);
		SnapshotDeviceScript script = new SnapshotDeviceScript(TestDrivers.registry(), TestConnectors.local(), null, this.taskContext, null,
			new BinaryFileStorage(TestConfig.store()));
		Method run = SnapshotDeviceScript.class.getDeclaredMethod("run", DeviceDraft.class, AccessManager.class);
		run.setAccessible(true);
		try {
			run.invoke(script, device, accessManager);
		}
		catch (InvocationTargetException e) {
			if (e.getCause() instanceof Exception cause) {
				throw cause;
			}
			throw e;
		}
		finally {
			accessManager.disconnectAll();
		}
		return device.getNewConfig();
	}

	private static String text(Config config, String name) {
		ConfigAttribute attribute = config.getAttribute(name);
		return attribute == null ? null : attribute.getAsText();
	}

	@Test
	@DisplayName("Sends the requests as the driver builds them, with the credentials, and returns the responses")
	void requests() throws Exception {
		register("HttpRequestsDriver", """
			var http = client.create("http");
			var info = http.get("/api/info", { params: { depth: "2" }, headers: { "X-Test": "yes" }, cookies: { lang: "en" } });
			config.set("version", info.json().version);
			var created = http.post("/api/items", { name: "item1" });
			var report = [created.status, created.data];
			try {
				http.get("/api/missing");
				report.push("no error");
			}
			catch (e) {
				report.push(e.response.status);
			}
			var any = function() { return true; };
			report.push(http.get("/api/missing", { validateStatus: any }).status);
			report.push(http.put("/api/items", "raw", { validateStatus: any }).status);
			report.push(http.request({ method: "delete", url: "/api/items", validateStatus: any }).status);
			var api = client.create("http", { http: { basePath: "/api" } });
			report.push(api.get("info").json().version);
			http.sleep(1);
			config.set("report", report.join("|"));
			""");
		Config config = this.snapshot("HttpRequestsDriver", account("good", GOOD_PASSWORD));

		Assertions.assertEquals("7.4.1", text(config, "version"));
		Assertions.assertEquals("201|created {\"name\":\"item1\"}|404|404|404|404|7.4.1", text(config, "report"));

		Received info = this.received.get(0);
		Assertions.assertEquals("GET", info.method());
		Assertions.assertEquals("depth=2", info.query());
		Assertions.assertEquals(List.of("yes"), info.headers().get("X-test"));
		Assertions.assertTrue(info.headers().get("Cookie").get(0).contains("lang=en"), info.headers().toString());
		Assertions.assertEquals(List.of(basic(GOOD_PASSWORD)), info.headers().get("Authorization"));
		Received post = this.received.get(1);
		Assertions.assertEquals("POST", post.method());
		Assertions.assertEquals("{\"name\":\"item1\"}", post.body());
		Assertions.assertEquals(List.of("application/json"), post.headers().get("Content-type"));
		Assertions.assertEquals("PUT", this.received.get(4).method());
		Assertions.assertEquals("raw", this.received.get(4).body());
		Assertions.assertEquals("DELETE", this.received.get(5).method());
		Assertions.assertEquals("/api/info", this.received.get(6).path());
	}

	@Test
	@DisplayName("Traces the requests and responses without the credentials")
	void traceWithoutSecrets() throws Exception {
		register("HttpTraceDriver", """
			var http = client.create("http");
			config.set("version", http.post("/api/items", "payload").data);
			""");
		this.snapshot("HttpTraceDriver", account("good", GOOD_PASSWORD));

		String log = this.taskContext.getLog();
		Assertions.assertTrue(log.contains("About to send the following HTTP request (before authentication is applied)"), log);
		Assertions.assertTrue(log.contains("POST /items") || log.contains("POST /api/items"), log);
		Assertions.assertTrue(log.contains("Status: 201"), log);
		Assertions.assertFalse(log.contains(GOOD_PASSWORD), "The password is in the log");
		Assertions.assertFalse(log.contains(basic(GOOD_PASSWORD).substring(6)), "The Authorization value is in the log");
	}

	@Test
	@DisplayName("Tries the next credential set when the first request is refused, but not for the later requests")
	void credentialFallback() throws Exception {
		register("HttpFallbackDriver", """
			var http = client.create("http");
			config.set("version", http.get("/api/info").json().version);
			config.set("report", String(http.get("/api/forbidden", { validateStatus: function() { return true; } }).status));
			""");
		Config config = this.snapshot("HttpFallbackDriver", account("wrong", "Wrong-Password"), account("good", GOOD_PASSWORD));

		Assertions.assertEquals("7.4.1", text(config, "version"));
		Assertions.assertEquals("401", text(config, "report"), "A later refusal is returned to the driver");
		Assertions.assertEquals(List.of(basic("Wrong-Password"), basic(GOOD_PASSWORD), basic(GOOD_PASSWORD)),
			this.received.stream().map(r -> r.headers().get("Authorization").get(0)).toList());
	}

	@Test
	@DisplayName("Fails when no credential set is accepted")
	void noValidCredentials() throws Exception {
		register("HttpNoCredentialDriver", """
			var http = client.create("http");
			config.set("version", http.get("/api/info").json().version);
			""");
		Exception e = Assertions.assertThrows(Exception.class,
			() -> this.snapshot("HttpNoCredentialDriver", account("wrong1", "Wrong-1"), account("wrong2", "Wrong-2")));
		Assertions.assertTrue(e.getMessage().contains("Couldn't find valid HTTP credentials (last status 401)"), e.getMessage());
		Assertions.assertEquals(2, this.received.size());
	}

	@Test
	@DisplayName("Without autoTryCredentials, reports the refusal and lets the driver try the next credential set")
	void manualCredentials() throws Exception {
		register("HttpManualDriver", """
			var http = client.create("http", { autoTryCredentials: false });
			var report = [];
			try {
				http.get("/api/info");
				report.push("no error");
			}
			catch (e) {
				report.push(String(e).indexOf("Authentication failed for this access (HTTP status 401)") >= 0);
			}
			report.push(http.tryNextCredentials());
			report.push(http.get("/api/info").json().version);
			report.push(http.tryNextCredentials());
			config.set("report", report.join("|"));
			""");
		Config config = this.snapshot("HttpManualDriver", account("wrong", "Wrong-Password"), account("good", GOOD_PASSWORD));

		Assertions.assertEquals("true|true|7.4.1|false", text(config, "report"));
	}
}
