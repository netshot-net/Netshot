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
package net.netshot.netshot.api.rest.v1.user;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

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
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.api.V1Endpoints;

@ApiTest
@V1Endpoints({
	"GET /users",
	"GET /users/{id}",
	"POST /users",
	"PUT /users/{id}",
	"PATCH /users/{id}",
	"DELETE /users/{id}",
	"POST /auth/session"
})
@DisplayName("v1 users")
@ResourceLock("DB")
public class V1UserResourceTest {

	private TestServer server;
	private V1Client admin;
	private V1Client anonymous;
	private Properties originalConfig;

	@BeforeEach
	void setUp(TestServer testServer) {
		this.server = testServer;
		this.admin = new V1Client(testServer, UiUser.LEVEL_ADMIN);
		this.anonymous = new V1Client(testServer, null);
		this.originalConfig = TestConfig.properties();
	}

	@AfterEach
	void restoreConfig() {
		TestConfig.init(this.originalConfig);
	}

	private static String localUser(String username, String password, int level) {
		return """
			{"username": "%s", "local": true, "level": %d, "password": %s}"""
			.formatted(username, level, password == null ? "null" : "\"" + password + "\"");
	}

	private static String remoteUser(String username, int level) {
		return """
			{"username": "%s", "local": false, "level": %d}""".formatted(username, level);
	}

	private long create(String body) throws IOException, InterruptedException {
		Answer answer = this.admin.post("/users", body);
		Assertions.assertEquals(201, answer.status(), answer.toString());
		return answer.json().get("id").asLong();
	}

	private boolean canLogIn(String username, String password) throws IOException, InterruptedException {
		Answer answer = this.anonymous.post("/auth/session", """
			{"username": "%s", "password": "%s"}""".formatted(username, password));
		return answer.status() == 201;
	}

	private static Set<String> names(JsonNode object) {
		Set<String> names = new TreeSet<>();
		object.fieldNames().forEachRemaining(names::add);
		return names;
	}

	private static void assertNoSecret(Answer answer, String... secrets) {
		String body = answer.body().toLowerCase();
		Assertions.assertFalse(body.contains("hash"), answer.toString());
		for (String secret : secrets) {
			Assertions.assertFalse(body.contains(secret.toLowerCase()), answer.toString());
		}
		if (answer.body().startsWith("{") || answer.body().startsWith("[")) {
			Assertions.assertFalse(answer.json().toString().contains("\"password\""), answer.toString());
		}
	}

	@Test
	@DisplayName("Create a local user: 201, Location, representation without any secret, can log in")
	void createLocal() throws Exception {
		Answer answer = this.admin.post("/users", localUser("alice", "Alicepassword1", UiUser.LEVEL_READWRITE));
		Assertions.assertEquals(201, answer.status(), answer.toString());
		JsonNode user = answer.json();
		long id = user.get("id").asLong();
		Assertions.assertEquals("/api/v1/users/" + id, answer.header("Location"));
		Assertions.assertNull(answer.header("ETag"));
		Assertions.assertEquals(Set.of("id", "url", "display", "username", "local", "level", "role", "hasPassword",
			"lastPasswordChangeDate"), names(user));
		Assertions.assertEquals("alice", user.get("display").asText());
		Assertions.assertTrue(user.get("local").asBoolean());
		Assertions.assertTrue(user.get("hasPassword").asBoolean());
		Assertions.assertEquals("read-write", user.get("role").asText());
		assertNoSecret(answer, "Alicepassword1");

		Assertions.assertTrue(this.canLogIn("alice", "Alicepassword1"));
		Assertions.assertFalse(this.canLogIn("alice", "Wrongpassword1"));
		assertNoSecret(this.admin.get("/users/" + id), "Alicepassword1");
	}

	@Test
	@DisplayName("Create a remote user: no password")
	void createRemote() throws Exception {
		Answer answer = this.admin.post("/users", remoteUser("bob", UiUser.LEVEL_READONLY));
		Assertions.assertEquals(201, answer.status(), answer.toString());
		Assertions.assertFalse(answer.json().get("local").asBoolean());
		Assertions.assertFalse(answer.json().get("hasPassword").asBoolean());
		Assertions.assertTrue(answer.json().get("lastPasswordChangeDate").isNull());
		// A password given for a remote user is ignored
		Answer ignored = this.admin.post("/users",
			"{\"username\": \"carol\", \"local\": false, \"level\": 10, \"password\": \"Ignoredpassword1\"}");
		Assertions.assertEquals(201, ignored.status(), ignored.toString());
		Assertions.assertFalse(ignored.json().get("hasPassword").asBoolean());
		Assertions.assertFalse(this.canLogIn("carol", "Ignoredpassword1"));
	}

	@Test
	@DisplayName("Invalid users are refused with problem details")
	void invalidUsers() throws Exception {
		Answer missing = this.admin.post("/users", "{\"local\": false, \"level\": 10}");
		Assertions.assertEquals(400, missing.status(), missing.toString());
		Assertions.assertEquals("username", missing.json().get("errors").get(0).get("field").asText());
		Assertions.assertEquals(400, this.admin.post("/users", "{\"username\": \"a\", \"level\": 10}").status());
		Assertions.assertEquals(400, this.admin.post("/users", "{\"username\": \"a\", \"local\": false}").status());
		Assertions.assertEquals(400, this.admin.post("/users", "{\"username\": \"a\", \"local\": false, \"level\": 10, \"x\": 1}").status());
		Assertions.assertEquals(400, this.admin.post("/users", remoteUser("   ", 10)).status());

		Answer noPassword = this.admin.post("/users", "{\"username\": \"a\", \"local\": true, \"level\": 10}");
		Assertions.assertEquals(400, noPassword.status(), noPassword.toString());
		Assertions.assertEquals("NETSHOT_INVALID_PASSWORD", noPassword.json().get("code").asText());
		Assertions.assertEquals(400, this.admin.post("/users", localUser("a", null, 10)).status());

		Properties config = TestConfig.properties();
		config.setProperty("netshot.aaa.passwordpolicy.mintotalchars", "20");
		TestConfig.init(config);
		Answer policy = this.admin.post("/users", localUser("a", "Shortpassword1", 10));
		Assertions.assertEquals(400, policy.status(), policy.toString());
		TestConfig.init(this.originalConfig);

		this.create(remoteUser("dup", 10));
		Answer duplicate = this.admin.post("/users", remoteUser("dup", 50));
		Assertions.assertEquals(409, duplicate.status(), duplicate.toString());
		Assertions.assertEquals("urn:netshot:error:duplicate-user", duplicate.json().get("type").asText());

		// A representation read can be sent back: the read-only properties are ignored
		Answer roundTrip = this.admin.post("/users",
			"{\"id\": 9, \"url\": \"x\", \"display\": \"x\", \"role\": \"admin\", \"hasPassword\": false, \"lastPasswordChangeDate\": null,"
				+ " \"username\": \"round\", \"local\": false, \"level\": 10}");
		Assertions.assertEquals(201, roundTrip.status(), roundTrip.toString());
	}

	@Test
	@DisplayName("List: envelope, brief view, filters, sort, search")
	void list() throws Exception {
		this.create(localUser("zoe", "Zoepassword123", UiUser.LEVEL_ADMIN));
		this.create(remoteUser("adam", UiUser.LEVEL_READONLY));
		this.create(remoteUser("adele", UiUser.LEVEL_OPERATOR));

		Answer all = this.admin.get("/users");
		Assertions.assertEquals(200, all.status(), all.toString());
		JsonNode page = all.json();
		Assertions.assertEquals(3, page.get("results").size());
		List<String> names = new ArrayList<>();
		page.get("results").forEach(u -> names.add(u.get("username").asText()));
		Assertions.assertEquals(List.of("zoe", "adam", "adele"), names, "without sort: by ID");
		Assertions.assertEquals(Set.of("id", "url", "display", "username", "local", "level"), names(page.get("results").get(0)));
		assertNoSecret(all, "Zoepassword123");

		Assertions.assertEquals(2, this.admin.get("/users?local=false").json().get("results").size());
		Assertions.assertEquals(1, this.admin.get("/users?level__gte=1000").json().get("results").size());
		Assertions.assertEquals(2, this.admin.get("/users?q=ad").json().get("results").size());
		Assertions.assertEquals("zoe", this.admin.get("/users?sort=-username&limit=1").json().get("results").get(0).get("username").asText());
		Assertions.assertEquals(400, this.admin.get("/users?color=red").status());
		Assertions.assertEquals(400, this.admin.get("/users?sort=hashedPassword").status());
		Assertions.assertEquals(400, this.admin.get("/users?hashedPassword=x").status());
	}

	@Test
	@DisplayName("Get: full view, 404 for an unknown user")
	void get() throws Exception {
		long id = this.create(remoteUser("dave", UiUser.LEVEL_OPERATOR));
		Answer answer = this.admin.get("/users/" + id);
		Assertions.assertEquals(200, answer.status(), answer.toString());
		Assertions.assertEquals("operator", answer.json().get("role").asText());
		Answer unknown = this.admin.get("/users/" + TestServer.UNKNOWN_ID);
		Assertions.assertEquals(404, unknown.status(), unknown.toString());
		Assertions.assertEquals("NETSHOT_USER_NOT_FOUND", unknown.json().get("code").asText());
	}

	@Test
	@DisplayName("Replace: an absent password is unchanged; a given one replaces it; remote/local switches")
	void replace() throws Exception {
		long id = this.create(localUser("erin", "Erinpassword123", UiUser.LEVEL_READONLY));

		Answer unchanged = this.admin.put("/users/" + id, "{\"username\": \"erin2\", \"local\": true, \"level\": 100}");
		Assertions.assertEquals(200, unchanged.status(), unchanged.toString());
		Assertions.assertEquals("erin2", unchanged.json().get("username").asText());
		Assertions.assertEquals(100, unchanged.json().get("level").asInt());
		Assertions.assertTrue(unchanged.json().get("hasPassword").asBoolean());
		Assertions.assertTrue(this.canLogIn("erin2", "Erinpassword123"));

		Answer changed = this.admin.put("/users/" + id, localUser("erin2", "Erinnewpassword12", 100));
		Assertions.assertEquals(200, changed.status(), changed.toString());
		assertNoSecret(changed, "Erinnewpassword12", "Erinpassword123");
		Assertions.assertFalse(this.canLogIn("erin2", "Erinpassword123"));
		Assertions.assertTrue(this.canLogIn("erin2", "Erinnewpassword12"));

		Answer cleared = this.admin.put("/users/" + id, localUser("erin2", null, 100));
		Assertions.assertEquals(400, cleared.status(), cleared.toString());
		Assertions.assertEquals("password", cleared.json().get("errors").get(0).get("field").asText());

		Answer remote = this.admin.put("/users/" + id, remoteUser("erin2", 100));
		Assertions.assertEquals(200, remote.status(), remote.toString());
		Assertions.assertFalse(remote.json().get("hasPassword").asBoolean());
		Assertions.assertFalse(this.canLogIn("erin2", "Erinnewpassword12"));

		Answer noPassword = this.admin.put("/users/" + id, "{\"username\": \"erin2\", \"local\": true, \"level\": 100}");
		Assertions.assertEquals(400, noPassword.status(), noPassword.toString());
		Assertions.assertEquals(200, this.admin.put("/users/" + id, localUser("erin2", "Erinthirdpassword1", 100)).status());

		Assertions.assertEquals(404, this.admin.put("/users/" + TestServer.UNKNOWN_ID, remoteUser("x", 10)).status());
		Assertions.assertEquals(400, this.admin.put("/users/" + id, "{\"username\": \"x\"}").status());
	}

	@Test
	@DisplayName("Update with a merge patch")
	void patch() throws Exception {
		long id = this.create(localUser("frank", "Frankpassword123", UiUser.LEVEL_READONLY));
		Answer level = this.admin.patch("/users/" + id, "{\"level\": 500}");
		Assertions.assertEquals(200, level.status(), level.toString());
		Assertions.assertEquals(500, level.json().get("level").asInt());
		Assertions.assertEquals("frank", level.json().get("username").asText());
		Assertions.assertTrue(this.canLogIn("frank", "Frankpassword123"), "A patch not touching the password keeps it");

		Answer password = this.admin.patch("/users/" + id, "{\"password\": \"Franknewpassword12\"}");
		Assertions.assertEquals(200, password.status(), password.toString());
		Assertions.assertTrue(this.canLogIn("frank", "Franknewpassword12"));
		Assertions.assertFalse(this.canLogIn("frank", "Frankpassword123"));

		Answer cleared = this.admin.patch("/users/" + id, "{\"password\": null}");
		Assertions.assertEquals(400, cleared.status(), cleared.toString());
		Assertions.assertTrue(this.canLogIn("frank", "Franknewpassword12"));

		Assertions.assertEquals(404, this.admin.patch("/users/" + TestServer.UNKNOWN_ID, "{\"level\": 10}").status());
	}

	@Test
	@DisplayName("Delete: 204, then 404")
	void delete() throws Exception {
		long id = this.create(remoteUser("gina", UiUser.LEVEL_READONLY));
		Assertions.assertEquals(204, this.admin.delete("/users/" + id).status());
		Assertions.assertEquals(404, this.admin.get("/users/" + id).status());
		Answer again = this.admin.delete("/users/" + id);
		Assertions.assertEquals(404, again.status(), again.toString());
		Assertions.assertEquals("NETSHOT_USER_NOT_FOUND", again.json().get("code").asText());
	}

	@Test
	@DisplayName("A user can't edit nor delete itself")
	void noSelfEdition() throws Exception {
		UiUser root = UiUser.withPassword("rooty", true, UiUser.LEVEL_ADMIN, "Rootypassword123");
		long id = TestWiring.get().transactions().write(dsl -> new UserRepository().add(dsl, root));
		Answer login = this.anonymous.post("/auth/session", """
			{"username": "rooty", "password": "Rootypassword123"}""");
		Assertions.assertEquals(201, login.status(), login.toString());
		String cookie = login.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("NetshotSessionID="))
			.map(c -> c.substring(0, c.indexOf(';'))).findFirst().orElseThrow();

		Answer edit = this.anonymous.put("/users/" + id, localUser("rooty", "Whateverpassword12", 10), "Cookie", cookie);
		Assertions.assertEquals(403, edit.status(), edit.toString());
		Assertions.assertEquals("NETSHOT_LOCKED_USER", edit.json().get("code").asText());
		Assertions.assertEquals(403, this.anonymous.patch("/users/" + id, "{\"level\": 10}", "Cookie", cookie).status());
		Assertions.assertEquals(403, this.anonymous.delete("/users/" + id, "Cookie", cookie).status());
		// But it can administer the others
		Assertions.assertEquals(200, this.anonymous.get("/users", "Cookie", cookie).status());
	}

	@Test
	@DisplayName("Only admins manage the users")
	void permissions() throws Exception {
		V1Client readWrite = new V1Client(this.server, UiUser.LEVEL_EXECUTEREADWRITE);
		Assertions.assertEquals(403, readWrite.get("/users").status());
		Assertions.assertEquals(403, readWrite.post("/users", remoteUser("h", 10)).status());
		Assertions.assertEquals(401, this.anonymous.get("/users").status());
	}
}
