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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.UserRepository;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.v1.V1Client;
import net.netshot.netshot.testing.api.v1.V1Client.Answer;
import net.netshot.netshot.testing.api.v1.V1Endpoints;

@ApiTest
@V1Endpoints({
	"POST /auth/session",
	"GET /auth/session",
	"DELETE /auth/session",
	"GET /auth/me",
	"PATCH /auth/me"
})
@DisplayName("v1 authentication (session, current user, own password)")
@ResourceLock("DB")
class V1AuthResourceTest {

	private static final String USERNAME = "sessionuser";
	private static final String PASSWORD = "Sessionpassword1";

	private TestServer server;
	private V1Client anonymous;
	private V1Client admin;
	private Properties originalConfig;

	@BeforeEach
	void setUp(TestServer testServer) {
		this.server = testServer;
		this.anonymous = new V1Client(testServer, null);
		this.admin = new V1Client(testServer, UiUser.LEVEL_ADMIN);
		this.originalConfig = TestConfig.properties();
		this.createUser(USERNAME, PASSWORD, UiUser.LEVEL_READWRITE, null);
	}

	@AfterEach
	void restoreConfig() {
		TestConfig.init(this.originalConfig);
	}

	private void createUser(String username, String password, int level, Instant passwordDate) {
		UiUser user = UiUser.withPassword(username, true, level, password);
		UiUser stored = passwordDate == null ? user : user.withLastPasswordChangeDate(passwordDate);
		TestWiring.get().transactions().run(dsl -> new UserRepository().add(dsl, stored));
	}

	private static String credentials(String username, String password) {
		return """
			{"username": "%s", "password": "%s"}""".formatted(username, password);
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

	private Answer doLogin() throws IOException, InterruptedException {
		Answer answer = this.anonymous.post("/auth/session", credentials(USERNAME, PASSWORD));
		Assertions.assertEquals(201, answer.status(), answer.toString());
		return answer;
	}

	@Test
	@DisplayName("Log in: 201, session cookie, Location and the session")
	void login() throws Exception {
		Answer answer = this.doLogin();
		Assertions.assertEquals("/api/v1/auth/session", answer.header("Location"));
		String cookie = sessionCookie(answer);
		Assertions.assertNotNull(cookie);
		Assertions.assertTrue(answer.headers().allValues("Set-Cookie").stream().anyMatch(c -> c.contains("HttpOnly")));
		JsonNode session = answer.json();
		Assertions.assertEquals("/api/v1/auth/session", session.get("url").asText());
		Assertions.assertTrue(session.get("creationDate").asText().endsWith("Z"));
		Assertions.assertTrue(session.get("maxIdleTime").asInt() > 0);
		JsonNode user = session.get("user");
		Assertions.assertEquals(USERNAME, user.get("username").asText());
		Assertions.assertEquals("USER", user.get("kind").asText());
		Assertions.assertEquals("SESSION", user.get("authMethod").asText());
		Assertions.assertTrue(user.get("local").asBoolean());
		Assertions.assertEquals(UiUser.LEVEL_READWRITE, user.get("level").asInt());
		Assertions.assertEquals("read-write", user.get("role").asText());
		Assertions.assertTrue(user.get("url").asText().startsWith("/api/v1/users/"));
		// No secret anywhere
		Assertions.assertFalse(answer.body().contains(PASSWORD));
		Assertions.assertFalse(answer.body().toLowerCase().contains("hash"));
		Assertions.assertFalse(answer.body().contains(cookie.substring(cookie.indexOf('=') + 1)));
	}

	@Test
	@DisplayName("The session cookie authenticates the requests; GET /auth/session and GET /auth/me")
	void sessionCookie() throws Exception {
		String cookie = sessionCookie(this.doLogin());

		Answer session = this.anonymous.get("/auth/session", "Cookie", cookie);
		Assertions.assertEquals(200, session.status(), session.toString());
		Assertions.assertEquals(USERNAME, session.json().get("user").get("username").asText());

		Answer me = this.anonymous.get("/auth/me", "Cookie", cookie);
		Assertions.assertEquals(200, me.status(), me.toString());
		Assertions.assertEquals("SESSION", me.json().get("authMethod").asText());
		Assertions.assertEquals(USERNAME, me.json().get("display").asText());

		// The session is a real one: other resources accept it, within the level
		Assertions.assertEquals(200, this.anonymous.get("/domains", "Cookie", cookie).status());
		Assertions.assertEquals(403, this.anonymous.get("/users", "Cookie", cookie).status());

		// Selection works on the current user
		JsonNode partial = this.anonymous.get("/auth/me?fields=username,level", "Cookie", cookie).json();
		Assertions.assertEquals(3, partial.size());
	}

	@Test
	@DisplayName("A new session is created on login: the previous session ID is dropped (session fixation)")
	void sessionRenewed() throws Exception {
		String first = sessionCookie(this.doLogin());
		Answer second = this.anonymous.post("/auth/session", credentials(USERNAME, PASSWORD), "Cookie", first);
		Assertions.assertEquals(201, second.status(), second.toString());
		String renewed = sessionCookie(second);
		Assertions.assertNotNull(renewed);
		Assertions.assertNotEquals(first, renewed, "The session ID wasn't renewed on login");
		Assertions.assertEquals(401, this.anonymous.get("/auth/session", "Cookie", first).status(),
			"The session existing before login is still valid");
		Assertions.assertEquals(200, this.anonymous.get("/auth/session", "Cookie", renewed).status());
	}

	@Test
	@DisplayName("Wrong credentials: 401, and the existing session is dropped (as in v0)")
	void wrongCredentials() throws Exception {
		String cookie = sessionCookie(this.doLogin());

		Answer wrong = this.anonymous.post("/auth/session", credentials(USERNAME, "wrongpassword"), "Cookie", cookie);
		Assertions.assertEquals(401, wrong.status(), wrong.toString());
		Assertions.assertEquals("NETSHOT_AUTHENTICATION_FAILED", wrong.json().get("code").asText());
		// The dropped session may be expired by a Set-Cookie: no cookie of the answer gives a session
		String after = sessionCookie(wrong);
		if (after != null) {
			Assertions.assertEquals(401, this.anonymous.get("/auth/session", "Cookie", after).status(),
				"No session is created by a failed login");
		}
		Assertions.assertEquals(401, this.anonymous.get("/auth/session", "Cookie", cookie).status(),
			"A failed login drops the existing session");
		Assertions.assertEquals("no-store", wrong.header("Cache-Control"));

		Answer unknown = this.anonymous.post("/auth/session", credentials("nobody", "wrongpassword"));
		Assertions.assertEquals(401, unknown.status(), unknown.toString());
		Assertions.assertEquals(wrong.json().get("detail").asText(), unknown.json().get("detail").asText(),
			"The answer must not tell whether the user exists");
		Assertions.assertNull(sessionCookie(unknown));
	}

	@Test
	@DisplayName("Invalid login bodies are refused with a 400")
	void invalidBodies() throws Exception {
		Assertions.assertEquals(400, this.anonymous.post("/auth/session", "{}").status());
		Assertions.assertEquals(400, this.anonymous.post("/auth/session", "{\"username\": \"a\"}").status());
		Assertions.assertEquals(400, this.anonymous.post("/auth/session", "{\"password\": \"a\"}").status());
		Answer both = this.anonymous.post("/auth/session",
			"{\"username\": \"a\", \"password\": \"b\", \"authorizationCode\": \"c\", \"redirectUri\": \"https://x/\"}");
		Assertions.assertEquals(400, both.status(), both.toString());
		Assertions.assertEquals(400, this.anonymous.post("/auth/session", "{\"authorizationCode\": \"c\"}").status());
		Answer code = this.anonymous.post("/auth/session",
			"{\"authorizationCode\": \"c\", \"redirectUri\": \"https://x/\", \"newPassword\": \"x\"}");
		Assertions.assertEquals(400, code.status(), code.toString());
		Answer unknown = this.anonymous.post("/auth/session", "{\"username\": \"a\", \"password\": \"b\", \"color\": \"red\"}");
		Assertions.assertEquals(400, unknown.status(), unknown.toString());
		// OIDC is not configured: the code can't be validated
		Answer unconfigured = this.anonymous.post("/auth/session", "{\"authorizationCode\": \"c\", \"redirectUri\": \"https://x/\"}");
		Assertions.assertEquals(401, unconfigured.status(), unconfigured.toString());
	}

	@Test
	@DisplayName("Log out: 204, the session is dropped; idempotent without session")
	void logout() throws Exception {
		String cookie = sessionCookie(this.doLogin());
		Answer out = this.anonymous.delete("/auth/session", "Cookie", cookie);
		Assertions.assertEquals(204, out.status(), out.toString());
		Assertions.assertNull(out.header("X-OIDC-EndSessionEndpoint"));
		Assertions.assertEquals(401, this.anonymous.get("/auth/session", "Cookie", cookie).status());
		Assertions.assertEquals(401, this.anonymous.get("/auth/me", "Cookie", cookie).status());

		Assertions.assertEquals(204, this.anonymous.delete("/auth/session").status());
		Assertions.assertEquals(204, this.anonymous.delete("/auth/session", "Cookie", cookie).status());
	}

	@Test
	@DisplayName("Without a session: 401 when anonymous, 404 when authenticated otherwise")
	void noSession() throws Exception {
		Answer none = this.anonymous.get("/auth/session");
		Assertions.assertEquals(401, none.status(), none.toString());
		Assertions.assertEquals("Bearer", none.header("WWW-Authenticate"));
		Assertions.assertEquals(401, this.anonymous.get("/auth/me").status());

		Answer token = this.admin.get("/auth/session");
		Assertions.assertEquals(404, token.status(), token.toString());
		Assertions.assertEquals("NETSHOT_SESSION_NOT_FOUND", token.json().get("code").asText());
	}

	@Test
	@DisplayName("Current user of an API token")
	void meWithApiToken() throws Exception {
		Answer answer = this.admin.get("/auth/me");
		Assertions.assertEquals(200, answer.status(), answer.toString());
		JsonNode me = answer.json();
		Assertions.assertEquals("API_TOKEN", me.get("kind").asText());
		Assertions.assertEquals("API_TOKEN", me.get("authMethod").asText());
		Assertions.assertTrue(me.get("username").isNull());
		Assertions.assertTrue(me.get("local").isNull());
		Assertions.assertEquals(UiUser.LEVEL_ADMIN, me.get("level").asInt());
		Assertions.assertEquals("admin", me.get("role").asText());
		Assertions.assertEquals("Test Token - level 1000", me.get("display").asText());
		Assertions.assertEquals("/api/v1/api-tokens/" + me.get("id").asLong(), me.get("url").asText());

		V1Client readOnly = new V1Client(this.server, UiUser.LEVEL_READONLY);
		Assertions.assertEquals("read-only", readOnly.get("/auth/me").json().get("role").asText());
	}

	@Test
	@DisplayName("Change own password: current password checked, policy enforced, local users only")
	void changePassword() throws Exception {
		String cookie = sessionCookie(this.doLogin());
		String newPassword = "Brandnewpassword2";

		Answer wrong = this.anonymous.send("PATCH", "/auth/me", "{\"currentPassword\": \"nope\", \"newPassword\": \"" + newPassword + "\"}",
			"Cookie", cookie);
		Assertions.assertEquals(400, wrong.status(), wrong.toString());
		Assertions.assertEquals("NETSHOT_INVALID_PASSWORD", wrong.json().get("code").asText());

		Answer missing = this.anonymous.send("PATCH", "/auth/me", "{\"currentPassword\": \"" + PASSWORD + "\"}", "Cookie", cookie);
		Assertions.assertEquals(400, missing.status(), missing.toString());

		// A password policy
		Properties config = TestConfig.properties();
		config.setProperty("netshot.aaa.passwordpolicy.mintotalchars", "20");
		TestConfig.init(config);
		Answer policy = this.anonymous.send("PATCH", "/auth/me",
			"{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"" + newPassword + "\"}", "Cookie", cookie);
		Assertions.assertEquals(400, policy.status(), policy.toString());
		Assertions.assertEquals("NETSHOT_FAILED_PASSWORD_POLICY", policy.json().get("code").asText());
		TestConfig.init(this.originalConfig);

		Answer changed = this.anonymous.send("PATCH", "/auth/me",
			"{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"" + newPassword + "\"}", "Cookie", cookie);
		Assertions.assertEquals(200, changed.status(), changed.toString());
		Assertions.assertEquals(USERNAME, changed.json().get("username").asText());
		Assertions.assertFalse(changed.body().contains(newPassword));

		Assertions.assertEquals(401, this.anonymous.post("/auth/session", credentials(USERNAME, PASSWORD)).status(),
			"The old password still works");
		Assertions.assertEquals(201, this.anonymous.post("/auth/session", credentials(USERNAME, newPassword)).status());

		// An API token has no password
		Answer token = this.admin.send("PATCH", "/auth/me",
			"{\"currentPassword\": \"a\", \"newPassword\": \"b\"}");
		Assertions.assertEquals(400, token.status(), token.toString());
	}

	@Test
	@DisplayName("Expired password: 403, unless a new password is given while logging in")
	void expiredPassword() throws Exception {
		this.createUser("expireduser", PASSWORD, UiUser.LEVEL_READONLY, Instant.now().minus(365, ChronoUnit.DAYS));
		Properties config = TestConfig.properties();
		config.setProperty("netshot.aaa.passwordpolicy.maxduration", "90");
		TestConfig.init(config);

		Answer expired = this.anonymous.post("/auth/session", credentials("expireduser", PASSWORD));
		Assertions.assertEquals(403, expired.status(), expired.toString());
		Assertions.assertEquals("NETSHOT_EXPIRED_PASSWORD", expired.json().get("code").asText());
		Assertions.assertNull(sessionCookie(expired));

		Answer renewed = this.anonymous.post("/auth/session",
			"{\"username\": \"expireduser\", \"password\": \"" + PASSWORD + "\", \"newPassword\": \"Anotherpassword3\"}");
		Assertions.assertEquals(201, renewed.status(), renewed.toString());
		Assertions.assertNotNull(sessionCookie(renewed));
		Assertions.assertEquals(401, this.anonymous.post("/auth/session", credentials("expireduser", PASSWORD)).status());
	}
}
