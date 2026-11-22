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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.access.Http.HttpResult;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.driver.protocol.AuthScheme;
import net.netshot.netshot.driver.protocol.HttpConfig;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestTls.Identity;
import net.netshot.netshot.testing.TestTls;
import net.netshot.netshot.testing.fake.FakeTaskContext;
import net.netshot.netshot.vault.SecretResolver;

/**
 * Unit tests for {@link Http}, in particular the per-access-declared
 * authentication scheme injection (basic / bearer / apiKey), against a real
 * (plain HTTP, loopback) {@code com.sun.net.httpserver.HttpServer} - no mock
 * framework needed, and no TLS involved since {@code tls=false} is used
 * throughout (trust/certificate behavior is out of scope for Phase 1 tests).
 */
@ScriptEngineTest
@DisplayName("HTTP access (authentication schemes)")
class HttpTest {

	private HttpServer server;
	private int port;
	private final AtomicReference<HttpExchange> lastExchange = new AtomicReference<>();
	private final AtomicReference<String> lastBody = new AtomicReference<>();

	@BeforeEach
	void startServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", exchange -> {
			this.lastExchange.set(exchange);
			byte[] requestBody = exchange.getRequestBody().readAllBytes();
			this.lastBody.set(new String(requestBody, StandardCharsets.UTF_8));
			byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, response.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(response);
			}
		});
		this.server.start();
		this.port = this.server.getAddress().getPort();
	}

	@AfterEach
	void stopServer() {
		this.server.stop(0);
	}

	private Http newClient() throws IOException {
		return new Http("127.0.0.1", this.port, false, Http.Settings.from(TestConfig.store().current()), new FakeTaskContext(), SecretResolver.LOCAL);
	}

	@Test
	@DisplayName("Basic auth injects a Base64-encoded Authorization header")
	void basicAuthHeader() throws IOException {
		HttpConfig config = new HttpConfig();
		AuthScheme auth = new AuthScheme();
		auth.setType("http");
		auth.setScheme("basic");
		config.setAuth(auth);
		DeviceHttpAccount account = DeviceHttpAccount.builder().username(Secret.local("bob")).password(Secret.local("s3cret")).name("cred").build();

		HttpResult result = newClient().request("GET", "/status", null, null, null, null, config, account);

		Assertions.assertEquals(200, result.getStatus());
		String expected = "Basic " + Base64.getEncoder().encodeToString("bob:s3cret".getBytes(StandardCharsets.UTF_8));
		Assertions.assertEquals(expected, this.lastExchange.get().getRequestHeaders().getFirst("Authorization"));
	}

	@Test
	@DisplayName("Bearer auth injects the token as an Authorization header")
	void bearerAuthHeader() throws IOException {
		HttpConfig config = new HttpConfig();
		AuthScheme auth = new AuthScheme();
		auth.setType("http");
		auth.setScheme("bearer");
		config.setAuth(auth);
		DeviceHttpAccount account = DeviceHttpAccount.builder().username(null).password(Secret.local("tok3n")).name("cred").build();

		HttpResult result = newClient().request("GET", "/status", null, null, null, null, config, account);

		Assertions.assertEquals(200, result.getStatus());
		Assertions.assertEquals("Bearer tok3n", this.lastExchange.get().getRequestHeaders().getFirst("Authorization"));
	}

	@Test
	@DisplayName("apiKey (header) auth injects the configured header name")
	void apiKeyHeader() throws IOException {
		HttpConfig config = new HttpConfig();
		AuthScheme auth = new AuthScheme();
		auth.setType("apiKey");
		auth.setIn("header");
		auth.setName("X-API-Key");
		config.setAuth(auth);
		DeviceHttpAccount account = DeviceHttpAccount.builder().username(null).password(Secret.local("the-key")).name("cred").build();

		HttpResult result = newClient().request("GET", "/status", null, null, null, null, config, account);

		Assertions.assertEquals(200, result.getStatus());
		Assertions.assertEquals("the-key", this.lastExchange.get().getRequestHeaders().getFirst("X-API-Key"));
	}

	@Test
	@DisplayName("apiKey (query) auth injects the configured query parameter")
	void apiKeyQuery() throws IOException {
		HttpConfig config = new HttpConfig();
		AuthScheme auth = new AuthScheme();
		auth.setType("apiKey");
		auth.setIn("query");
		auth.setName("api_key");
		config.setAuth(auth);
		DeviceHttpAccount account = DeviceHttpAccount.builder().username(null).password(Secret.local("the-key")).name("cred").build();

		HttpResult result = newClient().request("GET", "/status", null, null, null, null, config, account);

		Assertions.assertEquals(200, result.getStatus());
		Assertions.assertEquals("/status?api_key=the-key", this.lastExchange.get().getRequestURI().toString());
	}

	@Test
	@DisplayName("the configured host (FQDN or IP) is used as the request's Host, resolved by the HTTP client itself")
	void configuredHostIsUsedAsRequestHost() throws IOException {
		Http client = new Http("localhost", this.port, false, Http.Settings.from(TestConfig.store().current()), new FakeTaskContext(), SecretResolver.LOCAL);

		HttpResult result = client.request("GET", "/status", null, null, null, null, null, null);

		Assertions.assertEquals(200, result.getStatus());
		Assertions.assertEquals("localhost:" + this.port, this.lastExchange.get().getRequestHeaders().getFirst("Host"));
	}

	@Test
	@DisplayName("cookie auth logs in once (POST to auth.path) and replays the session cookie on later requests")
	void cookieAuthLoginAndReplay() throws IOException {
		AtomicInteger loginCalls = new AtomicInteger(0);
		AtomicReference<String> loginBody = new AtomicReference<>();
		this.server.createContext("/login", exchange -> {
			loginCalls.incrementAndGet();
			byte[] requestBody = exchange.getRequestBody().readAllBytes();
			loginBody.set(new String(requestBody, StandardCharsets.UTF_8));
			exchange.getResponseHeaders().add("Set-Cookie", "SESSION=abc123; Path=/");
			byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, response.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(response);
			}
		});

		HttpConfig config = new HttpConfig();
		AuthScheme auth = new AuthScheme();
		auth.setType("cookie");
		auth.setMethod("POST");
		auth.setPath("/login");
		auth.setContentType("json");
		auth.setData(Map.of(
			"userName", "$$NetshotUsername$$",
			"userPasswd", "$$NetshotPassword$$"
		));
		config.setAuth(auth);
		DeviceHttpAccount account = DeviceHttpAccount.builder().username(Secret.local("bob")).password(Secret.local("s3cret")).name("cred").build();
		Http client = newClient();

		HttpResult first = client.request("GET", "/status", null, null, null, null, config, account);
		Assertions.assertEquals(200, first.getStatus());
		Assertions.assertEquals(1, loginCalls.get());
		Assertions.assertTrue(loginBody.get().contains("\"userName\":\"bob\""));
		Assertions.assertTrue(loginBody.get().contains("\"userPasswd\":\"s3cret\""));
		Assertions.assertEquals("$Version=1;SESSION=abc123", this.lastExchange.get().getRequestHeaders().getFirst("Cookie"));

		HttpResult second = client.request("GET", "/other", null, null, null, null, config, account);
		Assertions.assertEquals(200, second.getStatus());
		Assertions.assertEquals(1, loginCalls.get(), "login should only happen once; the cookie must be replayed");
		Assertions.assertEquals("$Version=1;SESSION=abc123", this.lastExchange.get().getRequestHeaders().getFirst("Cookie"));
	}

	/**
	 * Serves a cookie login endpoint, which answers with the given status (and a session cookie on success).
	 *
	 * @param status the HTTP status of the login answers
	 * @param calls the number of login calls, incremented
	 * @param request the last login request, as "content type / body"
	 */
	private void loginEndpoint(int status, AtomicInteger calls, AtomicReference<String> request) {
		this.server.createContext("/login", exchange -> {
			calls.incrementAndGet();
			request.set(exchange.getRequestHeaders().getFirst("Content-Type") + " / "
				+ new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			if (status == 200) {
				exchange.getResponseHeaders().add("Set-Cookie", "SESSION=abc123; Path=/");
			}
			exchange.sendResponseHeaders(status, -1);
			exchange.close();
		});
	}

	private static HttpConfig cookieAuth(String contentType, Map<String, Object> data) {
		AuthScheme auth = new AuthScheme();
		auth.setType("cookie");
		auth.setPath("/login");
		auth.setContentType(contentType);
		auth.setData(data);
		HttpConfig config = new HttpConfig();
		config.setAuth(auth);
		return config;
	}

	private static final DeviceHttpAccount BOB = DeviceHttpAccount.builder().username(Secret.local("bob")).password(Secret.local("s3&cret"))
		.name("cred").build();

	@Test
	@DisplayName("cookie auth with a form body sends the credentials form-encoded (POST by default)")
	void cookieAuthForm() throws IOException {
		AtomicInteger calls = new AtomicInteger();
		AtomicReference<String> login = new AtomicReference<>();
		this.loginEndpoint(200, calls, login);
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("user", "$$NetshotUsername$$");
		data.put("pass", "$$NetshotPassword$$");

		HttpResult result = newClient().request("GET", "/status", null, null, null, null, cookieAuth("form", data), BOB);

		Assertions.assertEquals(200, result.getStatus());
		Assertions.assertTrue(login.get().startsWith("application/x-www-form-urlencoded"), login.get());
		Assertions.assertTrue(login.get().contains("user=bob"), login.get());
		Assertions.assertTrue(login.get().contains("pass=s3%26cret"), login.get());
	}

	@Test
	@DisplayName("cookie auth replaces the placeholders anywhere in a JSON body, in lists and nested objects too")
	void cookieAuthNestedPlaceholders() throws IOException {
		AtomicInteger calls = new AtomicInteger();
		AtomicReference<String> login = new AtomicReference<>();
		this.loginEndpoint(200, calls, login);
		Map<String, Object> data = Map.of(
			"identity", List.of("$$NetshotUsername$$", Map.of("secret", "$$NetshotPassword$$")),
			"remember", true);

		newClient().request("GET", "/status", null, null, null, null, cookieAuth("json", data), BOB);

		Assertions.assertTrue(login.get().startsWith("application/json"), login.get());
		Assertions.assertTrue(login.get().contains("\"identity\":[\"bob\",{\"secret\":\"s3&cret\"}]"), login.get());
		Assertions.assertTrue(login.get().contains("\"remember\":true"), login.get());
	}

	@Test
	@DisplayName("A refused cookie login fails the request, and the next request logs in again")
	void cookieAuthRefused() throws IOException {
		AtomicInteger calls = new AtomicInteger();
		this.loginEndpoint(401, calls, new AtomicReference<>());
		Http client = newClient();
		HttpConfig config = cookieAuth("json", Map.of("user", "$$NetshotUsername$$"));

		IOException error = Assertions.assertThrows(IOException.class,
			() -> client.request("GET", "/status", null, null, null, null, config, BOB));
		Assertions.assertEquals("Cookie-auth login failed (HTTP status 401).", error.getMessage());
		Assertions.assertThrows(IOException.class, () -> client.request("GET", "/status", null, null, null, null, config, BOB));
		Assertions.assertEquals(2, calls.get(), "Not logged in again");
	}

	@Test
	@DisplayName("apiKey auth with wrong-case type is ignored (exact-case match required)")
	void wrongCaseAuthTypeIsIgnored() throws IOException {
		HttpConfig config = new HttpConfig();
		AuthScheme auth = new AuthScheme();
		auth.setType("APIKEY");
		auth.setIn("header");
		auth.setName("X-API-Key");
		config.setAuth(auth);
		DeviceHttpAccount account = DeviceHttpAccount.builder().username(null).password(Secret.local("the-key")).name("cred").build();

		HttpResult result = newClient().request("GET", "/status", null, null, null, null, config, account);

		Assertions.assertEquals(200, result.getStatus());
		Assertions.assertNull(this.lastExchange.get().getRequestHeaders().getFirst("X-API-Key"));
	}

	@Test
	@DisplayName("Custom headers/body pass through untouched, and the base path is prepended")
	void customHeadersAndBasePath() throws IOException {
		HttpConfig config = new HttpConfig();
		config.setBasePath("/api/v1");

		HttpResult result = newClient().request("POST", "/things", Map.of("X-Custom", "value"), null, null,
			"{\"name\":\"test\"}", config, null);

		Assertions.assertEquals(200, result.getStatus());
		Assertions.assertEquals("/api/v1/things", this.lastExchange.get().getRequestURI().toString());
		Assertions.assertEquals("value", this.lastExchange.get().getRequestHeaders().getFirst("X-Custom"));
		Assertions.assertEquals("{\"name\":\"test\"}", this.lastBody.get());
	}


	@Test
	@DisplayName("HTTPS: the CA trust policy of the access applies (system trust store by default, custom CA)")
	void httpsTrustPolicy() throws IOException {
		Identity authority = TestTls.authority("Device CA");
		try (TestTls.Server server = new TestTls.Server(TestTls.server(authority, "localhost"), "{\"status\": \"ok\"}")) {
			Http byDefault = new Http("localhost", server.port(), true, Http.Settings.from(TestConfig.store().current()), new FakeTaskContext(), SecretResolver.LOCAL);
			IOException e = Assertions.assertThrows(IOException.class,
				() -> byDefault.request("GET", "/status", null, null, null, null, new HttpConfig(), null));
			Assertions.assertNotNull(findSslException(e), "Not refused by TLS: " + e);

			Http customCa = new Http("localhost", server.port(), true, Http.Settings.from(TestConfig.store().current()), new FakeTaskContext(), SecretResolver.LOCAL);
			customCa.applyTrustPolicy(HttpsCaTrustMode.CUSTOM_CA, authority.pem());
			HttpResult result = customCa.request("GET", "/status", null, null, null, null, new HttpConfig(), null);
			Assertions.assertEquals(200, result.getStatus());
			Assertions.assertEquals("{\"status\": \"ok\"}", result.getBody());
		}
	}

	@Test
	@DisplayName("A device which accepts the connection but never answers makes the request fail after the receive timeout")
	void receiveTimeout() throws IOException {
		Properties original = TestConfig.properties();
		Properties config = TestConfig.properties();
		config.setProperty("netshot.httpclient.receivetimeout", "500");
		TestConfig.init(config);
		// The kernel completes the TCP handshake (backlog) but nothing ever reads or answers
		try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			Http http = new Http("127.0.0.1", silent.getLocalPort(), false, Http.Settings.from(TestConfig.store().current()), new FakeTaskContext(), SecretResolver.LOCAL);
			IOException e = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(10),
				() -> Assertions.assertThrows(IOException.class,
					() -> http.request("GET", "/status", null, null, null, null, new HttpConfig(), null)));
			Assertions.assertNotNull(findCause(e, SocketTimeoutException.class), "Not a read timeout: " + e);
		}
		finally {
			TestConfig.init(original);
		}
	}

	private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (type.isInstance(cause)) {
				return type.cast(cause);
			}
		}
		return null;
	}

	private static SSLException findSslException(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SSLException ssl) {
				return ssl;
			}
		}
		return null;
	}
}
