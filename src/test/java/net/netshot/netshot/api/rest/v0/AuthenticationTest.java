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
package net.netshot.netshot.api.rest.v0;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;

import jakarta.ws.rs.core.Response;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.ApiTokenRepository;
import net.netshot.netshot.aaa.user.PasswordPolicy.PasswordPolicyException;
import net.netshot.netshot.aaa.user.PasswordPolicy;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.UserRepository;
import net.netshot.netshot.platform.database.schema.Tables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.api.v0.V0Enabled;
import net.netshot.netshot.testing.TestDatabase;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.v0.V0Client;
import net.netshot.netshot.testing.api.v0.V0Client.WrongApiResponseException;
import org.junit.jupiter.api.BeforeEach;

/**
 * How the requests to the v0 API are authenticated: API tokens, local users and their session cookies,
 * expired passwords. The rest (session idle timeout, OIDC, JWT bearer tokens, password policy) is the same
 * for every API version and is tested through the v1 API.
 */
@ApiTest
@V0Enabled
@DisplayName("v0 request authentication")
public class AuthenticationTest {

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
			Assertions.assertEquals(200, this.getWithCookie("/domains", cookie));
			Assertions.assertEquals(401, this.getWithCookie("/domains", cookie,
				"X-Netshot-API-Token", "00000000000000000000000000000000"), "With an invalid API token");
			Assertions.assertEquals(401, this.getWithCookie("/domains", cookie,
				"Authorization", "Bearer 00000000000000000000000000000000"), "With an invalid bearer API token");
			Assertions.assertEquals(401, this.getWithCookie("/domains", cookie,
				"Authorization", "Bearer aaa.bbb.ccc"), "With an invalid JWT");
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

	}
}
