/**
 * Copyright 2013-2025 Netshot
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
package net.netshot.netshot;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.Session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import net.netshot.netshot.NetshotApiClient.WrongApiResponseException;
import net.netshot.netshot.aaa.ApiToken;
import net.netshot.netshot.aaa.Oidc;
import net.netshot.netshot.aaa.PasswordPolicy;
import net.netshot.netshot.aaa.PasswordPolicy.PasswordPolicyException;
import net.netshot.netshot.aaa.UiUser;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.rules.JavaScriptRule;
import net.netshot.netshot.compliance.rules.PythonRule;
import net.netshot.netshot.compliance.rules.TextRule;
import net.netshot.netshot.database.Database;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.Device.MissingDeviceDriverException;
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.DeviceDriver.DriverProtocol;
import net.netshot.netshot.device.DeviceGroup;
import net.netshot.netshot.device.Domain;
import net.netshot.netshot.device.Network4Address;
import net.netshot.netshot.device.StaticDeviceGroup;
import net.netshot.netshot.device.access.DeviceAccess;
import net.netshot.netshot.device.attribute.AttributeDefinition;
import net.netshot.netshot.device.credentials.DeviceCredentialSet;
import net.netshot.netshot.device.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.device.credentials.DeviceSshAccount;
import net.netshot.netshot.hooks.Hook;
import net.netshot.netshot.hooks.WebHook;
import net.netshot.netshot.rest.NetshotBadRequestException;
import net.netshot.netshot.rest.RestService;
import net.netshot.netshot.utils.HttpsCaTrustMode;
import net.netshot.netshot.work.tasks.DeviceJsScript;
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

public class RestServiceTest extends WithDatabaseTest {

	private static final String apiUrl = "http://localhost:8888/api";
	private static final long UNKNOWN_ID = 99999999;

	protected static final Map<Integer, String> API_TOKENS = Map.of(
		UiUser.LEVEL_READONLY, "mwAPEe0mQlBvKuUYYS5MiFsmnVRWZpca",
		UiUser.LEVEL_OPERATOR, "vMLKSPWMq9hFRtKkhXbvHhveXEn3Y4BZ",
		UiUser.LEVEL_READWRITE, "rCSrr3S3PGsLmi54HFw6a3PbZClpCivk",
		UiUser.LEVEL_EXECUTEREADWRITE, "PXlJ4r8VvTObn6hyknI1lwOoxEPJjCDO",
		UiUser.LEVEL_ADMIN, "hLXg3ABu1qWJPle3Z3Z22X0aLkx5T354"
	);

	protected static void createApiTokens() {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			// Remove default admin account
			session
				.createMutationQuery("delete from net.netshot.netshot.aaa.UiUser")
				.executeUpdate();
			for (Map.Entry<Integer, String> entry : API_TOKENS.entrySet()) {
				String description = "Test Token - level %d".formatted(entry.getKey());
				ApiToken token = new ApiToken(description, entry.getValue(), entry.getKey());
				session.persist(token);
			}
			session.getTransaction().commit();
		}
	}

	protected static Properties getNetshotConfig() {
		Properties config = getDatabaseConfig("restservicetest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "INFO");
		config.setProperty("netshot.http.ssl.enabled", "false");
		URI uri = UriBuilder.fromUri(apiUrl).replacePath("/").build();
		config.setProperty("netshot.http.baseurl", uri.toString());
		// Very low value for session expiration testing
		config.setProperty("netshot.aaa.maxidletime", "10");
		return config;
	}

	@BeforeAll
	protected static void initNetshot() throws Exception {
		Netshot.initConfig(getNetshotConfig());
		Netshot.loadModuleConfigs();
		Database.update();
		Database.init();
		TaskManager.init();
		RestService.init();
		Thread.sleep(1000);
	}

	private NetshotApiClient apiClient;

	@BeforeEach
	void createToken() throws URISyntaxException {
		RestServiceTest.createApiTokens();
		this.apiClient = new NetshotApiClient(RestServiceTest.apiUrl,
			RestServiceTest.API_TOKENS.get(UiUser.LEVEL_ADMIN));
	}

	@AfterEach
	void flushTokens() {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session
				.createMutationQuery("delete from ApiToken")
				.executeUpdate();
			session.getTransaction().commit();
		}
	}


	@Nested
	@DisplayName("Authentication Tests")
	class ApiTokenTest {

		@AfterEach
		void cleanUpData() {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session
					.createMutationQuery("delete from net.netshot.netshot.aaa.UiUser")
					.executeUpdate();
				session.getTransaction().commit();
			}
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
			apiClient.setApiToken(RestServiceTest.API_TOKENS.get(UiUser.LEVEL_EXECUTEREADWRITE));
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
			ApiToken token1 = new ApiToken("Token get test", secret, UiUser.LEVEL_READONLY);
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(token1);
				session.getTransaction().commit();
			}
			apiClient.setApiToken(secret);
			HttpResponse<JsonNode> response = apiClient.get("/user");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for /user");

			Assertions.assertEquals(
				JsonNodeFactory.instance.objectNode()
					.put("id", token1.getId())
					.put("description", token1.getDescription())
					.put("level", Long.valueOf(token1.getLevel())),
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
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				List<String> oldPasswords = new ArrayList<>(List.of(testOldPasswords));
				UiUser user = new UiUser(testUsername, true, oldPasswords.removeLast());
				while (oldPasswords.size() > 0) {
					try {
						user.setPassword(oldPasswords.removeLast(), PasswordPolicy.getMainPolicy());
					}
					catch (PasswordPolicyException e) {
						// Ignore
					}
				}
				user.setPassword(testPassword);
				user.setLevel(testUserLevel);
				if (passwordAge > 0) {
					Calendar oneYearAgo = Calendar.getInstance();
					oneYearAgo.add(Calendar.DATE, -1 * passwordAge);
					user.setLastPasswordChangeDate(oneYearAgo.getTime());
				}
				session.persist(user);
				session.getTransaction().commit();
				this.testUser = user;
			}
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
					.put("id", this.testUser.getId())
					.put("username", this.testUser.getUsername())
					.put("local", true)
					.put("level", Long.valueOf(this.testUser.getLevel())),
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
			Netshot.initConfig(config);
			PasswordPolicy.loadConfig();
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
			Netshot.initConfig(config);
			PasswordPolicy.loadConfig();
			String newPassword = "testpassword1";
			this.passwordAge = 365;
			this.createTestUser();
			apiClient.setLogin(testUsername, testPassword);
			apiClient.setNewPassword(newPassword);
			HttpResponse<JsonNode> response = apiClient.get("/domains");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response");
			try (Session session = Database.getSession()) {
				UiUser user = session
					.bySimpleNaturalId(UiUser.class)
					.load(testUsername);
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
			try (Session session = Database.getSession()) {
				UiUser user = session
					.bySimpleNaturalId(UiUser.class)
					.load(testUsername);
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
			Netshot.initConfig(config);
			PasswordPolicy.loadConfig();
			this.createTestUser();
			config.setProperty("netshot.aaa.passwordpolicy.mintotalchars", "16");
			config.setProperty("netshot.aaa.passwordpolicy.minspecialchars", "3");
			config.setProperty("netshot.aaa.passwordpolicy.minnumericalchars", "3");
			config.setProperty("netshot.aaa.passwordpolicy.minlowercasechars", "3");
			config.setProperty("netshot.aaa.passwordpolicy.minuppercasechars", "3");
			Netshot.initConfig(config);
			PasswordPolicy.loadConfig();
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
					NetshotBadRequestException.Reason.NETSHOT_INVALID_PASSWORD.getCode(),
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
					NetshotBadRequestException.Reason.NETSHOT_INVALID_PASSWORD.getCode(),
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
					NetshotBadRequestException.Reason.NETSHOT_FAILED_PASSWORD_POLICY.getCode(),
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
					NetshotBadRequestException.Reason.NETSHOT_FAILED_PASSWORD_POLICY.getCode(),
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
					NetshotBadRequestException.Reason.NETSHOT_FAILED_PASSWORD_POLICY.getCode(),
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
					NetshotBadRequestException.Reason.NETSHOT_FAILED_PASSWORD_POLICY.getCode(),
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
					NetshotBadRequestException.Reason.NETSHOT_FAILED_PASSWORD_POLICY.getCode(),
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
				try (Session session = Database.getSession()) {
					UiUser user = session
						.bySimpleNaturalId(UiUser.class)
						.load(testUsername);
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

		@BeforeAll
		void prepareOidc() throws IOException, InterruptedException, GeneralSecurityException {
			this.idpServer = new FakeOidcIdpServer();
			this.idpServer.registerClient(clientId, clientSecret, redirectUri);
			this.idpServer.start();
			Properties config = getNetshotConfig();
			config.setProperty("netshot.aaa.oidc.idp.url", this.idpServer.getBaseUri().toString());
			config.setProperty("netshot.aaa.oidc.clientid", clientId);
			config.setProperty("netshot.aaa.oidc.clientsecret", clientSecret);
			Netshot.initConfig(config);
			Oidc.loadConfig();
			Thread.sleep(3000);
		}

		@AfterAll
		void stopOidc() {
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

	@Nested
	@DisplayName("Admin API Tests")
	class AdminTest {

		@Nested
		@DisplayName("Domain API Tests")
		class DomainTest {

			@AfterEach
			void cleanUpData() {
				try (Session session = Database.getSession()) {
					session.beginTransaction();
					session
						.createMutationQuery("delete from Domain")
						.executeUpdate();
					session.getTransaction().commit();
				}
			}

			@Test
			@DisplayName("List domains")
			@ResourceLock("DB")
			void listDomains() throws IOException, InterruptedException {
				{
					HttpResponse<JsonNode> response = apiClient.get("/domains");
					Assertions.assertEquals(
						Response.Status.OK.getStatusCode(), response.statusCode(),
						"Not getting 200 response for initial list");
					Assertions.assertEquals(0, response.body().size(),
						"Domain list is not empty");
				}
				Domain domain1 = new Domain(
					"Test1", "Test Domain1 for listing",
					new Network4Address("10.1.1.1"),
					null
				);
				Domain domain2 = new Domain(
					"Test2", "Test Domain2 for listing",
					new Network4Address("10.1.2.1"),
					null
				);
				try (Session session = Database.getSession()) {
					session.beginTransaction();
					session.persist(domain1);
					session.persist(domain2);
					session.getTransaction().commit();
				}
				{
					HttpResponse<JsonNode> response = apiClient.get("/domains");
					Assertions.assertEquals(
						Response.Status.OK.getStatusCode(), response.statusCode(),
						"Not getting 200 response for domain list");
					Assertions.assertEquals(2, response.body().size(),
						"Domain list doesn't have 2 elements");
					Iterator<JsonNode> domainNodeIt = response.body().iterator();
					JsonNode domainNode1 = domainNodeIt.next();
					Assertions.assertEquals(
						JsonNodeFactory.instance.objectNode()
							.put("id", domain1.getId())
							.put("name", domain1.getName())
							.put("description", domain1.getDescription())
							.put("ipAddress", domain1.getServer4Address().getIp()),
						domainNode1,
						"Retrieved domain doesn't match expected object");
				}
			}

			@Test
			@DisplayName("List domains with pagination")
			@ResourceLock("DB")
			void listPaginatedDomains() throws IOException, InterruptedException {
				try (Session session = Database.getSession()) {
					session.beginTransaction();
					for (int i = 1; i <= 15; i++) {
						Domain domain = new Domain(
							"Test%d".formatted(i),
							"Test Domain%d for pagination".formatted(i),
							new Network4Address("10.1.%d.1".formatted(i)),
							null
						);
						session.persist(domain);
					}
					session.getTransaction().commit();
				}
				{
					HttpResponse<JsonNode> response = apiClient.get("/domains?limit=10");
					Assertions.assertEquals(
						Response.Status.OK.getStatusCode(), response.statusCode(),
						"Not getting 200 response for domain list");
					Assertions.assertEquals(10, response.body().size(),
						"Domain list doesn't have 10 elements");
					JsonNode firstNode = response.body().iterator().next();
					Assertions.assertEquals("Test1", firstNode.get("name").asText());
				}
				{
					HttpResponse<JsonNode> response = apiClient.get("/domains?limit=10&offset=10");
					Assertions.assertEquals(
						Response.Status.OK.getStatusCode(), response.statusCode(),
						"Not getting 200 response for domain list");
					Assertions.assertEquals(5, response.body().size(),
						"Domain list doesn't have 5 elements");
					JsonNode firstNode = response.body().iterator().next();
					Assertions.assertEquals("Test11", firstNode.get("name").asText());
				}
			}


			@Test
			@DisplayName("Create domain")
			@ResourceLock("DB")
			void createDomain() throws IOException, InterruptedException {
				Domain domain = new Domain(
					"Test", "Test Domain for creation",
					new Network4Address("10.1.1.1"),
					null
				);
				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("name", domain.getName())
					.put("description", domain.getDescription())
					.put("ipAddress", domain.getServer4Address().getIp());
				HttpResponse<JsonNode> response = apiClient.post("/domains", data);
				Assertions.assertEquals(
					Response.Status.CREATED.getStatusCode(), response.statusCode(),
					"Not getting 201 response for created domain");

				try (Session session = Database.getSession()) {
					Domain newDomain = session
						.createQuery("from Domain d where d.name = :name", Domain.class)
						.setParameter("name", domain.getName())
						.uniqueResult();
					domain.setId(newDomain.getId());
					Assertions.assertEquals(domain, newDomain, "Domain not created as expected");
				}
			}

			@Test
			@DisplayName("Delete domain")
			@ResourceLock("DB")
			void deleteDomain() throws IOException, InterruptedException {
				Domain domain1 = new Domain(
					"Test1", "Test Domain1 for deletion",
					new Network4Address("10.1.1.1"),
					null
				);
				Domain domain2 = new Domain(
					"Test2", "Test Domain2 for deletion",
					new Network4Address("10.1.2.1"),
					null
				);
				try (Session session = Database.getSession()) {
					session.beginTransaction();
					session.persist(domain1);
					session.persist(domain2);
					session.getTransaction().commit();
				}
				{
					HttpResponse<JsonNode> response = apiClient.delete(
						"/domains/%d".formatted(UNKNOWN_ID));
					Assertions.assertEquals(
						Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
						"Not getting 404 response for unknown domain deletion");
				}
				{
					HttpResponse<JsonNode> response = apiClient.delete(
						"/domains/%d".formatted(domain1.getId()));
					Assertions.assertEquals(
						Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
						"Not getting 204 response for domain deletion");
				}
				HttpResponse<JsonNode> listResponse = apiClient.get("/domains");
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), listResponse.statusCode(),
					"Not getting 200 response for domain listing");
				Assertions.assertEquals(1, listResponse.body().size(),
					"Domain list doesn't have 1 element");
				JsonNode domainNode = listResponse.body().iterator().next();
				Assertions.assertEquals(
					JsonNodeFactory.instance.objectNode()
						.put("id", domain2.getId())
						.put("name", domain2.getName())
						.put("description", domain2.getDescription())
						.put("ipAddress", domain2.getServer4Address().getIp()),
					domainNode,
					"Retrieved domain doesn't match expected object");
			}

			@Test
			@DisplayName("Update domain")
			@ResourceLock("DB")
			void updateDomain() throws IOException, InterruptedException {
				Domain domain = new Domain(
					"Test1", "Test Domain1 for update",
					new Network4Address("10.1.1.1"),
					null
				);
				try (Session session = Database.getSession()) {
					session.beginTransaction();
					session.persist(domain);
					session.getTransaction().commit();
				}
				Domain targetDomain = new Domain(
					"Test2", "Test Domain2 for update",
					new Network4Address("10.1.2.1"),
					null
				);
				targetDomain.setId(domain.getId());

				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("name", targetDomain.getName())
					.put("description", targetDomain.getDescription())
					.put("ipAddress", targetDomain.getServer4Address().getIp());
				{
					HttpResponse<JsonNode> response = apiClient.put(
						"/domains/%d".formatted(UNKNOWN_ID), data);
					Assertions.assertEquals(
						Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
						"Not getting 404 response for unknown domain update");
				}
				{
					HttpResponse<JsonNode> response = apiClient.put(
						"/domains/%d".formatted(domain.getId()), data);
					Assertions.assertEquals(
						Response.Status.OK.getStatusCode(), response.statusCode(),
						"Not getting 200 response for domain update");
				}
				try (Session session = Database.getSession()) {
					Domain dbDomain = session.byId(Domain.class)
						.load(targetDomain.getId());
					Assertions.assertEquals(
						targetDomain, dbDomain, "Domain not updated as expected");
				}
			}
		}

		@Nested
		@DisplayName("User management API Tests")
		class UserTest {

			@AfterEach
			void cleanUpData() {
				try (Session session = Database.getSession()) {
					session.beginTransaction();
					session
						.createMutationQuery("delete from net.netshot.netshot.aaa.UiUser")
						.executeUpdate();
					session.getTransaction().commit();
				}
			}

			@Test
			@DisplayName("List users")
			@ResourceLock("DB")
			void listUsers() throws IOException, InterruptedException {
				{
					HttpResponse<JsonNode> response = apiClient.get("/users");
					Assertions.assertEquals(
						Response.Status.OK.getStatusCode(), response.statusCode(),
						"Not getting 200 response for initial list");
					Assertions.assertEquals(0, response.body().size(),
						"User list is not empty");
				}
				UiUser user1 = new UiUser("user1", true, "pass1");
				user1.setLevel(UiUser.LEVEL_EXECUTEREADWRITE);
				UiUser user2 = new UiUser("user2", false, "pass2");
				user2.setLevel(UiUser.LEVEL_READONLY);
				try (Session session = Database.getSession()) {
					session.beginTransaction();
					session.persist(user1);
					session.persist(user2);
					session.getTransaction().commit();
				}
				{
					HttpResponse<JsonNode> response = apiClient.get("/users");
					Assertions.assertEquals(
						Response.Status.OK.getStatusCode(), response.statusCode(),
						"Not getting 200 response for user list");
					Assertions.assertEquals(2, response.body().size(),
						"User list doesn't have 2 elements");
					Iterator<JsonNode> userNodeIt = response.body().iterator();
					JsonNode userNode1 = userNodeIt.next();
					Assertions.assertEquals(
						JsonNodeFactory.instance.objectNode()
							.put("id", user1.getId())
							.put("local", user1.isLocal())
							.put("username", user1.getUsername())
							.put("level", Long.valueOf(user1.getLevel())),
						userNode1,
						"Retrieved user doesn't match expected object");
					JsonNode userNode2 = userNodeIt.next();
					Assertions.assertEquals(
						JsonNodeFactory.instance.objectNode()
							.put("id", user2.getId())
							.put("local", user2.isLocal())
							.put("username", user2.getUsername())
							.put("level", Long.valueOf(user2.getLevel())),
						userNode2,
						"Retrieved user doesn't match expected object");
				}
			}


			@Test
			@DisplayName("Create user")
			@ResourceLock("DB")
			void createUser() throws IOException, InterruptedException {
				String password = "userpass";
				UiUser user = new UiUser("newuser", true, password);
				user.setLevel(UiUser.LEVEL_EXECUTEREADWRITE);
				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("username", user.getUsername())
					.put("password", password)
					.put("level", Long.valueOf(user.getLevel()))
					.put("local", user.isLocal());
				HttpResponse<JsonNode> response = apiClient.post("/users", data);
				Assertions.assertEquals(
					Response.Status.CREATED.getStatusCode(), response.statusCode(),
					"Not getting 201 response for created user");

				try (Session session = Database.getSession()) {
					UiUser newUser = session
						.createQuery("from net.netshot.netshot.aaa.UiUser u where u.username = :name", UiUser.class)
						.setParameter("name", user.getUsername())
						.uniqueResult();
					Assertions.assertEquals(user.getUsername(), newUser.getUsername(), "User not created as expected");
					Assertions.assertEquals(user.getLevel(), newUser.getLevel(), "User not created as expected");
					Assertions.assertEquals(user.isLocal(), newUser.isLocal(), "User not created as expected");
				}
			}

			@Test
			@DisplayName("Delete user")
			@ResourceLock("DB")
			void deleteUser() throws IOException, InterruptedException {
				UiUser user1 = new UiUser("user1", true, "pass1");
				user1.setLevel(UiUser.LEVEL_EXECUTEREADWRITE);
				UiUser user2 = new UiUser("user2", false, "pass2");
				user2.setLevel(UiUser.LEVEL_READONLY);
				try (Session session = Database.getSession()) {
					session.beginTransaction();
					session.persist(user1);
					session.persist(user2);
					session.getTransaction().commit();
				}
				HttpResponse<JsonNode> response = apiClient.delete(
					"/users/%d".formatted(user1.getId()));
				Assertions.assertEquals(
					Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
					"Not getting 204 response for user deletion");
				HttpResponse<JsonNode> listResponse = apiClient.get("/users");
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), listResponse.statusCode(),
					"Not getting 200 response for user listing");
				Assertions.assertEquals(1, listResponse.body().size(),
					"User list doesn't have 1 element");
				JsonNode userNode = listResponse.body().iterator().next();
				Assertions.assertEquals(
					JsonNodeFactory.instance.objectNode()
						.put("id", user2.getId())
						.put("local", user2.isLocal())
						.put("username", user2.getUsername())
						.put("level", Long.valueOf(user2.getLevel())),
					userNode,
					"Retrieved user doesn't match expected object");
			}

			@Test
			@DisplayName("Update user")
			@ResourceLock("DB")
			void updateUser() throws IOException, InterruptedException {
				UiUser user = new UiUser("user1", true, "pass1");
				user.setLevel(UiUser.LEVEL_EXECUTEREADWRITE);
				try (Session session = Database.getSession()) {
					session.beginTransaction();
					session.persist(user);
					session.getTransaction().commit();
				}
				UiUser targetUser = new UiUser("user2", true, "pass1");
				targetUser.setLevel(UiUser.LEVEL_READONLY);
				targetUser.setHashedPassword(user.getHashedPassword());
				targetUser.setId(user.getId());
				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("local", targetUser.isLocal())
					.put("username", targetUser.getUsername())
					.put("level", Long.valueOf(targetUser.getLevel()));
				HttpResponse<JsonNode> response = apiClient.put(
					"/users/%d".formatted(user.getId()), data);
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Not getting 200 response for user update");
				try (Session session = Database.getSession()) {
					UiUser dbUser = session.byId(UiUser.class)
						.load(targetUser.getId());
					Assertions.assertEquals(targetUser.getName(), dbUser.getName(),
						"User not updated as expected");
					Assertions.assertEquals(targetUser.getLevel(), dbUser.getLevel(),
						"User not updated as expected");
				}
			}

			@Test
			@DisplayName("Update remote user")
			@ResourceLock("DB")
			void updateRemoteUser() throws IOException, InterruptedException {
				UiUser user = new UiUser("user1", false, "pass1");
				user.setLevel(UiUser.LEVEL_OPERATOR);
				try (Session session = Database.getSession()) {
					session.beginTransaction();
					session.persist(user);
					session.getTransaction().commit();
				}
				UiUser targetUser = new UiUser("user2", true, "pass1");
				targetUser.setLevel(UiUser.LEVEL_OPERATOR);
				targetUser.setHashedPassword(user.getHashedPassword());
				targetUser.setId(user.getId());
				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("local", true)
					.put("username", targetUser.getUsername())
					.put("level", Long.valueOf(targetUser.getLevel()));
				{
					HttpResponse<JsonNode> response = apiClient.put(
						"/users/%d".formatted(user.getId()), data);
					Assertions.assertEquals(
						Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
						"Not getting 400 response for erroneous user update");
				}
				data.put("password", "pass2");
				{
					HttpResponse<JsonNode> response = apiClient.put(
						"/users/%d".formatted(user.getId()), data);
					Assertions.assertEquals(
						Response.Status.OK.getStatusCode(), response.statusCode(),
						"Not getting 200 response for user update");
				}
				try (Session session = Database.getSession()) {
					UiUser dbUser = session.byId(UiUser.class)
						.load(targetUser.getId());
					Assertions.assertEquals(targetUser.getName(), dbUser.getName(),
						"User not updated as expected");
					Assertions.assertEquals(targetUser.getLevel(), dbUser.getLevel(),
						"User not updated as expected");
					Assertions.assertEquals(targetUser.isLocal(), dbUser.isLocal(),
						"User not updated as expected");
				}
			}
		}

		@Nested
		@DisplayName("API token management API Tests")
		class ApiTokenTest {

			@Test
			@DisplayName("List tokens")
			@ResourceLock("DB")
			void listTokens() throws IOException, InterruptedException {
				// Tokens already created like before all tests
				HttpResponse<JsonNode> response = apiClient.get("/apitokens");
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Not getting 200 response for token list");
				Assertions.assertEquals(API_TOKENS.size(), response.body().size(),
					"User list doesn't have as many elements as API_TOKENS");
			}


			@Test
			@DisplayName("Create token")
			@ResourceLock("DB")
			void createToken() throws IOException, InterruptedException {
				String tokenString = "dZLV0zCn5gmbUJIebRHBxM4QjIAoNruK";
				ApiToken token = new ApiToken(
					"Test API token",
					tokenString,
					UiUser.LEVEL_READWRITE
				);
				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("description", token.getDescription())
					.put("token", tokenString)
					.put("level", token.getLevel());
				HttpResponse<JsonNode> response = apiClient.post("/apitokens", data);
				Assertions.assertEquals(
					Response.Status.CREATED.getStatusCode(), response.statusCode(),
					"Not getting 201 response for created API token");

				try (Session session = Database.getSession()) {
					ApiToken newToken = session
						.createQuery("from ApiToken t where t.description = :description", ApiToken.class)
						.setParameter("description", token.getDescription())
						.uniqueResult();
					Assertions.assertEquals(token.getLevel(), newToken.getLevel(),
						"Token not created as expected");
				}
			}

			@Test
			@DisplayName("Delete token")
			@ResourceLock("DB")
			void deleteToken() throws IOException, InterruptedException {
				ApiToken token1 = new ApiToken(
					"Temp token", "dZLV0zCn5gmbUJIebRHBxM4QjIAoNruK", UiUser.LEVEL_OPERATOR);
				try (Session session = Database.getSession()) {
					session.beginTransaction();
					session.persist(token1);
					session.getTransaction().commit();
				}
				HttpResponse<JsonNode> response = apiClient.delete(
					"/apitokens/%d".formatted(token1.getId()));
				Assertions.assertEquals(
					Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
					"Not getting 204 response for token deletion");
				HttpResponse<JsonNode> listResponse = apiClient.get("/apitokens");
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), listResponse.statusCode(),
					"Not getting 200 response for token listing");
				Assertions.assertEquals(API_TOKENS.size(), listResponse.body().size(),
					"User list doesn't have the right number of elements");
			}
		}

		@Nested
		@DisplayName("Webhook API Tests")
		class WebHookTest {

			// A real, parseable self-signed certificate (reused from DeviceDriverTest) -
			// only ever used here to exercise the CUSTOM_CA PEM-parsing validation, its
			// identity/expiry are irrelevant.
			private static final String VALID_CUSTOM_CA_CERTIFICATE =
				"-----BEGIN CERTIFICATE-----\n" +
				"MIID8TCCAtmgAwIBAgIUU0V+Vgqhs6fsS4MX6dbNYFn2Nk4wDQYJKoZIhvcNAQEL\n" +
				"BQAwgYcxCzAJBgNVBAYTAkZSMQwwCgYDVQQIDANJZEYxDjAMBgNVBAcMBVBhcmlz\n" +
				"MRAwDgYDVQQKDAdOZXRzaG90MQ0wCwYDVQQLDARUZXN0MRUwEwYDVQQDDAxBc3lu\n" +
				"Y09TIHRlc3QxIjAgBgkqhkiG9w0BCQEWE2NvbnRhY3RAbmV0c2hvdC5uZXQwHhcN\n" +
				"MjUwOTIwMTI1MjA5WhcNMjYwOTIwMTI1MjA5WjCBhzELMAkGA1UEBhMCRlIxDDAK\n" +
				"BgNVBAgMA0lkRjEOMAwGA1UEBwwFUGFyaXMxEDAOBgNVBAoMB05ldHNob3QxDTAL\n" +
				"BgNVBAsMBFRlc3QxFTATBgNVBAMMDEFzeW5jT1MgdGVzdDEiMCAGCSqGSIb3DQEJ\n" +
				"ARYTY29udGFjdEBuZXRzaG90Lm5ldDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC\n" +
				"AQoCggEBANNRfOo37uan0pRKrfSVpefgwbZwZ/J5MRtaeZcBqHvompZA8FTz6Yp1\n" +
				"amNuKehdKXae/gYCc83cNTwS7IBivz30iZt4/1VewIwKiZEVbK4DbsGQioseb3vO\n" +
				"gJoot7FzGrkoKnHn9n9cZmOA2zWiKE7SqVztg1MXcKnZhz5QE1mIG4Abz8dAYnM7\n" +
				"yRQ7DuDl9L7ESFQA8NcsML+zZ1q8kpQz82Oq10lolbnMolHCJx8jjAYnnMG/tK4I\n" +
				"Q6gUFPxS0gNsgoKnOe6OYMX7Z06hU5sibVc6jF+wCnVZLjEMuOhpCvLcZmbaYTmg\n" +
				"Cy7KpGF6ILyKKG83NlOYttHJBSA3eP8CAwEAAaNTMFEwHQYDVR0OBBYEFMABqRHf\n" +
				"1AxhRgx/dCyPTbbIkJYjMB8GA1UdIwQYMBaAFMABqRHf1AxhRgx/dCyPTbbIkJYj\n" +
				"MA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBADQ5fEVeNuw8RaFf\n" +
				"hv97+xQfej39CSDCI9foSSFr6LL624S4wyiSwWKrtg9lH+mxozg5q2NXUW+qWKPR\n" +
				"NDuOBY9SNVwkEoLqsdXbOIncuvwXBJ+5+/Md/IVjH5O88Gb6fEczhb0snBqp9+8v\n" +
				"jD75NoACvUucKL4J5A8M54mw+wZvRn64RhwUhigqQETRilUUsVAJITRdXvXtyYK0\n" +
				"Jtb1Bt8xVvOHzMi/n6I8yKdjuIrDiec4+XGaXK9DqNeZc4znTVXkx3Sr1HLs3BT4\n" +
				"SlvJ+y6SluxPcAoSFBGy4uC8DhJiWXpPaCDWPY1IE7aBiwMFCzdQrczWnp6vOKIa\n" +
				"Ahhsx70=\n" +
				"-----END CERTIFICATE-----\n";

			@AfterEach
			void cleanUpData() {
				try (Session session = Database.getSession()) {
					session.beginTransaction();
					session
						.createMutationQuery("delete from HookTrigger")
						.executeUpdate();
					session
						.createMutationQuery("delete from Hook")
						.executeUpdate();
					session.getTransaction().commit();
				}
			}

			private ObjectNode newWebhookData(String name, String url) {
				return JsonNodeFactory.instance.objectNode()
					.put("type", "Web")
					.put("name", name)
					.put("enabled", true)
					.put("action", WebHook.Action.POST_JSON.toString())
					.put("url", url);
			}

			private WebHook getWebHookByName(String name) {
				try (Session session = Database.getSession()) {
					return (WebHook) session
						.createQuery("from Hook h where h.name = :name", Hook.class)
						.setParameter("name", name)
						.uniqueResult();
				}
			}

			@Test
			@DisplayName("List webhooks")
			@ResourceLock("DB")
			void listWebhooks() throws IOException, InterruptedException {
				{
					HttpResponse<JsonNode> response = apiClient.get("/hooks");
					Assertions.assertEquals(
						Response.Status.OK.getStatusCode(), response.statusCode(),
						"Not getting 200 response for initial webhook list");
					Assertions.assertEquals(0, response.body().size(),
						"Webhook list is not empty");
				}
				Assertions.assertEquals(
					Response.Status.CREATED.getStatusCode(),
					apiClient.post("/hooks", newWebhookData("Webhook 1", "https://example.net/callback1")).statusCode(),
					"Not getting 201 response for created webhook 1");
				Assertions.assertEquals(
					Response.Status.CREATED.getStatusCode(),
					apiClient.post("/hooks", newWebhookData("Webhook 2", "https://example.net/callback2")).statusCode(),
					"Not getting 201 response for created webhook 2");
				{
					HttpResponse<JsonNode> response = apiClient.get("/hooks");
					Assertions.assertEquals(
						Response.Status.OK.getStatusCode(), response.statusCode(),
						"Not getting 200 response for webhook list");
					Assertions.assertEquals(2, response.body().size(),
						"Webhook list doesn't have 2 elements");
				}
			}

			@Test
			@DisplayName("Create webhook applies default HTTPS trust policy")
			@ResourceLock("DB")
			void createWebhookDefaultsTrustPolicy() throws IOException, InterruptedException {
				HttpResponse<JsonNode> response = apiClient.post("/hooks",
					newWebhookData("Webhook default trust", "https://example.net/callback"));
				Assertions.assertEquals(
					Response.Status.CREATED.getStatusCode(), response.statusCode(),
					"Not getting 201 response for created webhook");

				WebHook newHook = this.getWebHookByName("Webhook default trust");
				Assertions.assertNotNull(newHook, "Webhook not created as expected");
				Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, newHook.getHttpsCaTrustMode(),
					"Default HTTPS CA trust mode not applied");
				Assertions.assertNull(newHook.getHttpsCustomCaCertificate(),
					"Custom CA certificate should not be set by default");
			}

			@Test
			@DisplayName("Create webhook with explicit HTTPS trust policy")
			@ResourceLock("DB")
			void createWebhookExplicitTrustPolicy() throws IOException, InterruptedException {
				ObjectNode data = newWebhookData("Webhook explicit trust", "https://example.net/callback")
					.put("httpsCaTrustMode", HttpsCaTrustMode.TRUST_ANY.toString());
				HttpResponse<JsonNode> response = apiClient.post("/hooks", data);
				Assertions.assertEquals(
					Response.Status.CREATED.getStatusCode(), response.statusCode(),
					"Not getting 201 response for created webhook");

				WebHook newHook = this.getWebHookByName("Webhook explicit trust");
				Assertions.assertNotNull(newHook, "Webhook not created as expected");
				Assertions.assertEquals(HttpsCaTrustMode.TRUST_ANY, newHook.getHttpsCaTrustMode());
			}

			@Test
			@DisplayName("Reject webhook with invalid target URL")
			@ResourceLock("DB")
			void rejectInvalidUrl() throws IOException, InterruptedException {
				HttpResponse<JsonNode> response = apiClient.post("/hooks",
					newWebhookData("Webhook bad URL", "not a url"));
				Assertions.assertEquals(
					Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
					"Not getting 400 response for an invalid webhook URL");
				Assertions.assertEquals(
					NetshotBadRequestException.Reason.NETSHOT_INVALID_HOOK_WEB_URL.getCode(),
					response.body().get("errorCode").asInt());
			}

			@Test
			@DisplayName("Reject webhook with missing action")
			@ResourceLock("DB")
			void rejectMissingAction() throws IOException, InterruptedException {
				ObjectNode data = newWebhookData("Webhook no action", "https://example.net/callback");
				data.remove("action");
				HttpResponse<JsonNode> response = apiClient.post("/hooks", data);
				Assertions.assertEquals(
					Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
					"Not getting 400 response for a webhook with no action");
				Assertions.assertEquals(
					NetshotBadRequestException.Reason.NETSHOT_INVALID_HOOK_WEB.getCode(),
					response.body().get("errorCode").asInt());
			}

			@Test
			@DisplayName("Reject CUSTOM_CA webhook with no certificate")
			@ResourceLock("DB")
			void rejectCustomCaWithoutCertificate() throws IOException, InterruptedException {
				ObjectNode data = newWebhookData("Webhook missing CA cert", "https://example.net/callback")
					.put("httpsCaTrustMode", HttpsCaTrustMode.CUSTOM_CA.toString());
				HttpResponse<JsonNode> response = apiClient.post("/hooks", data);
				Assertions.assertEquals(
					Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
					"Not getting 400 response for a CUSTOM_CA webhook with no certificate");
				Assertions.assertEquals(
					NetshotBadRequestException.Reason.NETSHOT_INVALID_REQUEST_PARAMETER.getCode(),
					response.body().get("errorCode").asInt());
			}

			@Test
			@DisplayName("Reject CUSTOM_CA webhook with an unparseable certificate")
			@ResourceLock("DB")
			void rejectCustomCaWithInvalidCertificate() throws IOException, InterruptedException {
				ObjectNode data = newWebhookData("Webhook bad CA cert", "https://example.net/callback")
					.put("httpsCaTrustMode", HttpsCaTrustMode.CUSTOM_CA.toString())
					.put("httpsCustomCaCertificate", "not a certificate");
				HttpResponse<JsonNode> response = apiClient.post("/hooks", data);
				Assertions.assertEquals(
					Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
					"Not getting 400 response for a CUSTOM_CA webhook with an unparseable certificate");
				Assertions.assertEquals(
					NetshotBadRequestException.Reason.NETSHOT_INVALID_REQUEST_PARAMETER.getCode(),
					response.body().get("errorCode").asInt());
			}

			@Test
			@DisplayName("Create CUSTOM_CA webhook with a valid certificate")
			@ResourceLock("DB")
			void createWebhookWithValidCustomCa() throws IOException, InterruptedException {
				ObjectNode data = newWebhookData("Webhook valid CA cert", "https://example.net/callback")
					.put("httpsCaTrustMode", HttpsCaTrustMode.CUSTOM_CA.toString())
					.put("httpsCustomCaCertificate", VALID_CUSTOM_CA_CERTIFICATE);
				HttpResponse<JsonNode> response = apiClient.post("/hooks", data);
				Assertions.assertEquals(
					Response.Status.CREATED.getStatusCode(), response.statusCode(),
					"Not getting 201 response for a CUSTOM_CA webhook with a valid certificate");

				WebHook newHook = this.getWebHookByName("Webhook valid CA cert");
				Assertions.assertNotNull(newHook, "Webhook not created as expected");
				Assertions.assertEquals(HttpsCaTrustMode.CUSTOM_CA, newHook.getHttpsCaTrustMode());
				Assertions.assertEquals(VALID_CUSTOM_CA_CERTIFICATE, newHook.getHttpsCustomCaCertificate());
			}

			@Test
			@DisplayName("Reject duplicate webhook name")
			@ResourceLock("DB")
			void rejectDuplicateName() throws IOException, InterruptedException {
				Assertions.assertEquals(
					Response.Status.CREATED.getStatusCode(),
					apiClient.post("/hooks", newWebhookData("Duplicate webhook", "https://example.net/callback1")).statusCode(),
					"Not getting 201 response for the first webhook");
				HttpResponse<JsonNode> response = apiClient.post("/hooks",
					newWebhookData("Duplicate webhook", "https://example.net/callback2"));
				Assertions.assertEquals(
					Response.Status.CONFLICT.getStatusCode(), response.statusCode(),
					"Not getting 409 response for a duplicate webhook name");
				Assertions.assertEquals(
					NetshotBadRequestException.Reason.NETSHOT_DUPLICATE_HOOK.getCode(),
					response.body().get("errorCode").asInt());
			}

			@Test
			@DisplayName("Update webhook")
			@ResourceLock("DB")
			void updateWebhook() throws IOException, InterruptedException {
				HttpResponse<JsonNode> createResponse = apiClient.post("/hooks",
					newWebhookData("Webhook to update", "https://example.net/callback"));
				long id = createResponse.body().get("id").asLong();

				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("type", "Web")
					.put("name", "Webhook updated")
					.put("enabled", false)
					.put("action", WebHook.Action.POST_XML.toString())
					.put("url", "https://example.net/updated")
					.put("httpsCaTrustMode", HttpsCaTrustMode.CUSTOM_CA.toString())
					.put("httpsCustomCaCertificate", VALID_CUSTOM_CA_CERTIFICATE);

				{
					HttpResponse<JsonNode> response = apiClient.put(
						"/hooks/%d".formatted(UNKNOWN_ID), data);
					Assertions.assertEquals(
						Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
						"Not getting 400 response for an unknown webhook update");
				}
				{
					HttpResponse<JsonNode> response = apiClient.put(
						"/hooks/%d".formatted(id), data);
					Assertions.assertEquals(
						Response.Status.OK.getStatusCode(), response.statusCode(),
						"Not getting 200 response for webhook update");
				}

				WebHook updatedHook = this.getWebHookByName("Webhook updated");
				Assertions.assertNotNull(updatedHook, "Webhook not updated as expected");
				Assertions.assertFalse(updatedHook.isEnabled());
				Assertions.assertEquals(WebHook.Action.POST_XML, updatedHook.getAction());
				Assertions.assertEquals("https://example.net/updated", updatedHook.getUrl());
				Assertions.assertEquals(HttpsCaTrustMode.CUSTOM_CA, updatedHook.getHttpsCaTrustMode());
				Assertions.assertEquals(VALID_CUSTOM_CA_CERTIFICATE, updatedHook.getHttpsCustomCaCertificate());
			}

			@Test
			@DisplayName("Delete webhook")
			@ResourceLock("DB")
			void deleteWebhook() throws IOException, InterruptedException {
				HttpResponse<JsonNode> createResponse = apiClient.post("/hooks",
					newWebhookData("Webhook to delete", "https://example.net/callback"));
				long id = createResponse.body().get("id").asLong();

				{
					HttpResponse<JsonNode> response = apiClient.delete(
						"/hooks/%d".formatted(UNKNOWN_ID));
					Assertions.assertEquals(
						Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
						"Not getting 404 response for unknown webhook deletion");
				}
				{
					HttpResponse<JsonNode> response = apiClient.delete("/hooks/%d".formatted(id));
					Assertions.assertEquals(
						Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
						"Not getting 204 response for webhook deletion");
				}

				Assertions.assertNull(this.getWebHookByName("Webhook to delete"),
					"Webhook not deleted as expected");
			}
		}

	}

	@Nested
	@DisplayName("Device tests")
	@ResourceLock("DB")
	class DeviceTest {

		private String testDomainName = "Domain 1";
		private Domain testDomain;

		private static enum DeviceField {
			CONTACT(Device::getContact),
			CREATED_DATE(Device::getCreatedDate),
			CREATOR(Device::getCreator),
			DRIVER(Device::getDriver),
			EOL_DATE(Device::getEolDate),
			EOS_DATE(Device::getEosDate),
			FAMILY(Device::getFamily),
			LOCATION(Device::getLocation),
			MGMT_ADDRESS(Device::getMgmtAddress),
			NAME(Device::getName),
			NETWORK_CLASS(Device::getNetworkClass),
			SERIAL_NUMBER(Device::getSerialNumber),
			SOFTWARE_LEVEL(Device::getSoftwareLevel),
			SOFTWARE_VERSION(Device::getSoftwareVersion),
			STATUS(Device::getStatus),
			COMMENTS(Device::getComments);

			private Function<Device, ?> getter;

			private DeviceField(Function<Device, ?> getter) {
				this.getter = getter;
			}
		}

		private void createTestDomain() throws IOException {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				this.testDomain = new Domain(
					this.testDomainName, "Test Domain for devices",
					new Network4Address("10.1.1.1"),
					null
				);
				session.persist(this.testDomain);
				session.getTransaction().commit();
			}
		}

		private DeviceDriver getTestDriver() {
			return DeviceDriver.getDriverByName("CiscoIOS12");
		}

		private void assertDevicesEqual(Device d1, Device d2, DeviceField... ignoredFields) {
			List<DeviceField> iFields = Arrays.asList(ignoredFields);
			for (DeviceField field : DeviceField.values()) {
				if (!iFields.contains(field)) {
					Object v1 = field.getter.apply(d1);
					Object v2 = field.getter.apply(d2);
					if ((v1 == null && v2 != null) || (v1 != null && !v1.equals(v2))) {
						Assertions.fail("Passed devices are not equal, check field %s".formatted(field.name()));
					}
				}
			}
		}

		@BeforeAll
		static void loadDrivers() throws Exception {
			DeviceDriver.refreshDrivers();
		}

		@AfterEach
		void cleanUpData() {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session
					.createMutationQuery("delete from Device")
					.executeUpdate();
				session
					.createMutationQuery("delete from Domain")
					.executeUpdate();
				session.getTransaction().commit();
			}
		}

		@Test
		@DisplayName("List device types")
		void listDeviceTypes() throws Exception {
			List<DeviceDriver> drivers = DeviceDriver.getAllDrivers();
			HttpResponse<JsonNode> response = apiClient.get("/devicetypes");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for device type list");
			Assertions.assertEquals(
				drivers.size(), response.body().size(),
				"Not getting the right number of device drivers");
			DeviceDriver testDriver = this.getTestDriver();
			JsonNode testDriverNode = null;
			Iterator<JsonNode> driverIt = response.body().iterator();
			while (driverIt.hasNext()) {
				JsonNode driverNode = driverIt.next();
				if (driverNode.get("name").asText().equals(testDriver.getName())) {
					testDriverNode = driverNode;
				}
			}
			Assertions.assertNotNull(testDriverNode, "API didn't return test driver");

			ObjectNode targetData = JsonNodeFactory.instance.objectNode()
				.put("name", testDriver.getName())
				.put("author", testDriver.getAuthor())
				.put("description", testDriver.getDescription())
				.put("version", testDriver.getVersion())
				.put("priority", Long.valueOf(testDriver.getPriority()));
			for (AttributeDefinition attribute : testDriver.getAttributes()) {
				targetData.withArray("attributes").add(
					JsonNodeFactory.instance.objectNode()
						.put("type", attribute.getType().toString())
						.put("level", attribute.getLevel().toString())
						.put("name", attribute.getName())
						.put("title", attribute.getTitle())
						.put("comparable", attribute.isComparable())
						.put("searchable", attribute.isSearchable())
						.put("checkable", attribute.isCheckable())
				);
			}
			for (DriverProtocol protocol : testDriver.getProtocols()) {
				targetData.withArray("protocols")
					.add(protocol.toString());
			}
			for (String mode : testDriver.getCliMainModes()) {
				targetData.withArray("cliMainModes").add(mode);
			}
			targetData
				.put("sourceHash", testDriver.getSourceHash())
				.set("location",
					JsonNodeFactory.instance.objectNode()
						.put("type", testDriver.getLocation().getType().toString())
						.put("fileName", testDriver.getLocation().getFileName()));
			ObjectNode accessDefinitionsNode = targetData.putObject("accessDefinitions");
			for (var entry : testDriver.getAccessDefinitions().entrySet()) {
				DeviceDriver.AccessDefinition accessDef = entry.getValue();
				ObjectNode accessDefNode = accessDefinitionsNode.putObject(entry.getKey())
					.put("name", accessDef.getName())
					.put("protocol", accessDef.getProtocol().toString());
				if (accessDef.getDescription() == null) {
					accessDefNode.putNull("description");
				}
				else {
					accessDefNode.put("description", accessDef.getDescription());
				}
				accessDefNode.put("defaultPort", Long.valueOf(accessDef.getDefaultPort()));
				accessDefNode.put("group", accessDef.getGroup());
				accessDefNode.put("priority", Long.valueOf(accessDef.getPriority()));
			}
			targetData.putObject("options");
			Assertions.assertEquals(targetData, testDriverNode,
				"Retrieved device type doesn't match expected object");
		}

		@Test
		@DisplayName("List devices")
		@ResourceLock("DB")
		void listDevices() throws IOException, InterruptedException {
			{
				HttpResponse<JsonNode> response = apiClient.get("/devices");
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Not getting 200 response for initial list");
				Assertions.assertEquals(0, response.body().size(),
					"Device list is not empty");
			}
			this.createTestDomain();
			Device device1 = new Device(this.getTestDriver().getName(),
				"10.1.1.1", this.testDomain, "test");
			device1.setName("device1");
			Device device2 = new Device(this.getTestDriver().getName(),
				"10.1.1.2", this.testDomain, "test");
			device2.setName("device2");
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(device1);
				session.persist(device2);
				session.getTransaction().commit();
			}
			{
				HttpResponse<JsonNode> response = apiClient.get("/devices");
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Not getting 200 response for device list");
				Assertions.assertEquals(2, response.body().size(),
					"Device list doesn't have 2 elements");
				Iterator<JsonNode> deviceNodeIt = response.body().iterator();
				JsonNode deviceNode1 = deviceNodeIt.next();
				Assertions.assertEquals(
					JsonNodeFactory.instance.objectNode()
						.put("id", device1.getId())
						.put("name", device1.getName())
						.put("family", device1.getFamily())
						.put("mgmtAddress", device1.getMgmtAddress())
						.put("status", device1.getStatus().toString())
						.put("driver", device1.getDriver())
						.put("eol", device1.isEndOfLife())
						.put("eos", device1.isEndOfSale())
						.put("configCompliant", device1.isCompliant())
						.put("softwareLevel", device1.getSoftwareLevel().toString())
						.put("networkClass", device1.getNetworkClass().toString()),
					deviceNode1,
					"Retrieved device doesn't match expected object");
			}
		}

		@Test
		@DisplayName("Get device")
		@ResourceLock("DB")
		void getDevice() throws IOException, InterruptedException, MissingDeviceDriverException {
			this.createTestDomain();
			Device device1 = new Device(this.getTestDriver().getName(),
				"10.1.1.1", this.testDomain, "test");
			device1.setName("device1");
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(device1);
				session.getTransaction().commit();
			}
			{
				HttpResponse<JsonNode> response = apiClient.get("/devices/%d".formatted(UNKNOWN_ID));
				Assertions.assertEquals(
					Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
					"Not getting 404 response for nonexistent device");
			}
			{
				HttpResponse<JsonNode> response = apiClient.get("/devices/%d".formatted(device1.getId()));
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Not getting 200 response for device");
				JsonNode deviceNode1 = response.body();
				ObjectNode expectedNode1 = JsonNodeFactory.instance.objectNode()
					.put("id", device1.getId())
					.put("name", device1.getName())
					.put("networkClass", device1.getNetworkClass().toString())
					.put("family", device1.getFamily())
					.put("mgmtAddress", device1.getMgmtAddress())
					.put("cachedIpAddress", device1.getMgmtAddress())
					.put("status", device1.getStatus().toString())
					.put("driver", device1.getDriver())
					.put("realDeviceType", device1.getDeviceDriver().getDescription())
					.put("serialNumber", device1.getSerialNumber())
					.put("softwareVersion", device1.getSoftwareVersion())
					.put("softwareLevel", device1.getSoftwareLevel().toString())
					.put("contact", device1.getContact())
					.put("location", device1.getLocation())
					.put("creator", device1.getCreator())
					.put("changeDate", device1.getChangeDate().getTime())
					.put("createdDate", device1.getCreatedDate().getTime())
					.putNull("eolDate")
					.putNull("eolModule")
					.putNull("eosDate")
					.putNull("eosModule")
					.put("endOfLife", device1.isEndOfLife())
					.put("endOfSale", device1.isEndOfSale())
					.put("compliant", device1.isCompliant())
					.put("comments", device1.getComments());
				expectedNode1.putArray("ownerGroups");
				expectedNode1.putArray("attributes");
				expectedNode1.putArray("accesses");
				expectedNode1.putObject("options");
				expectedNode1.set("mgmtDomain",
					JsonNodeFactory.instance.objectNode()
						.put("id", device1.getMgmtDomain().getId())
						.put("name", device1.getMgmtDomain().getName())
						.put("changeDate", device1.getMgmtDomain().getChangeDate().getTime())
						.put("description", device1.getMgmtDomain().getDescription())
						.put("server4Address", device1.getMgmtDomain().getServer4Address().getIp())
						.putNull("server6Address"));
				Assertions.assertEquals(expectedNode1, deviceNode1,
					"Retrieved device doesn't match expected object");
			}
		}

		@Test
		@DisplayName("Create device")
		@ResourceLock("DB")
		void createDevice() throws IOException, InterruptedException {
			this.createTestDomain();
			Device device1 = new Device(this.getTestDriver().getName(),
				"10.1.1.1", this.testDomain, "test");
			ObjectNode data = JsonNodeFactory.instance.objectNode()
				.put("autoDiscover", false)
				.put("ipAddress", device1.getMgmtAddress())
				.put("domainId", this.testDomain.getId())
				.put("name", device1.getName())
				.put("deviceType", device1.getDriver());
			{
				HttpResponse<JsonNode> response = apiClient.post("/devices",
					data.deepCopy().put("domainId", UNKNOWN_ID));
				Assertions.assertEquals(
					Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
					"Not getting 400 response for invalid domain");
			}
			{
				HttpResponse<JsonNode> response = apiClient.post("/devices",
					data.deepCopy().put("deviceType", "NonExistingDriver"));
				Assertions.assertEquals(
					Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
					"Not getting 400 response for invalid domain");
			}
			{
				HttpResponse<JsonNode> response = apiClient.post("/devices", data);
				Assertions.assertEquals(
					Response.Status.CREATED.getStatusCode(), response.statusCode(),
					"Not getting 201 response for created device");
			}

			try (Session session = Database.getSession()) {
				Device newDevice = session
					.createQuery("from Device d", Device.class)
					.uniqueResult();
				device1.setId(newDevice.getId());
				this.assertDevicesEqual(device1, newDevice,
					DeviceField.CREATED_DATE, DeviceField.CREATOR);
			}
		}

		@Test
		@DisplayName("Create device with IPv6 management address")
		@ResourceLock("DB")
		void createDeviceWithIpv6Address() throws IOException, InterruptedException {
			this.createTestDomain();
			ObjectNode data = JsonNodeFactory.instance.objectNode()
				.put("autoDiscover", false)
				.put("ipAddress", "2001:db8::1")
				.put("domainId", this.testDomain.getId())
				.put("name", "device1")
				.put("deviceType", this.getTestDriver().getName());
			HttpResponse<JsonNode> response = apiClient.post("/devices", data);
			Assertions.assertEquals(
				Response.Status.CREATED.getStatusCode(), response.statusCode(),
				"Not getting 201 response for created device with an IPv6 management address");
			try (Session session = Database.getSession()) {
				Device newDevice = session
					.createQuery("from Device d", Device.class)
					.uniqueResult();
				Assertions.assertEquals("2001:db8::1", newDevice.getMgmtAddress(),
					"Management address not stored as entered");
				Assertions.assertEquals(InetAddress.getByName("2001:db8::1"), newDevice.getCachedIpAddress(),
					"Cached IP address not resolved from management address at creation");
			}
		}

		@Test
		@DisplayName("Create device with FQDN management address")
		@ResourceLock("DB")
		void createDeviceWithFqdnAddress() throws IOException, InterruptedException {
			this.createTestDomain();
			ObjectNode data = JsonNodeFactory.instance.objectNode()
				.put("autoDiscover", false)
				.put("ipAddress", "router1.example.com")
				.put("domainId", this.testDomain.getId())
				.put("name", "device1")
				.put("deviceType", this.getTestDriver().getName());
			HttpResponse<JsonNode> response = apiClient.post("/devices", data);
			Assertions.assertEquals(
				Response.Status.CREATED.getStatusCode(), response.statusCode(),
				"Not getting 201 response for created device with a hostname management address");
			try (Session session = Database.getSession()) {
				Device newDevice = session
					.createQuery("from Device d", Device.class)
					.uniqueResult();
				Assertions.assertEquals("router1.example.com", newDevice.getMgmtAddress(),
					"Management address not stored as entered");
			}
		}

		@Test
		@DisplayName("Reject device with malformed management address")
		@ResourceLock("DB")
		void createDeviceWithMalformedAddress() throws IOException, InterruptedException {
			this.createTestDomain();
			ObjectNode data = JsonNodeFactory.instance.objectNode()
				.put("autoDiscover", false)
				.put("ipAddress", "not an address!")
				.put("domainId", this.testDomain.getId())
				.put("name", "device1")
				.put("deviceType", this.getTestDriver().getName());
			HttpResponse<JsonNode> response = apiClient.post("/devices", data);
			Assertions.assertEquals(
				Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
				"Not getting 400 response for a malformed management address");
		}

		@Test
		@DisplayName("Create devices with same management address")
		@ResourceLock("DB")
		void createDevicesWithSameMgmtAddress() throws IOException, InterruptedException {
			this.createTestDomain();
			Device device1 = FakeDeviceFactory.getFakeCiscoIosDevice(this.testDomain, null, 0);
			ObjectNode data = JsonNodeFactory.instance.objectNode()
				.put("autoDiscover", false)
				.put("ipAddress", device1.getMgmtAddress())
				.put("domainId", this.testDomain.getId())
				.put("name", device1.getName())
				.put("deviceType", device1.getDriver());
			{
				HttpResponse<JsonNode> response = apiClient.post("/devices", data);
				Assertions.assertEquals(
					Response.Status.CREATED.getStatusCode(), response.statusCode(),
					"Not getting 201 response for created device");
			}
			try (Session session = Database.getSession()) {
				Device newDevice = session
					.createQuery("from Device d where d.mgmtAddress = :ip", Device.class)
					.setParameter("ip", device1.getMgmtAddress())
					.uniqueResult();
				device1.setId(newDevice.getId());
			}
			{
				HttpResponse<JsonNode> response = apiClient.post("/devices", data);
				Assertions.assertEquals(
					Response.Status.CONFLICT.getStatusCode(), response.statusCode(),
					"Not getting 409 response for duplicated device");
			}
			{
				// Disable first device
				ObjectNode editData = JsonNodeFactory.instance.objectNode()
					.put("enabled", false);
				HttpResponse<JsonNode> response = apiClient.put(
					"/devices/%d".formatted(device1.getId()), editData);
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Not getting 200 response for edited device");
			}
			{
				// New creation attempt
				HttpResponse<JsonNode> response = apiClient.post("/devices", data);
				Assertions.assertEquals(
					Response.Status.CREATED.getStatusCode(), response.statusCode(),
					"Not getting 201 response for second created device");
			}
			{
				// Try to re-enable first device
				ObjectNode editData = JsonNodeFactory.instance.objectNode()
					.put("enabled", true);
				HttpResponse<JsonNode> response = apiClient.put(
					"/devices/%d".formatted(device1.getId()), editData);
				Assertions.assertEquals(
					Response.Status.CONFLICT.getStatusCode(), response.statusCode(),
					"Not getting 409 response for edited device");
			}
		}

		@Test
		@DisplayName("Delete device")
		@ResourceLock("DB")
		void deleteDevice() throws IOException, InterruptedException {
			this.createTestDomain();
			Device device1 = new Device(this.getTestDriver().getName(),
				"10.1.1.1", this.testDomain, "test");
			device1.setName("device1");
			Device device2 = new Device(this.getTestDriver().getName(),
				"10.1.1.2", this.testDomain, "test");
			device2.setName("device2");
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(device1);
				session.persist(device2);
				session.getTransaction().commit();
			}
			{
				HttpResponse<JsonNode> response = apiClient.delete(
					"/devices/%d".formatted(UNKNOWN_ID));
				Assertions.assertEquals(
					Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
					"Not getting 404 response for unknown device deletion");
			}
			{
				HttpResponse<JsonNode> response = apiClient.delete(
					"/devices/%d".formatted(device1.getId()));
				Assertions.assertEquals(
					Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
					"Not getting 204 response for device deletion");
			}
			HttpResponse<JsonNode> listResponse = apiClient.get("/devices");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), listResponse.statusCode(),
				"Not getting 200 response for device listing");
			Assertions.assertEquals(1, listResponse.body().size(),
				"Device list doesn't have 1 element");
			JsonNode deviceNode = listResponse.body().iterator().next();
			Assertions.assertEquals(
				JsonNodeFactory.instance.objectNode()
					.put("id", device2.getId())
					.put("name", device2.getName())
					.put("family", device2.getFamily())
					.put("mgmtAddress", device2.getMgmtAddress())
					.put("status", device2.getStatus().toString())
					.put("driver", device2.getDriver())
					.put("eol", device2.isEndOfLife())
					.put("eos", device2.isEndOfSale())
					.put("configCompliant", device2.isCompliant())
					.put("softwareLevel", device2.getSoftwareLevel().toString())
					.put("networkClass", device2.getNetworkClass().toString()),
				deviceNode,
				"Retrieved device doesn't match expected object");
		}

		@Test
		@DisplayName("Deleting a device cascades to its DeviceAccess rows and their owned specific credential, but not a referenced global one")
		@ResourceLock("DB")
		void deleteDeviceCascadesAccessesAndOwnedCredentialOnly() throws IOException, InterruptedException {
			this.createTestDomain();
			Device device1 = new Device(this.getTestDriver().getName(),
				"10.1.1.1", this.testDomain, "test");
			device1.setName("device1");
			DeviceCredentialSet globalCred = new DeviceSshAccount("admin", "admin", null, "globalCredForCascadeTest");
			DeviceCredentialSet specificCred = new DeviceSshAccount("admin", "admin", null,
				DeviceCredentialSet.generateSpecificName());
			specificCred.setDeviceSpecific(true);
			long globalCredId;
			long specificCredId;
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(globalCred);
				DeviceAccess sshAccess = new DeviceAccess(device1, "ssh");
				sshAccess.setGlobalCredentialSet(globalCred);
				device1.getAccesses().add(sshAccess);
				DeviceAccess telnetAccess = new DeviceAccess(device1, "telnet");
				telnetAccess.setSpecificCredentialSet(specificCred);
				device1.getAccesses().add(telnetAccess);
				session.persist(device1);
				session.getTransaction().commit();
				globalCredId = globalCred.getId();
				specificCredId = specificCred.getId();
			}

			HttpResponse<JsonNode> response = apiClient.delete("/devices/%d".formatted(device1.getId()));
			Assertions.assertEquals(
				Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
				"Not getting 204 response for device deletion");

			try (Session session = Database.getSession()) {
				Long deviceAccessCount = session
					.createQuery("select count(a) from DeviceAccess a where a.key.device.id = :id", Long.class)
					.setParameter("id", device1.getId())
					.uniqueResult();
				Assertions.assertEquals(0L, deviceAccessCount,
					"DeviceAccess rows should be cascade-deleted along with the device");
				Assertions.assertNull(session.get(DeviceCredentialSet.class, specificCredId),
					"The owned specific credential set should be cascade-deleted along with its DeviceAccess");
				Assertions.assertNotNull(session.get(DeviceCredentialSet.class, globalCredId),
					"A referenced global credential set must survive device deletion");
			}
		}

		@Test
		@DisplayName("Reject a device access that sets both a global and a specific credential set")
		@ResourceLock("DB")
		void deviceAccessRejectsBothCredentialRelations() throws IOException, InterruptedException {
			this.createTestDomain();
			ObjectNode data = JsonNodeFactory.instance.objectNode()
				.put("autoDiscover", false)
				.put("ipAddress", "10.1.1.1")
				.put("domainId", this.testDomain.getId())
				.put("name", "device1")
				.put("deviceType", this.getTestDriver().getName());
			ObjectNode accessNode = JsonNodeFactory.instance.objectNode()
				.put("accessName", "ssh");
			accessNode.putObject("globalCredentialSet").put("type", "SSH").put("id", 1);
			accessNode.putObject("specificCredentialSet").put("type", "SSH")
				.put("username", "admin").put("password", "admin");
			data.withArray("accesses").add(accessNode);

			HttpResponse<JsonNode> response = apiClient.post("/devices", data);
			Assertions.assertEquals(
				Response.Status.PRECONDITION_FAILED.getStatusCode(), response.statusCode(),
				"Not getting the expected error status for a device access with both credential relations set");
		}

		@Test
		@DisplayName("Reject a device access pinned to a global credential set of the wrong family")
		@ResourceLock("DB")
		void deviceAccessRejectsMismatchedCredentialClass() throws IOException, InterruptedException {
			this.createTestDomain();
			DeviceCredentialSet snmpCred = new DeviceSnmpv2cCommunity("public", "snmpCredForMismatchTest");
			long snmpCredId;
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(snmpCred);
				session.getTransaction().commit();
				snmpCredId = snmpCred.getId();
			}
			ObjectNode data = JsonNodeFactory.instance.objectNode()
				.put("autoDiscover", false)
				.put("ipAddress", "10.1.1.1")
				.put("domainId", this.testDomain.getId())
				.put("name", "device1")
				.put("deviceType", this.getTestDriver().getName());
			ObjectNode accessNode = JsonNodeFactory.instance.objectNode()
				.put("accessName", "ssh");
			accessNode.putObject("globalCredentialSet").put("type", "SNMP v2").put("id", snmpCredId);
			data.withArray("accesses").add(accessNode);

			HttpResponse<JsonNode> response = apiClient.post("/devices", data);
			Assertions.assertEquals(
				Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
				"Not getting 400 for a credential class mismatched with the access's declared family");
		}

		@Test
		@DisplayName("Edit device")
		@ResourceLock("DB")
		void editDevice() throws IOException, InterruptedException {
			this.createTestDomain();
			Device device1 = FakeDeviceFactory.getFakeCiscoIosDevice(this.testDomain, null, 1);
			Device device2 = FakeDeviceFactory.getFakeCiscoIosDevice(this.testDomain, null, 2);
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(device1);
				session.persist(device2);
				session.getTransaction().commit();
			}
			{
				ObjectNode editData = JsonNodeFactory.instance.objectNode()
					.put("enabled", false);
				HttpResponse<JsonNode> response = apiClient.put(
					"/devices/%d".formatted(UNKNOWN_ID), editData);
				Assertions.assertEquals(
					Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
					"Not getting 404 response for unknown edited device");
			}
			{
				ObjectNode editData = JsonNodeFactory.instance.objectNode()
					.put("enabled", false);
				HttpResponse<JsonNode> response = apiClient.put(
					"/devices/%d".formatted(device1.getId()), editData);
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Not getting 200 response for edited device");

				try (Session session = Database.getSession()) {
					Device editedDevice = session
						.createQuery("from Device d where d.id = :id", Device.class)
						.setParameter("id", device1.getId())
						.uniqueResult();
					Assertions.assertEquals(Device.Status.DISABLED, editedDevice.getStatus(),
						"The edited device is not disabled");
					this.assertDevicesEqual(device1, editedDevice, DeviceField.STATUS);
				}
			}
			{
				ObjectNode editData = JsonNodeFactory.instance.objectNode()
					.put("enabled", true);
				HttpResponse<JsonNode> response = apiClient.put(
					"/devices/%d".formatted(device1.getId()), editData);
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Not getting 200 response for edited device");

				try (Session session = Database.getSession()) {
					Device editedDevice = session
						.createQuery("from Device d where d.id = :id", Device.class)
						.setParameter("id", device1.getId())
						.uniqueResult();
					Assertions.assertEquals(Device.Status.INPRODUCTION, editedDevice.getStatus(),
						"The edited device is not enabled");
					this.assertDevicesEqual(device1, editedDevice);
				}
			}
			{
				final String comments = "TEST EDIT COMMENT";
				ObjectNode editData = JsonNodeFactory.instance.objectNode()
					.put("comments", comments);
				HttpResponse<JsonNode> response = apiClient.put(
					"/devices/%d".formatted(device1.getId()), editData);
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Not getting 200 response for edited device");

				try (Session session = Database.getSession()) {
					Device editedDevice = session
						.createQuery("from Device d where d.id = :id", Device.class)
						.setParameter("id", device1.getId())
						.uniqueResult();
					Assertions.assertEquals(comments, editedDevice.getComments(),
						"The edited device comments are not correct");
					this.assertDevicesEqual(device1, editedDevice, DeviceField.COMMENTS);
				}
			}
		}

	}

	@Nested
	@DisplayName("Compliance tests")
	@ResourceLock("DB")
	class ComplianceTest {

		@AfterEach
		void cleanUpData() {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.createMutationQuery("delete from Rule").executeUpdate();
				session.createMutationQuery("delete from Policy").executeUpdate();
				session.createMutationQuery("delete from DeviceGroup").executeUpdate();
				session.getTransaction().commit();
			}
		}

		@Test
		@DisplayName("List policies")
		void listPolicies() throws IOException, InterruptedException {
			{
				HttpResponse<JsonNode> response = apiClient.get("/policies");
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Not getting 200 response for initial policy list");
				Assertions.assertEquals(0, response.body().size(),
					"Policy list is not empty");
			}

			StaticDeviceGroup group = new StaticDeviceGroup("Test group");
			Policy policy;
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(group);
				Set<DeviceGroup> groups = new HashSet<>();
				groups.add(group);
				policy = new Policy("Test policy", groups);
				session.persist(policy);
				session.getTransaction().commit();
			}

			HttpResponse<JsonNode> response = apiClient.get("/policies");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for policy list");
			Assertions.assertEquals(1, response.body().size(),
				"Policy list doesn't have 1 element");

			JsonNode policyNode = response.body().get(0);
			Assertions.assertEquals(policy.getId(), policyNode.get("id").asLong(),
				"The returned policy doesn't have the right ID");
			Assertions.assertEquals(policy.getName(), policyNode.get("name").asText(),
				"The returned policy doesn't have the right name");
			Assertions.assertEquals(1, policyNode.get("targetGroups").size(),
				"The returned policy doesn't have the right number of target groups");
			Assertions.assertEquals(group.getId(), policyNode.get("targetGroups").get(0).get("id").asLong(),
				"The returned policy target group doesn't have the right ID");
			Assertions.assertFalse(policyNode.has("rules"),
				"The policy list response should not embed the rules of each policy");
		}

		@Test
		@DisplayName("List rules")
		void listRules() throws IOException, InterruptedException {
			{
				HttpResponse<JsonNode> response = apiClient.get("/rules");
				Assertions.assertEquals(
					Response.Status.OK.getStatusCode(), response.statusCode(),
					"Not getting 200 response for initial rule list");
				Assertions.assertEquals(0, response.body().size(),
					"Rule list is not empty");
			}

			Policy policy;
			TextRule textRule;
			JavaScriptRule jsRule;
			PythonRule pyRule;
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				policy = new Policy("Test policy", new HashSet<>());
				session.persist(policy);
				textRule = new TextRule("Text rule", policy);
				jsRule = new JavaScriptRule("JS rule", policy);
				jsRule.setEnabled(true);
				pyRule = new PythonRule("Python rule", policy);
				session.persist(textRule);
				session.persist(jsRule);
				session.persist(pyRule);
				session.getTransaction().commit();
			}

			HttpResponse<JsonNode> response = apiClient.get("/rules");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for rule list");
			Assertions.assertEquals(3, response.body().size(),
				"Rule list doesn't have 3 elements");

			Map<Long, JsonNode> rulesById = new HashMap<>();
			for (JsonNode ruleNode : response.body()) {
				rulesById.put(ruleNode.get("id").asLong(), ruleNode);
			}

			JsonNode textNode = rulesById.get(textRule.getId());
			Assertions.assertNotNull(textNode, "The text rule is missing from the rule list");
			Assertions.assertEquals("Text rule", textNode.get("name").asText());
			Assertions.assertEquals("TextRule", textNode.get("type").asText());
			Assertions.assertFalse(textNode.get("enabled").asBoolean());
			Assertions.assertEquals(policy.getId(), textNode.get("policyId").asLong());
			Assertions.assertFalse(textNode.has("script"),
				"The rule list response should not expose the heavier rule fields");

			JsonNode jsNode = rulesById.get(jsRule.getId());
			Assertions.assertNotNull(jsNode, "The JavaScript rule is missing from the rule list");
			Assertions.assertEquals("JS rule", jsNode.get("name").asText());
			Assertions.assertEquals("JavaScriptRule", jsNode.get("type").asText());
			Assertions.assertTrue(jsNode.get("enabled").asBoolean());
			Assertions.assertEquals(policy.getId(), jsNode.get("policyId").asLong());
			Assertions.assertFalse(jsNode.has("script"),
				"The rule list response should not expose the heavier rule fields");

			JsonNode pyNode = rulesById.get(pyRule.getId());
			Assertions.assertNotNull(pyNode, "The Python rule is missing from the rule list");
			Assertions.assertEquals("Python rule", pyNode.get("name").asText());
			Assertions.assertEquals("PythonRule", pyNode.get("type").asText());
			Assertions.assertFalse(pyNode.get("enabled").asBoolean());
			Assertions.assertEquals(policy.getId(), pyNode.get("policyId").asLong());
		}

	}


	@Nested
	@DisplayName("Report tests")
	@ResourceLock("DB")
	class ReportTest {

		private String testDomainName = "Domain 1";
		private Domain testDomain;
		private List<Device> testDevices;

		private void createTestDomain() throws IOException {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				this.testDomain = new Domain(
					this.testDomainName, "Test Domain for devices",
					new Network4Address("10.1.1.1"),
					null
				);
				session.persist(this.testDomain);
				session.getTransaction().commit();
			}
		}

		private void createTestDevices() {
			this.testDevices = new ArrayList<>();
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				for (int i = 0; i < 1000; i++) {
					Device device = FakeDeviceFactory.getFakeCiscoIosDevice(this.testDomain, null, i);
					this.testDevices.add(device);
					session.persist(device);
				}
				session.getTransaction().commit();
			}
		}

		@BeforeAll
		static void loadDrivers() throws Exception {
			DeviceDriver.refreshDrivers();
		}

		@AfterEach
		void cleanUpData() {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session
					.createMutationQuery("delete from Device")
					.executeUpdate();
				session
					.createMutationQuery("delete from DeviceCredentialSet cs where cs.deviceSpecific is true")
					.executeUpdate();
				session
					.createMutationQuery("delete from Domain")
					.executeUpdate();
				session.getTransaction().commit();
			}
		}

		@Test
		@DisplayName("Export data test")
		@ResourceLock("DB")
		void exportDataTest() throws IOException, InterruptedException {
			this.createTestDomain();
			this.createTestDevices();
			apiClient.setMediaType(MediaType.WILDCARD_TYPE);
			{
				InputStream xlsStream = apiClient.download("/reports/export?format=xlsx").body();
				try (XSSFWorkbook wb = new XSSFWorkbook(xlsStream)) {
					Assertions.assertEquals(wb.getNumberOfSheets(), 2);
					XSSFSheet deviceSheet = wb.getSheet("Devices");
					Assertions.assertEquals(this.testDevices.size(), deviceSheet.getLastRowNum(),
						"Excel report doesn't have the expected number of lines in Devices sheet");
				}
			}
			{
				InputStream xlsStream = apiClient.download("/reports/export?interfaces=true").body();
				try (XSSFWorkbook wb = new XSSFWorkbook(xlsStream)) {
					Assertions.assertEquals(wb.getNumberOfSheets(), 3);
					XSSFSheet deviceSheet = wb.getSheet("Devices");
					Assertions.assertEquals(deviceSheet.getLastRowNum(), this.testDevices.size(),
						"Excel report doesn't have the expected number of lines in Devices sheet");
					XSSFSheet intfSheet = wb.getSheet("Interfaces");
					Assertions.assertEquals(this.testDevices.size() * 4, intfSheet.getLastRowNum(),
						"Excel report doesn't have the expected number of lines in Interfaces sheet");
				}
			}
			{
				InputStream xlsStream = apiClient.download("/reports/export?inventory=true").body();
				try (XSSFWorkbook wb = new XSSFWorkbook(xlsStream)) {
					Assertions.assertEquals(wb.getNumberOfSheets(), 3);
					XSSFSheet deviceSheet = wb.getSheet("Devices");
					Assertions.assertEquals(deviceSheet.getLastRowNum(), this.testDevices.size(),
						"Excel report doesn't have the expected number of lines in Devices sheet");
					XSSFSheet intfSheet = wb.getSheet("Inventory");
					Assertions.assertEquals(this.testDevices.size() * 2, intfSheet.getLastRowNum(),
						"Excel report doesn't have the expected number of lines in Inventory sheet");
				}
			}
		}

	}

	@Nested
	@DisplayName("Script tests")
	@ResourceLock("DB")
	class ScriptTest {

		private final String testScriptContent =
			"function run(cli, device) {\n" +
			"   cli.macro(\"configure\");\n" +
			"   cli.command(\"no ip domain-lookup\")\n" +
			"   cli.macro(\"end\");\n" +
			"   cli.macro(\"save\");\n" +
			"}";

		@BeforeAll
		static void loadDrivers() throws Exception {
			DeviceDriver.refreshDrivers();
		}

		private DeviceDriver getTestDriver() {
			return DeviceDriver.getDriverByName("CiscoIOS12");
		}

		@AfterEach
		void cleanUpData() {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session
					.createMutationQuery("delete from DeviceJsScript")
					.executeUpdate();
				session.getTransaction().commit();
			}
		}

		@Test
		@DisplayName("Add script")
		@ResourceLock("DB")
		void addScript() throws IOException, InterruptedException {
			ObjectNode data = JsonNodeFactory.instance.objectNode()
				.put("name", "Test Script")
				.put("deviceDriver", this.getTestDriver().getName())
				.put("script", testScriptContent);
			{
				HttpResponse<JsonNode> response = apiClient.post("/scripts",
					data.deepCopy().put("deviceDriver", "NonExistingDriver"));
				Assertions.assertEquals(
					Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
					"Not getting 400 response for invalid device driver");
			}
			{
				HttpResponse<JsonNode> response = apiClient.post("/scripts",
					data.deepCopy().put("name", ""));
				Assertions.assertEquals(
					Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
					"Not getting 400 response for invalid script name");
			}
			{
				HttpResponse<JsonNode> response = apiClient.post("/scripts", data);
				Assertions.assertEquals(
					Response.Status.CREATED.getStatusCode(), response.statusCode(),
					"Not getting 201 response for created script");
			}
			try (Session session = Database.getSession()) {
				DeviceJsScript newScript = session
					.createQuery("from DeviceJsScript s where s.name = :name", DeviceJsScript.class)
					.setParameter("name", "Test Script")
					.uniqueResult();
				Assertions.assertNotNull(newScript, "Script not created as expected");
				Assertions.assertEquals(this.getTestDriver().getName(), newScript.getDeviceDriver(),
					"Script not created as expected");
				Assertions.assertEquals(testScriptContent, newScript.getScript(),
					"Script not created as expected");
			}
			{
				// Duplicate script name in the same folder
				HttpResponse<JsonNode> response = apiClient.post("/scripts", data);
				Assertions.assertEquals(
					Response.Status.CONFLICT.getStatusCode(), response.statusCode(),
					"Not getting 400 response for duplicated script name");
			}
			{
				// Same script name but different folder is allowed
				HttpResponse<JsonNode> response = apiClient.post("/scripts",
					data.deepCopy().put("folder", "Other folder"));
				Assertions.assertEquals(
					Response.Status.CREATED.getStatusCode(), response.statusCode(),
					"Not getting 201 response for created script with same name in another folder");
			}
		}

		@Test
		@DisplayName("Get script")
		@ResourceLock("DB")
		void getScript() throws IOException, InterruptedException {
			DeviceJsScript script = new DeviceJsScript(
				"Test Script", this.getTestDriver().getName(), testScriptContent, "author1");
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(script);
				session.getTransaction().commit();
			}
			HttpResponse<JsonNode> response = apiClient.get("/scripts/%d".formatted(script.getId()));
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for script");
			JsonNode scriptNode = response.body();
			Assertions.assertEquals(script.getName(), scriptNode.get("name").asText(),
				"Retrieved script doesn't match expected object");
			Assertions.assertEquals(script.getDeviceDriver(), scriptNode.get("deviceDriver").asText(),
				"Retrieved script doesn't match expected object");
			Assertions.assertEquals(script.getScript(), scriptNode.get("script").asText(),
				"Retrieved script doesn't match expected object");
			Assertions.assertEquals(script.getAuthor(), scriptNode.get("author").asText(),
				"Retrieved script doesn't match expected object");
		}

		@Test
		@DisplayName("Delete script")
		@ResourceLock("DB")
		void deleteScript() throws IOException, InterruptedException {
			DeviceJsScript script = new DeviceJsScript(
				"Test Script", this.getTestDriver().getName(), testScriptContent, "author1");
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(script);
				session.getTransaction().commit();
			}
			{
				HttpResponse<JsonNode> response = apiClient.delete(
					"/scripts/%d".formatted(UNKNOWN_ID));
				Assertions.assertEquals(
					Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
					"Not getting 404 response for unknown script deletion");
			}
			{
				HttpResponse<JsonNode> response = apiClient.delete(
					"/scripts/%d".formatted(script.getId()));
				Assertions.assertEquals(
					Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
					"Not getting 204 response for script deletion");
			}
			try (Session session = Database.getSession()) {
				DeviceJsScript deletedScript = session.get(DeviceJsScript.class, script.getId());
				Assertions.assertNull(deletedScript, "Script not deleted as expected");
			}
		}

	}

}
