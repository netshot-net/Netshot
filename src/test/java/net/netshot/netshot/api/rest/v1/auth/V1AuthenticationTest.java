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
package net.netshot.netshot.api.rest.v1.auth;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.aaa.auth.OidcDiscovery;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.UserRepository;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDatabase;
import net.netshot.netshot.testing.TestProperty;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.v1.V1Client;
import net.netshot.netshot.testing.api.v1.V1Client.Answer;
import net.netshot.netshot.testing.fake.FakeOidcIdpServer;

/**
 * How the requests to the v1 API are authenticated: API tokens, session cookies (idle timeout, invalid
 * credentials beside a session), OIDC authorization codes and JWT bearer tokens. The session resource itself
 * (log in and out, current user, own password) is tested by {@link V1AuthResourceTest}.
 */
@ApiTest
// Very low value for session expiration testing
@TestProperty(name = "netshot.aaa.maxidletime", value = "10")
@DisplayName("v1 request authentication")
@TestInstance(Lifecycle.PER_CLASS)
public class V1AuthenticationTest {

	private static final String USERNAME = "testuser";
	private static final String PASSWORD = "testpassword";

	/** The database of the class, whose configuration some tests change. */
	private TestDatabase database;

	/** The test server. */
	private TestServer server;

	@BeforeAll
	void keepDatabase(TestDatabase testDatabase, TestServer testServer) {
		this.database = testDatabase;
		this.server = testServer;
	}

	/**
	 * Gets the session cookie ("name=value") set by an answer.
	 *
	 * @param answer the answer
	 * @return the cookie, or null if none is set
	 */
	private static String sessionCookie(Answer answer) {
		return answer.headers().allValues("Set-Cookie").stream()
			.filter(c -> c.startsWith("NetshotSessionID=")).map(c -> c.substring(0, c.indexOf(';'))).findFirst().orElse(null);
	}

	/**
	 * Gets the status of an anonymous GET with the given headers.
	 *
	 * @param path the path under the v1 API
	 * @param headers the headers (name, value, name, value...)
	 * @return the status
	 */
	private int status(String path, String... headers) throws IOException, InterruptedException {
		return new V1Client(this.server, null).get(path, headers).status();
	}

	@Nested
	@DisplayName("API tokens")
	class ApiTokenTest {

		@Test
		@DisplayName("Missing, empty or wrong API token: 401")
		void wrongApiToken() throws Exception {
			Assertions.assertEquals(401, status("/devices"));
			Assertions.assertEquals(401, status("/devices", "X-Netshot-API-Token", ""));
			Assertions.assertEquals(401, status("/devices", "X-Netshot-API-Token", "WRONGTOKEN"));
			Assertions.assertEquals(401, status("/devices", "Authorization", "Bearer WRONGTOKEN"));
		}

		@Test
		@DisplayName("Valid API token, as a header or a bearer token; missing privilege: 403")
		void validApiToken() throws Exception {
			String admin = TestServer.API_TOKENS.get(UiUser.LEVEL_ADMIN);
			String operator = TestServer.API_TOKENS.get(UiUser.LEVEL_EXECUTEREADWRITE);
			Assertions.assertEquals(200, status("/api-tokens", "X-Netshot-API-Token", admin));
			Assertions.assertEquals(200, status("/api-tokens", "Authorization", "Bearer " + admin));
			Assertions.assertEquals(403, status("/api-tokens", "X-Netshot-API-Token", operator));
		}
	}

	@Nested
	@DisplayName("Sessions")
	class SessionTest {

		@BeforeEach
		void createUser() {
			UiUser user = UiUser.withPassword(USERNAME, true, UiUser.LEVEL_ADMIN, PASSWORD);
			TestWiring.get().transactions().run(dsl -> new UserRepository().add(dsl, user));
		}

		@AfterEach
		void deleteUser() {
			TestWiring.get().transactions().run(dsl -> dsl.deleteFrom(Tables.USER).execute());
		}

		private String logIn() throws Exception {
			Answer answer = new V1Client(server, null).post("/auth/session", """
				{"username": "%s", "password": "%s"}""".formatted(USERNAME, PASSWORD));
			Assertions.assertEquals(201, answer.status(), answer.toString());
			return sessionCookie(answer);
		}

		@Test
		@DisplayName("Wrong session cookie: 401")
		void wrongCookie() throws Exception {
			Assertions.assertEquals(401, status("/devices", "Cookie", "NetshotSessionID=9212336284027029412"));
		}

		@Test
		@DisplayName("Invalid credentials in a request don't fall back to the session")
		@ResourceLock("DB")
		void invalidCredentialsWithSession() throws Exception {
			String cookie = this.logIn();
			Assertions.assertEquals(200, status("/domains", "Cookie", cookie));
			Assertions.assertEquals(401, status("/domains", "Cookie", cookie,
				"X-Netshot-API-Token", "00000000000000000000000000000000"), "With an invalid API token");
			Assertions.assertEquals(401, status("/domains", "Cookie", cookie,
				"Authorization", "Bearer 00000000000000000000000000000000"), "With an invalid bearer API token");
			Assertions.assertEquals(401, status("/domains", "Cookie", cookie,
				"Authorization", "Bearer aaa.bbb.ccc"), "With an invalid JWT");
		}

		@Test
		@DisplayName("Idle session timeout")
		@ResourceLock("DB")
		void sessionTimeout() throws Exception {
			String cookie = this.logIn();
			Assertions.assertEquals(200, status("/domains", "Cookie", cookie));
			Thread.sleep(Duration.ofSeconds(15));
			Assertions.assertEquals(401, status("/domains", "Cookie", cookie), "The session didn't expire");
		}
	}

	@Nested
	@DisplayName("OIDC")
	@TestInstance(Lifecycle.PER_CLASS)
	class OidcTest {

		private static final String REDIRECT_URI = "http://localhost:8080/";
		private static final String CLIENT_ID = "netshot";
		private static final String CLIENT_SECRET = "iR56DPj4ZX0TrB1NSCHsPNk6LAbrN3HE";

		private FakeOidcIdpServer idpServer;

		private OidcDiscovery oidcDiscovery;

		@BeforeAll
		void prepareOidc() throws IOException, InterruptedException, GeneralSecurityException {
			this.idpServer = new FakeOidcIdpServer();
			this.idpServer.registerClient(CLIENT_ID, CLIENT_SECRET, java.net.URI.create(REDIRECT_URI));
			this.idpServer.start();
			this.setJwt(false, null);
			this.oidcDiscovery = new OidcDiscovery(TestWiring.get().oidc(), Netshot.config());
			this.oidcDiscovery.start();
			// Wait for the discovery daemon to fetch the IdP metadata, instead of a fixed
			// sleep: the daemon retries only every 30s (netshot.aaa.oidc.idp.retryinterval)
			// after a failed attempt, so a short fixed sleep is flaky on loaded CI runners.
			Instant deadline = Instant.now().plus(Duration.ofSeconds(35));
			while (!TestWiring.get().oidc().isAvailable() && Instant.now().isBefore(deadline)) {
				Thread.sleep(200);
			}
			Assertions.assertTrue(TestWiring.get().oidc().isAvailable(), "OIDC IdP metadata was not discovered in time");
		}

		@AfterAll
		void stopOidc() throws InterruptedException {
			this.oidcDiscovery.stop();
			this.idpServer.shutdown();
		}

		private void setJwt(boolean enabled, String audience) {
			Properties config = database.netshotConfig();
			config.setProperty("netshot.aaa.oidc.idp.url", this.idpServer.getBaseUri().toString());
			config.setProperty("netshot.aaa.oidc.clientid", CLIENT_ID);
			config.setProperty("netshot.aaa.oidc.clientsecret", CLIENT_SECRET);
			config.setProperty("netshot.aaa.oidc.jwt.enabled", Boolean.toString(enabled));
			if (audience != null) {
				config.setProperty("netshot.aaa.oidc.jwt.audience", audience);
			}
			TestConfig.init(config);
		}

		private Answer logIn(String authorizationCode) throws Exception {
			return new V1Client(server, null).post("/auth/session", """
				{"authorizationCode": "%s", "redirectUri": "%s"}""".formatted(authorizationCode, REDIRECT_URI));
		}

		@Test
		@DisplayName("Authorization code: session of a remote user, with the level of its role")
		void codeAuthentication() throws Exception {
			String username = "oidcreadwrite";
			UiUser.Role role = UiUser.Role.READWRITE;
			String authorizationCode = "apzoeilpqoisdmlkaze120398O2374lmakzhe123";
			this.idpServer.addAuthorizatioCode(authorizationCode, username, role.getName());

			Assertions.assertEquals(401, this.logIn("wrongcode").status(), "Logged in with a wrong authorization code");

			Answer login = this.logIn(authorizationCode);
			Assertions.assertEquals(201, login.status(), login.toString());
			String cookie = sessionCookie(login);
			JsonNode me = new V1Client(server, null).get("/auth/me", "Cookie", cookie).json();
			Assertions.assertEquals(username, me.get("username").asText());
			Assertions.assertFalse(me.get("local").asBoolean());
			Assertions.assertEquals(role.getLevel(), me.get("level").asInt());

			Assertions.assertEquals(403, status("/api-tokens", "Cookie", cookie), "Missing privilege");

			Assertions.assertEquals(204, new V1Client(server, null).delete("/auth/session", "Cookie", cookie).status());
			Assertions.assertEquals(401, status("/domains", "Cookie", cookie), "The session survived the logout");
		}

		@Test
		@DisplayName("Idle timeout of an OIDC session")
		void idleTimeout() throws Exception {
			String authorizationCode = "pAmljUL1h23JKDSo1kjoi23KJSDnhkj028Jkj";
			this.idpServer.addAuthorizatioCode(authorizationCode, "oidcreadonly", UiUser.Role.READONLY.getName());
			Answer login = this.logIn(authorizationCode);
			Assertions.assertEquals(201, login.status(), login.toString());
			String cookie = sessionCookie(login);
			Thread.sleep(Duration.ofSeconds(5));
			Assertions.assertEquals(200, status("/auth/me", "Cookie", cookie), "Expired before the idle timeout");
			Thread.sleep(Duration.ofSeconds(8));
			Assertions.assertEquals(200, status("/auth/me", "Cookie", cookie), "Expired before the idle timeout");
			Thread.sleep(Duration.ofSeconds(15));
			Assertions.assertEquals(401, status("/auth/me", "Cookie", cookie), "The session didn't expire after the idle timeout");
		}

		@Test
		@DisplayName("JWT bearer tokens issued by the IdP")
		void jwtBearer() throws Exception {
			String admin = this.idpServer.issueAccessToken("jwtadmin", UiUser.Role.ADMIN.getName(), CLIENT_ID, null, Duration.ofHours(1));
			String readOnly = this.idpServer.issueAccessToken("jwtreader", UiUser.Role.READONLY.getName(), CLIENT_ID, null, Duration.ofHours(1));
			try {
				// Disabled by default
				Assertions.assertEquals(401, status("/domains", "Authorization", "Bearer " + admin));

				this.setJwt(true, null);
				Assertions.assertEquals(200, status("/domains", "Authorization", "Bearer " + admin));
				Assertions.assertEquals(200, status("/domains", "Authorization", "bearer " + admin));
				Assertions.assertEquals(200, status("/api-tokens", "Authorization", "Bearer " + admin));
				Assertions.assertEquals(200, status("/domains", "Authorization", "Bearer " + readOnly));
				Assertions.assertEquals(403, status("/api-tokens", "Authorization", "Bearer " + readOnly));

				// Tampered, expired, wrong audience, wrong issuer, no role (default level 0)
				String tampered = admin.substring(0, admin.lastIndexOf('.') + 1) + "AAAA" + admin.substring(admin.lastIndexOf('.') + 5);
				Assertions.assertEquals(401, status("/domains", "Authorization", "Bearer " + tampered));
				Assertions.assertEquals(401, status("/domains", "Authorization", "Bearer "
					+ this.idpServer.issueAccessToken("jwtadmin", "admin", CLIENT_ID, null, Duration.ofHours(-1))));
				Assertions.assertEquals(401, status("/domains", "Authorization", "Bearer "
					+ this.idpServer.issueAccessToken("jwtadmin", "admin", "other-client", null, Duration.ofHours(1))));
				Assertions.assertEquals(401, status("/domains", "Authorization", "Bearer "
					+ this.idpServer.issueAccessToken("jwtadmin", "admin", CLIENT_ID, "https://evil.example.com/", Duration.ofHours(1))));
				Assertions.assertEquals(401, status("/domains", "Authorization", "Bearer "
					+ this.idpServer.issueAccessToken("jwtnobody", null, CLIENT_ID, null, Duration.ofHours(1))));
				Assertions.assertEquals(401, status("/domains", "Authorization", "Bearer not.a.jwt"));

				// Configured audience
				this.setJwt(true, "netshot-api");
				Assertions.assertEquals(401, status("/domains", "Authorization", "Bearer " + admin));
				Assertions.assertEquals(200, status("/domains", "Authorization", "Bearer "
					+ this.idpServer.issueAccessToken("jwtadmin", "admin", "netshot-api", null, Duration.ofHours(1))));

				// An API token can be given as a bearer token too
				Assertions.assertEquals(200, status("/domains", "Authorization", "Bearer " + TestServer.API_TOKENS.get(UiUser.LEVEL_READONLY)));
			}
			finally {
				this.setJwt(false, null);
			}
		}
	}
}
