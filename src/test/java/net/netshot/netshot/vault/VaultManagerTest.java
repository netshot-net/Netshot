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
package net.netshot.netshot.vault;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;

/**
 * The Vault client against a fake Vault server (and OAuth2 identity provider for the JWT login): logins, reads,
 * caches, the sharing of concurrent requests, and the errors.
 */
@UnitTest
@DisplayName("Vault client")
class VaultManagerTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private HttpServer server;

	/** The tokens the fake Vault accepts. */
	private final Set<String> validTokens = ConcurrentHashMap.newKeySet();

	private final AtomicInteger tokenCounter = new AtomicInteger();

	/** The requests received, as "METHOD path". */
	private final List<String> requests = new CopyOnWriteArrayList<>();

	/** The namespaces received with the requests. */
	private final List<String> namespaces = new CopyOnWriteArrayList<>();

	/** How long the fake Vault takes to answer a read (ms). */
	private volatile int readDelay;

	@BeforeEach
	void start() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.setExecutor(Executors.newCachedThreadPool());
		this.server.createContext("/", this::handle);
		this.server.start();
		this.configure("60000");
	}

	@AfterEach
	void stop() {
		this.server.stop(0);
		TestConfig.init(new Properties());
	}

	private void configure(String secretCacheTtl) {
		Properties config = new Properties();
		config.setProperty("netshot.vault.secret.cachettlms", secretCacheTtl);
		TestConfig.init(config);
	}

	private String baseUrl() {
		return "http://127.0.0.1:%d/".formatted(this.server.getAddress().getPort());
	}

	private static void reply(HttpExchange exchange, int status, Object body) throws IOException {
		byte[] data = JSON.writeValueAsBytes(body);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, data.length);
		exchange.getResponseBody().write(data);
		exchange.close();
	}

	private String newToken() {
		String token = "token-" + this.tokenCounter.incrementAndGet();
		this.validTokens.add(token);
		return token;
	}

	private void handle(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		this.requests.add(exchange.getRequestMethod() + " " + path);
		String namespace = exchange.getRequestHeaders().getFirst("X-Vault-Namespace");
		if (namespace != null) {
			this.namespaces.add(namespace);
		}
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		switch (path) {
			case "/idp/token" -> {
				String basic = "Basic " + Base64.getEncoder().encodeToString("netshot:client-secret".getBytes(StandardCharsets.UTF_8));
				if (basic.equals(exchange.getRequestHeaders().getFirst("Authorization")) && body.contains("grant_type=client_credentials")) {
					reply(exchange, 200, Map.of("access_token", "idp-jwt", "token_type", "Bearer", "expires_in", 300));
				}
				else {
					reply(exchange, 401, Map.of("error", "invalid_client", "error_description", "Unknown client"));
				}
			}
			case "/v1/auth/approle/login" -> {
				JsonNode login = JSON.readTree(body);
				if ("notoken".equals(login.path("role_id").asText())) {
					reply(exchange, 200, Map.of("auth", Map.of()));
				}
				else if ("good-secret".equals(login.path("secret_id").asText())) {
					reply(exchange, 200, Map.of("auth", Map.of("client_token", this.newToken(), "lease_duration", 3600)));
				}
				else {
					reply(exchange, 400, Map.of("errors", List.of("invalid role or secret ID")));
				}
			}
			case "/v1/auth/jwt/login" -> {
				JsonNode login = JSON.readTree(body);
				if ("idp-jwt".equals(login.path("jwt").asText()) && "netshot-role".equals(login.path("role").asText())) {
					reply(exchange, 200, Map.of("auth", Map.of("client_token", this.newToken(), "lease_duration", 3600)));
				}
				else {
					reply(exchange, 400, Map.of("errors", List.of("invalid JWT")));
				}
			}
			default -> {
				if (!path.startsWith("/v1/kv/data/")) {
					reply(exchange, 404, Map.of("errors", List.of()));
					return;
				}
				try {
					Thread.sleep(this.readDelay);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				if (!this.validTokens.contains(exchange.getRequestHeaders().getFirst("X-Vault-Token"))) {
					reply(exchange, 403, Map.of("errors", List.of("permission denied")));
				}
				else if (path.equals("/v1/kv/data/lab/device1")) {
					reply(exchange, 200, Map.of("data", Map.of("data", Map.of("username", "admin", "password", "Secret1",
						"snmp", Map.of("community", "public")))));
				}
				else {
					reply(exchange, 404, Map.of("errors", List.of()));
				}
			}
		}
	}

	/**
	 * Builds an AppRole instance (ID 1) of the fake Vault, with the KV engine mounted on "/kv/" and a namespace.
	 *
	 * @param secretId the AppRole secret ID
	 * @return the instance
	 */
	private HashicorpVaultKv2Instance appRole(String secretId) {
		return HashicorpVaultKv2Instance.builder("lab-vault").id(1).baseUrl(this.baseUrl()).namespace("netshot")
			.kvMountPath("/kv/").authMethod(AuthMethod.APPROLE).appRoleId("netshot-role-id")
			.appRoleSecretId(Secret.local(secretId)).build();
	}

	/**
	 * Builds a JWT instance (ID 2) of the fake Vault.
	 *
	 * @param clientSecret the secret of the client at the identity provider
	 * @return the instance
	 */
	private HashicorpVaultKv2Instance jwt(String clientSecret) {
		return HashicorpVaultKv2Instance.builder("lab-vault-jwt").id(2).baseUrl(this.baseUrl()).kvMountPath("kv")
			.authMethod(AuthMethod.JWT).jwtIdpTokenEndpoint(this.baseUrl() + "idp/token").jwtClientId("netshot")
			.jwtClientSecret(Secret.local(clientSecret)).jwtVaultRole("netshot-role").jwtScope("vault").build();
	}

	private static VaultManager manager(VaultInstance... instances) {
		return new VaultManager(id -> {
			for (VaultInstance instance : instances) {
				if (instance.id() == id) {
					return instance;
				}
			}
			return null;
		}, TestConfig.store());
	}

	private long count(String request) {
		return this.requests.stream().filter(request::equals).count();
	}

	@Test
	@DisplayName("A secret is read once, then from the cache; the namespace and the mount path are used")
	void readAndCache() throws Exception {
		VaultManager vault = manager(this.appRole("good-secret"));
		Assertions.assertEquals("admin", vault.resolve(Secret.vault(1, "lab/device1/username")));
		Assertions.assertEquals("Secret1", vault.resolve(Secret.vault(1, "lab/device1/password")));
		Assertions.assertEquals("{\"community\":\"public\"}", vault.resolve(Secret.vault(1, "lab/device1/snmp")), "An object as JSON");
		Assertions.assertEquals("local", vault.resolve(Secret.local("local")));
		Assertions.assertNull(vault.resolve(null));
		Map<String, Object> stats = vault.stats();
		Assertions.assertEquals(1L, stats.get("logins"));
		Assertions.assertEquals(1L, stats.get("reads"));
		Assertions.assertEquals(2L, stats.get("cacheHits"));
		Assertions.assertEquals(1, stats.get("cachedSecrets"));
		Assertions.assertEquals(1L, this.count("GET /v1/kv/data/lab/device1"));
		Assertions.assertTrue(this.namespaces.size() == this.requests.size() && this.namespaces.stream().allMatch("netshot"::equals),
			"The namespace is given with each request");

		vault.invalidate(1);
		Assertions.assertEquals("admin", vault.resolve(Secret.vault(1, "lab/device1/username")));
		Assertions.assertEquals(2L, vault.stats().get("logins"), "Invalidated: logged in again");
		Assertions.assertEquals(2L, this.count("GET /v1/kv/data/lab/device1"), "Invalidated: read again");
	}

	@Test
	@DisplayName("Concurrent resolutions of a secret share one login and one read")
	void concurrentResolutions() throws Exception {
		this.readDelay = 500;
		VaultManager vault = manager(this.appRole("good-secret"));
		ExecutorService threads = Executors.newFixedThreadPool(8);
		try {
			List<Future<String>> results = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				results.add(threads.submit(() -> vault.resolve(Secret.vault(1, "lab/device1/password"))));
			}
			for (Future<String> result : results) {
				Assertions.assertEquals("Secret1", result.get());
			}
		}
		finally {
			threads.shutdownNow();
		}
		Assertions.assertEquals(1L, this.count("POST /v1/auth/approle/login"));
		Assertions.assertEquals(1L, this.count("GET /v1/kv/data/lab/device1"));
	}

	@Test
	@DisplayName("A missing key or path fails, and isn't cached; a missing or unknown instance fails")
	void missing() throws Exception {
		VaultManager vault = manager(this.appRole("good-secret"));
		VaultException missingKey = Assertions.assertThrows(VaultException.class, () -> vault.resolve(Secret.vault(1, "lab/device1/enable")));
		Assertions.assertTrue(missingKey.getMessage().contains("Key 'enable' not found at Vault path 'lab/device1' on instance 'lab-vault'"),
			missingKey.getMessage());
		VaultException missingPath = Assertions.assertThrows(VaultException.class, () -> vault.resolve(Secret.vault(1, "lab/device2/password")));
		Assertions.assertTrue(missingPath.getMessage().contains("(HTTP 404)"), missingPath.getMessage());
		Assertions.assertThrows(VaultException.class, () -> vault.resolve(Secret.vault(1, "lab/device2/password")));
		Assertions.assertEquals(2L, vault.stats().get("readFailures"), "Failures aren't cached");
		Assertions.assertThrows(VaultException.class, () -> vault.resolve(Secret.vault(9, "lab/device1/password")), "Unknown instance");
		Assertions.assertThrows(VaultException.class, () -> vault.resolve(Secret.vault(1, "password")), "No key");
	}

	@Test
	@DisplayName("A refused login fails the resolution with the answer of Vault; the next resolution tries again")
	void refusedLogin() throws Exception {
		VaultManager vault = manager(this.appRole("bad-secret"));
		VaultException refused = Assertions.assertThrows(VaultException.class, () -> vault.resolve(Secret.vault(1, "lab/device1/password")));
		Assertions.assertTrue(refused.getMessage().contains("Vault login failed for instance 'lab-vault' (HTTP 400)")
			&& refused.getMessage().contains("invalid role or secret ID"), refused.getMessage());
		Assertions.assertThrows(VaultException.class, () -> vault.resolve(Secret.vault(1, "lab/device1/password")));
		Assertions.assertEquals(2L, vault.stats().get("loginFailures"));
		Assertions.assertEquals(0, vault.stats().get("cachedTokens"));

		VaultManager noToken = manager(this.appRole("good-secret").toBuilder().appRoleId("notoken").build());
		VaultException missingToken = Assertions.assertThrows(VaultException.class, () -> noToken.resolve(Secret.vault(1, "lab/device1/password")));
		Assertions.assertTrue(missingToken.getMessage().contains("didn't include a client token"), missingToken.getMessage());
	}

	@Test
	@DisplayName("A cached token which Vault doesn't accept anymore (revoked) is replaced by a new login")
	void revokedToken() throws Exception {
		this.configure("0");
		VaultManager vault = manager(this.appRole("good-secret"));
		Assertions.assertEquals("Secret1", vault.resolve(Secret.vault(1, "lab/device1/password")));
		this.validTokens.clear();
		Assertions.assertEquals("Secret1", vault.resolve(Secret.vault(1, "lab/device1/password")));
		Assertions.assertEquals(2L, this.count("POST /v1/auth/approle/login"));
	}

	@Test
	@DisplayName("JWT: a token of the identity provider (client credentials) is exchanged for a Vault token")
	void jwtLogin() throws Exception {
		VaultManager vault = manager(this.jwt("client-secret"));
		Assertions.assertEquals("Secret1", vault.resolve(Secret.vault(2, "lab/device1/password")));
		Assertions.assertEquals(List.of("POST /idp/token", "POST /v1/auth/jwt/login", "GET /v1/kv/data/lab/device1"), this.requests);

		VaultManager refused = manager(this.jwt("wrong-secret"));
		VaultException error = Assertions.assertThrows(VaultException.class, () -> refused.resolve(Secret.vault(2, "lab/device1/password")));
		Assertions.assertTrue(error.getMessage().contains("Failed to obtain JWT from IdP for Vault instance 'lab-vault-jwt': Unknown client"),
			error.getMessage());
	}

	@Test
	@DisplayName("Test connection: an incomplete configuration is refused before any request, then the login is tried")
	void testConnection() throws Exception {
		VaultManager vault = manager();
		VaultException noMethod = Assertions.assertThrows(VaultException.class,
			() -> vault.testConnection(this.appRole("good-secret").toBuilder().authMethod(null).build()));
		Assertions.assertEquals("No authentication method configured", noMethod.getMessage());
		VaultException jwt = Assertions.assertThrows(VaultException.class,
			() -> vault.testConnection(this.jwt("client-secret").toBuilder().jwtVaultRole(null).build()));
		Assertions.assertEquals("Incomplete JWT authentication configuration", jwt.getMessage());
		VaultException appRole = Assertions.assertThrows(VaultException.class,
			() -> vault.testConnection(this.appRole("good-secret").toBuilder().appRoleSecretId(null).build()));
		Assertions.assertEquals("Incomplete AppRole authentication configuration", appRole.getMessage());
		Assertions.assertTrue(this.requests.isEmpty());

		vault.testConnection(this.appRole("good-secret"));
		vault.testConnection(this.jwt("client-secret"));
		Assertions.assertThrows(VaultException.class, () -> vault.testConnection(this.appRole("bad-secret")));
		Assertions.assertEquals(0, vault.stats().get("cachedTokens"), "Test logins aren't cached");
	}
}
