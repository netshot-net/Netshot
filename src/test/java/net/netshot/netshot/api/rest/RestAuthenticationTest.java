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
package net.netshot.netshot.api.rest;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.Response;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.aaa.auth.OidcDiscovery;
import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.ApiTokenRepository;
import net.netshot.netshot.aaa.user.PasswordPolicy.PasswordPolicyException;
import net.netshot.netshot.aaa.user.PasswordPolicy;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.UserRepository;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.platform.error.ErrorCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.ResourceLock;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDatabase;
import net.netshot.netshot.testing.TestProperty;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.v0.V0Client;
import net.netshot.netshot.testing.api.v0.V0Client.WrongApiResponseException;
import net.netshot.netshot.testing.fake.FakeOidcIdpServer;
import org.junit.jupiter.api.BeforeEach;

@ApiTest
// Very low value for session expiration testing
@TestProperty(name = "netshot.aaa.maxidletime", value = "10")
public class RestAuthenticationTest {

	/** API client of the current test, authenticated with the admin token. */
	private V0Client apiClient;

	@BeforeEach
	void createApiClient(V0Client client) {
		this.apiClient = client;
	}

	/** The database of the class, whose configuration some tests change. */
	private static TestDatabase database;

	/** The test server. */
	private static TestServer server;

	@BeforeAll
	static void keepDatabase(TestDatabase testDatabase, TestServer testServer) {
		database = testDatabase;
		server = testServer;
	}

	/**
	 * Gets a copy of the configuration of the class.
	 *
	 * @return the configuration
	 */
	private static Properties getNetshotConfig() {
		return database.netshotConfig();
	}

	@Nested
	@DisplayName("Authentication Tests")
	class ApiTokenTest {

		@AfterEach
		void cleanUpData() {
			TestWiring.get().transactions().run(dsl -> dsl.deleteFrom(Tables.USER).execute());
		}

		@Test
		@DisplayName("Missing API token")
		void missingApiToken() throws IOException, InterruptedException {
			apiClient.setApiToken(null);
			HttpResponse<JsonNode> response = apiClient.get("/devices");
			Assertions.assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.statusCode(),
				"Not getting 401 response for missing API token");
			Assertions.assertInstanceOf(MissingNode.class,
				response.body(), "Response body not empty");
		}

		@Test
		@DisplayName("Empty API token")
		void emptyApiToken() throws IOException, InterruptedException {
			apiClient.setApiToken("");
			HttpResponse<JsonNode> response = apiClient.get("/devices");
			Assertions.assertEquals(
				Response.Status.UNAUTHORIZED.getStatusCode(), response.statusCode(),
				"Not getting 401 response for empty API token");
			Assertions.assertInstanceOf(MissingNode.class,
				response.body(), "Response body not empty");
		}

		@Test
		@DisplayName("Wrong API token")
		void wrongApiToken() throws IOException, InterruptedException {
			apiClient.setApiToken("WRONGTOKEN");
			HttpResponse<JsonNode> response = apiClient.get("/devices");
			Assertions.assertEquals(
				Response.Status.UNAUTHORIZED.getStatusCode(), response.statusCode(),
				"Not getting 401 response for wrong API token");
			Assertions.assertInstanceOf(MissingNode.class,
				response.body(), "Response body not empty");
		}

		@Test
		@DisplayName("Missing privilege")
		void notAdminToken() throws IOException, InterruptedException {
			apiClient.setApiToken(TestServer.API_TOKENS.get(UiUser.LEVEL_EXECUTEREADWRITE));
			HttpResponse<JsonNode> response = apiClient.get("/apitokens");
			Assertions.assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.statusCode(),
				"Not getting 403 response while missing privileges");
			Assertions.assertInstanceOf(MissingNode.class,
				response.body(), "Response body not empty");
		}

		@Test
		@DisplayName("Current user retrieval for API token")
		void currentUser() throws IOException, InterruptedException {
			String secret = "jmE5C9JHDpLtbGswYfWBdUayKFn7Th6R";
			ApiToken temp = ApiToken.of("Token get test", secret, UiUser.LEVEL_READONLY);
			ApiToken token1 = temp.withId(TestWiring.get().transactions().write(dsl -> new ApiTokenRepository().add(dsl, temp)));
			apiClient.setApiToken(secret);
			HttpResponse<JsonNode> response = apiClient.get("/user");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for /user");

			Assertions.assertEquals(
				JsonNodeFactory.instance.objectNode()
					.put("id", token1.id())
					.put("description", token1.description())
					.put("level", Long.valueOf(token1.level())),
				response.body(),
				"Retrieved user/token doesn't match expected object");
		}


	}

	@Nested
	@DisplayName("Local Authentication Tests")
	class LocalAuthenticationTest {

		private String testUsername = "testuser";
		private String testPassword = "testpassword";
		private String[] testOldPasswords = new String[] {
			"testpassword02",
			"testpassword01",
			"testpassword00",
		};
		private int testUserLevel = UiUser.LEVEL_ADMIN;
		private int passwordAge;
		private UiUser testUser;

		private void createTestUser() {
			List<String> oldPasswords = new ArrayList<>(List.of(testOldPasswords));
			UiUser user = UiUser.withPassword(testUsername, true, testUserLevel, oldPasswords.removeLast());
			while (oldPasswords.size() > 0) {
				try {
					user = user.withPassword(oldPasswords.removeLast(), PasswordPolicy.getMainPolicy());
				}
				catch (PasswordPolicyException e) {
					// Ignore
				}
			}
			user = user.withPassword(testPassword);
			if (passwordAge > 0) {
				user = user.withLastPasswordChangeDate(Instant.now().minus(passwordAge, ChronoUnit.DAYS));
			}
			UiUser stored = user;
			this.testUser = stored.withId(TestWiring.get().transactions().write(dsl -> new UserRepository().add(dsl, stored)));
		}

		@Test
		@DisplayName("Local user authentication and cookie")
		@ResourceLock("DB")
		void localUserAuth() throws IOException, InterruptedException {
			this.createTestUser();
			apiClient.setLogin(testUsername, testPassword);
			HttpResponse<JsonNode> response1 = apiClient.get("/domains");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response1.statusCode(),
				"Unable to get data using username/password and cookie API access");
			HttpCookie sessionCookie = apiClient.getSessionCookie();
			apiClient.logout();
			apiClient.addSessionCookie(sessionCookie);
			HttpResponse<JsonNode> response2 = apiClient.get("/domains");
			Assertions.assertEquals(
				Response.Status.UNAUTHORIZED.getStatusCode(), response2.statusCode(),
				"Not getting 401 response for post-logout request");
		}

		@Test
		@DisplayName("Invalid credentials in a request don't fall back to the session")
		@ResourceLock("DB")
		void invalidCredentialsWithSession() throws IOException, InterruptedException {
			this.createTestUser();
			apiClient.setLogin(testUsername, testPassword);
			Assertions.assertEquals(Response.Status.OK.getStatusCode(), apiClient.get("/domains").statusCode());
			HttpCookie sessionCookie = apiClient.getSessionCookie();
			String cookie = sessionCookie.getName() + "=" + sessionCookie.getValue();
			for (String version : List.of("", "/v1")) {
				Assertions.assertEquals(200, this.getWithCookie(version + "/domains", cookie), version);
				Assertions.assertEquals(401, this.getWithCookie(version + "/domains", cookie,
					"X-Netshot-API-Token", "00000000000000000000000000000000"), version + " with an invalid API token");
				Assertions.assertEquals(401, this.getWithCookie(version + "/domains", cookie,
					"Authorization", "Bearer 00000000000000000000000000000000"), version + " with an invalid bearer API token");
				Assertions.assertEquals(401, this.getWithCookie(version + "/domains", cookie,
					"Authorization", "Bearer aaa.bbb.ccc"), version + " with an invalid JWT");
			}
		}

		private int getWithCookie(String path, String cookie, String... headers) throws IOException, InterruptedException {
			HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(server.apiUrl() + path))
				.header("Cookie", cookie).header("Accept", "application/json");
			for (int i = 0; i + 1 < headers.length; i += 2) {
				builder.header(headers[i], headers[i + 1]);
			}
			return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
		}

		@Test
		@DisplayName("Session ID is renewed on login (session fixation)")
		@ResourceLock("DB")
		void sessionRenewedOnLogin() throws IOException, InterruptedException {
			this.createTestUser();
			apiClient.setLogin(testUsername, testPassword);
			HttpResponse<JsonNode> response1 = apiClient.get("/domains");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response1.statusCode(),
				"Unable to get data using username/password and cookie API access");
			HttpCookie firstCookie = apiClient.getSessionCookie();
			// Log in again, while sending the existing session cookie
			apiClient.login();
			HttpCookie secondCookie = apiClient.getSessionCookie();
			Assertions.assertNotEquals(firstCookie.getValue(), secondCookie.getValue(),
				"The session ID wasn't renewed on login");
			apiClient.addSessionCookie(firstCookie);
			HttpResponse<JsonNode> response2 = apiClient.get("/domains");
			Assertions.assertEquals(
				Response.Status.UNAUTHORIZED.getStatusCode(), response2.statusCode(),
				"The session existing before login is still valid");
		}

		@Test
		@DisplayName("Local user authentication and idle session timeout")
		@ResourceLock("DB")
		void sessionTimeout() throws IOException, InterruptedException {
			this.createTestUser();
			apiClient.setLogin(testUsername, testPassword);
			HttpResponse<JsonNode> response1 = apiClient.get("/domains");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response1.statusCode(),
				"Unable to get data using username/password and cookie API access");
			Thread.sleep(Duration.ofSeconds(15));
			HttpResponse<JsonNode> response2 = apiClient.get("/domains");
			Assertions.assertEquals(
				Response.Status.UNAUTHORIZED.getStatusCode(), response2.statusCode(),
				"Not getting 401 response while session have expired");
		}

		@Test
		@DisplayName("Current user retrieval for local user")
		void currentUser() throws IOException, InterruptedException {
			this.createTestUser();
			apiClient.setLogin(testUsername, testPassword);
			HttpResponse<JsonNode> response = apiClient.get("/user");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for /user");

			Assertions.assertEquals(
				JsonNodeFactory.instance.objectNode()
					.put("id", this.testUser.id())
					.put("username", this.testUser.username())
					.put("local", true)
					.put("level", Long.valueOf(this.testUser.level())),
				response.body(),
				"Retrieved user doesn't match expected object");
		}

		@Test
		@DisplayName("Wrong cookie")
		void wrongCookie() throws IOException, InterruptedException {
			apiClient.setApiToken(null);
			apiClient.addSessionCookie("9212336284027029412");
			HttpResponse<JsonNode> response = apiClient.get("/devices");
			Assertions.assertEquals(
				Response.Status.UNAUTHORIZED.getStatusCode(), response.statusCode(),
				"Not getting 401 response for wrong cookie");
			Assertions.assertInstanceOf(MissingNode.class,
				response.body(), "Response body not empty");
		}

		@Test
		@DisplayName("Expired password authentication attempt")
		@ResourceLock("DB")
		void expiredPasswordFailAuth() throws IOException, InterruptedException {
			Properties config = getNetshotConfig();
			config.setProperty("netshot.aaa.passwordpolicy.maxduration", "90");
			TestConfig.init(config);
			this.passwordAge = 365;
			this.createTestUser();
			apiClient.setLogin(testUsername, testPassword);
			WrongApiResponseException thrown = Assertions.assertThrows(WrongApiResponseException.class,
				() -> apiClient.get("/domains"),
				"Login not failing as expected");
			Assertions.assertEquals(
				Response.Status.PRECONDITION_FAILED.getStatusCode(), thrown.getResponse().statusCode(),
				"Not getting 412 when logging in with expired password");
		}

		@Test
		@DisplayName("Expired password, change and authentication attempt")
		@ResourceLock("DB")
		void expiredPasswordChangeAuth() throws IOException, InterruptedException {
			Properties config = getNetshotConfig();
			config.setProperty("netshot.aaa.passwordpolicy.maxduration", "90");
			TestConfig.init(config);
			String newPassword = "testpassword1";
			this.passwordAge = 365;
			this.createTestUser();
			apiClient.setLogin(testUsername, testPassword);
			apiClient.setNewPassword(newPassword);
			HttpResponse<JsonNode> response = apiClient.get("/domains");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response");
			{
				UiUser user = TestWiring.get().transactions().read(dsl -> new UserRepository().findByUsername(dsl, testUsername));
				Assertions.assertDoesNotThrow(() -> user.checkPassword(newPassword, null),
					"Password not changed as expected");
			}
		}

		@Test
		@DisplayName("The password cannot be changed if auth fails")
		@ResourceLock("DB")
		void wrongAuthCantChangePassword() throws IOException, InterruptedException {
			String newPassword = "testpassword1";
			this.createTestUser();
			apiClient.setLogin(testUsername, "wrongpass");
			apiClient.setNewPassword(newPassword);
			Assertions.assertThrows(WrongApiResponseException.class,
				() -> apiClient.get("/domains"),
				"Login should have failed");
			{
				UiUser user = TestWiring.get().transactions().read(dsl -> new UserRepository().findByUsername(dsl, testUsername));
				Assertions.assertDoesNotThrow(() -> user.checkPassword(testPassword, null),
					"Password should not have changed");
			}
		}

		@Test
		@DisplayName("Password policy")
		@ResourceLock("DB")
		void passwordChangeWithPolicy() throws IOException, InterruptedException {
			Properties config = getNetshotConfig();
			config.setProperty("netshot.aaa.passwordpolicy.maxhistory", "5");
			TestConfig.init(config);
			this.createTestUser();
			config.setProperty("netshot.aaa.passwordpolicy.mintotalchars", "16");
			config.setProperty("netshot.aaa.passwordpolicy.minspecialchars", "3");
			config.setProperty("netshot.aaa.passwordpolicy.minnumericalchars", "3");
			config.setProperty("netshot.aaa.passwordpolicy.minlowercasechars", "3");
			config.setProperty("netshot.aaa.passwordpolicy.minuppercasechars", "3");
			TestConfig.init(config);
			apiClient.setLogin(testUsername, testPassword);
			{
				HttpResponse<JsonNode> response = apiClient.get("/user");
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Not getting 200 response");
				JsonNode userNode = response.body();
				Assertions.assertEquals(testUsername, userNode.get("username").asText());
				Assertions.assertEquals(testUserLevel, userNode.get("level").asInt());
			}
			{
				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("username", testUsername)
					// missing password
					.put("newPassword", "New902C0pml;(EP!$");
				HttpResponse<JsonNode> response = apiClient.put("/user/0", data);
				Assertions.assertEquals(
					Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
					"Not getting 400 response");
				Assertions.assertEquals(
					response.body().get("errorCode").asInt(),
					ErrorCode.NETSHOT_INVALID_PASSWORD.getCode(),
					"Unexpected Netshot error code");
			}
			{
				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("username", testUsername)
					.put("password", "invalidpass")
					.put("newPassword", "New902C0pml;(EP!$");
				HttpResponse<JsonNode> response = apiClient.put("/user/0", data);
				Assertions.assertEquals(
					Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
					"Not getting 400 response");
				Assertions.assertEquals(
					response.body().get("errorCode").asInt(),
					ErrorCode.NETSHOT_INVALID_PASSWORD.getCode(),
					"Unexpected Netshot error code");
			}
			{
				String newPassword = testOldPasswords[2];
				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("username", testUsername)
					.put("password", testPassword)
					.put("newPassword", newPassword);
				HttpResponse<JsonNode> response = apiClient.put("/user/0", data);
				Assertions.assertEquals(
					Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
					"Not getting 400 response");
				Assertions.assertEquals(
					response.body().get("errorCode").asInt(),
					ErrorCode.NETSHOT_FAILED_PASSWORD_POLICY.getCode(),
					"Unexpected Netshot error code");
			}
			{
				String newPassword = "pass";
				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("username", testUsername)
					.put("password", testPassword)
					.put("newPassword", newPassword);
				HttpResponse<JsonNode> response = apiClient.put("/user/0", data);
				Assertions.assertEquals(
					Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
					"Not getting 400 response");
				Assertions.assertEquals(
					response.body().get("errorCode").asInt(),
					ErrorCode.NETSHOT_FAILED_PASSWORD_POLICY.getCode(),
					"Unexpected Netshot error code");
			}
			{
				String newPassword = "newverylongpassword";
				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("username", testUsername)
					.put("password", testPassword)
					.put("newPassword", newPassword);
				HttpResponse<JsonNode> response = apiClient.put("/user/0", data);
				Assertions.assertEquals(
					Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
					"Not getting 400 response");
				Assertions.assertEquals(
					response.body().get("errorCode").asInt(),
					ErrorCode.NETSHOT_FAILED_PASSWORD_POLICY.getCode(),
					"Unexpected Netshot error code");
			}
			{
				String newPassword = "newverylongpassword123";
				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("username", testUsername)
					.put("password", testPassword)
					.put("newPassword", newPassword);
				HttpResponse<JsonNode> response = apiClient.put("/user/0", data);
				Assertions.assertEquals(
					Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
					"Not getting 400 response");
				Assertions.assertEquals(
					response.body().get("errorCode").asInt(),
					ErrorCode.NETSHOT_FAILED_PASSWORD_POLICY.getCode(),
					"Unexpected Netshot error code");
			}
			{
				String newPassword = "newverylongPASSWORD123";
				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("username", testUsername)
					.put("password", testPassword)
					.put("newPassword", newPassword);
				HttpResponse<JsonNode> response = apiClient.put("/user/0", data);
				Assertions.assertEquals(
					Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
					"Not getting 400 response");
				Assertions.assertEquals(
					response.body().get("errorCode").asInt(),
					ErrorCode.NETSHOT_FAILED_PASSWORD_POLICY.getCode(),
					"Unexpected Netshot error code");
			}
			{
				String newPassword = "newverylongPASS123!!$$";
				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("username", testUsername)
					.put("password", testPassword)
					.put("newPassword", newPassword);
				HttpResponse<JsonNode> response = apiClient.put("/user/0", data);
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Not getting 200 response");
				{
					UiUser user = TestWiring.get().transactions().read(dsl -> new UserRepository().findByUsername(dsl, testUsername));
					Assertions.assertDoesNotThrow(() -> user.checkPassword(newPassword, null),
						"Password should have changed");
				}
			}

		}
	}

	@Nested
	@DisplayName("OIDC Authentication Tests")
	@TestInstance(Lifecycle.PER_CLASS)
	class OidcAuthenticationTest {

		private final URI redirectUri = URI.create("http://localhost:8080/");
		private final String clientId = "netshot";
		private final String clientSecret = "iR56DPj4ZX0TrB1NSCHsPNk6LAbrN3HE";
		private FakeOidcIdpServer idpServer;

		private OidcDiscovery oidcDiscovery;

		@BeforeAll
		void prepareOidc() throws IOException, InterruptedException, GeneralSecurityException {
			this.idpServer = new FakeOidcIdpServer();
			this.idpServer.registerClient(clientId, clientSecret, redirectUri);
			this.idpServer.start();
			Properties config = getNetshotConfig();
			config.setProperty("netshot.aaa.oidc.idp.url", this.idpServer.getBaseUri().toString());
			config.setProperty("netshot.aaa.oidc.clientid", clientId);
			config.setProperty("netshot.aaa.oidc.clientsecret", clientSecret);
			TestConfig.init(config);
			this.oidcDiscovery = new OidcDiscovery(TestWiring.get().oidc(), Netshot.config());
			this.oidcDiscovery.start();
			// Wait for the discovery daemon to fetch the IdP metadata, instead of a fixed
			// sleep: the daemon retries only every 30s (netshot.aaa.oidc.idp.retryinterval)
			// after a failed attempt, so a short fixed sleep is flaky on loaded CI runners.
			final Duration idpDiscoveryTimeout = Duration.ofSeconds(35);
			final Instant deadline = Instant.now().plus(idpDiscoveryTimeout);
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

		@Test
		@DisplayName("OIDC code based authentication")
		void oidcCodeAuth() throws IOException, InterruptedException {
			final String username = "oidcreadwrite";
			final UiUser.Role role = UiUser.Role.READWRITE;
			final String authorizationCode = "apzoeilpqoisdmlkaze120398O2374lmakzhe123";
			this.idpServer.addAuthorizatioCode(authorizationCode, username, role.getName());

			// Authentication attempt with wrong authorization code
			apiClient.setOidcCodeLogin("wrongcode", redirectUri.toString());
			WrongApiResponseException thrown = Assertions.assertThrows(WrongApiResponseException.class,
				() -> apiClient.get("/user"),
				"Login not failing as expected");
			Assertions.assertEquals(
				Response.Status.UNAUTHORIZED.getStatusCode(), thrown.getResponse().statusCode(),
				"Not getting 401 when logging in with wrong authorization code");

			// Authentication attempt with proper authorization code
			apiClient.setOidcCodeLogin(authorizationCode, redirectUri.toString());
			HttpResponse<JsonNode> response2 = apiClient.get("/user");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response2.statusCode(),
				"Unable to get user profile after OIDC code and cookie API access");
			Assertions.assertEquals(
				JsonNodeFactory.instance.objectNode()
					.put("id", Long.valueOf(0))
					.put("local", false)
					.put("username", username)
					.put("level", Long.valueOf(role.getLevel())),
				response2.body(),
				"Retrieved user doesn't match expected object");

			// Permission test
			HttpResponse<JsonNode> response = apiClient.get("/apitokens");
			Assertions.assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.statusCode(),
				"Not getting 403 response while missing privileges");
			Assertions.assertInstanceOf(MissingNode.class,
				response.body(), "Response body not empty");

			// Forced logout and try again with cookie
			HttpCookie sessionCookie = apiClient.getSessionCookie();
			apiClient.logout();
			apiClient.addSessionCookie(sessionCookie);
			HttpResponse<JsonNode> response3 = apiClient.get("/domains");
			Assertions.assertEquals(
				Response.Status.UNAUTHORIZED.getStatusCode(), response3.statusCode(),
				"Not getting 401 response for post-logout request");
		}

		/**
		 * Sends a GET with an Authorization header.
		 *
		 * @param path the path under the API path
		 * @param authorization the Authorization header
		 * @return the status of the answer
		 */
		private int getWithAuthorization(String path, String authorization) throws IOException, InterruptedException {
			return HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(server.apiUrl() + path))
					.header("Authorization", authorization).header("Accept", "application/json").build(),
				HttpResponse.BodyHandlers.ofString()).statusCode();
		}

		private void setJwt(boolean enabled, String audience) {
			Properties config = getNetshotConfig();
			config.setProperty("netshot.aaa.oidc.idp.url", this.idpServer.getBaseUri().toString());
			config.setProperty("netshot.aaa.oidc.clientid", clientId);
			config.setProperty("netshot.aaa.oidc.clientsecret", clientSecret);
			config.setProperty("netshot.aaa.oidc.jwt.enabled", Boolean.toString(enabled));
			if (audience != null) {
				config.setProperty("netshot.aaa.oidc.jwt.audience", audience);
			}
			TestConfig.init(config);
		}

		@Test
		@DisplayName("JWT bearer tokens issued by the IdP")
		void jwtBearer() throws Exception {
			String admin = this.idpServer.issueAccessToken("jwtadmin", UiUser.Role.ADMIN.getName(), clientId, null, Duration.ofHours(1));
			String readOnly = this.idpServer.issueAccessToken("jwtreader", UiUser.Role.READONLY.getName(), clientId, null, Duration.ofHours(1));
			try {
				// Disabled by default
				this.setJwt(false, null);
				Assertions.assertEquals(401, this.getWithAuthorization("/v1/domains", "Bearer " + admin));

				this.setJwt(true, null);
				Assertions.assertEquals(200, this.getWithAuthorization("/v1/domains", "Bearer " + admin));
				Assertions.assertEquals(200, this.getWithAuthorization("/domains", "bearer " + admin));
				Assertions.assertEquals(200, this.getWithAuthorization("/apitokens", "Bearer " + admin));
				Assertions.assertEquals(200, this.getWithAuthorization("/v1/domains", "Bearer " + readOnly));
				Assertions.assertEquals(403, this.getWithAuthorization("/apitokens", "Bearer " + readOnly));

				// Tampered, expired, wrong audience, wrong issuer, no role (default level 0)
				String tampered = admin.substring(0, admin.lastIndexOf('.') + 1) + "AAAA" + admin.substring(admin.lastIndexOf('.') + 5);
				Assertions.assertEquals(401, this.getWithAuthorization("/v1/domains", "Bearer " + tampered));
				Assertions.assertEquals(401, this.getWithAuthorization("/v1/domains", "Bearer "
					+ this.idpServer.issueAccessToken("jwtadmin", "admin", clientId, null, Duration.ofHours(-1))));
				Assertions.assertEquals(401, this.getWithAuthorization("/v1/domains", "Bearer "
					+ this.idpServer.issueAccessToken("jwtadmin", "admin", "other-client", null, Duration.ofHours(1))));
				Assertions.assertEquals(401, this.getWithAuthorization("/v1/domains", "Bearer "
					+ this.idpServer.issueAccessToken("jwtadmin", "admin", clientId, "https://evil.example.com/", Duration.ofHours(1))));
				Assertions.assertEquals(401, this.getWithAuthorization("/v1/domains", "Bearer "
					+ this.idpServer.issueAccessToken("jwtnobody", null, clientId, null, Duration.ofHours(1))));
				Assertions.assertEquals(401, this.getWithAuthorization("/v1/domains", "Bearer not.a.jwt"));

				// Configured audience
				this.setJwt(true, "netshot-api");
				Assertions.assertEquals(401, this.getWithAuthorization("/v1/domains", "Bearer " + admin));
				Assertions.assertEquals(200, this.getWithAuthorization("/v1/domains", "Bearer "
					+ this.idpServer.issueAccessToken("jwtadmin", "admin", "netshot-api", null, Duration.ofHours(1))));

				// An API token can be given as a bearer token too
				Assertions.assertEquals(200, this.getWithAuthorization("/v1/domains",
					"Bearer " + TestServer.API_TOKENS.get(UiUser.LEVEL_READONLY)));
			}
			finally {
				this.setJwt(false, null);
			}
		}

		@Test
		@DisplayName("OIDC idle timeout")
		void oidcIdleTimeout() throws IOException, InterruptedException {
			final String username = "oidcreadonly";
			final UiUser.Role role = UiUser.Role.READONLY;
			final String authorizationCode = "pAmljUL1h23JKDSo1kjoi23KJSDnhkj028Jkj";
			this.idpServer.addAuthorizatioCode(authorizationCode, username, role.getName());

			apiClient.setOidcCodeLogin(authorizationCode, redirectUri.toString());
			{
				HttpResponse<JsonNode> response = apiClient.get("/user");
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Unable to get user profile after OIDC code and cookie API access");
				Assertions.assertEquals(
					JsonNodeFactory.instance.objectNode()
						.put("id", Long.valueOf(0))
						.put("local", false)
						.put("username", username)
						.put("level", Long.valueOf(role.getLevel())),
					response.body(),
					"Retrieved user doesn't match expected object");
			}
			Thread.sleep(Duration.ofSeconds(5));
			{
				HttpResponse<JsonNode> response = apiClient.get("/user");
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Unable to get user profile before reaching idle timeout");
			}
			Thread.sleep(Duration.ofSeconds(8));
			{
				HttpResponse<JsonNode> response = apiClient.get("/user");
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Unable to get user profile before reaching idle timeout");
			}
			Thread.sleep(Duration.ofSeconds(15));
			{
				HttpResponse<JsonNode> response = apiClient.get("/user");
				Assertions.assertEquals(
					Response.Status.UNAUTHORIZED.getStatusCode(), response.statusCode(),
					"The session didn't expire after idle timeout as planned");
			}
		}
	}
}
