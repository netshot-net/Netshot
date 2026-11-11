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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.netshot.netshot.access.Http.HttpResult;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.driver.protocol.AuthScheme;
import net.netshot.netshot.driver.protocol.HttpConfig;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.ScriptEngineTest;
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
		return new Http("127.0.0.1", this.port, false, new FakeTaskContext(), SecretResolver.LOCAL);
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
		Http client = new Http("localhost", this.port, false, new FakeTaskContext(), SecretResolver.LOCAL);

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

}
