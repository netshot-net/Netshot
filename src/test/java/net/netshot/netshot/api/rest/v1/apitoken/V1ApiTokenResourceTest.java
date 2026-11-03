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
package net.netshot.netshot.api.rest.v1.apitoken;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.api.V1Endpoints;

@ApiTest
@V1Endpoints({
	"GET /api-tokens",
	"GET /api-tokens/{id}",
	"POST /api-tokens",
	"DELETE /api-tokens/{id}",
	"GET /auth/me"
})
@DisplayName("v1 API tokens")
@ResourceLock("DB")
public class V1ApiTokenResourceTest {

	private TestServer server;
	private V1Client admin;

	@BeforeEach
	void setUp(TestServer testServer) {
		this.server = testServer;
		this.admin = new V1Client(testServer, UiUser.LEVEL_ADMIN);
	}

	private static Set<String> names(JsonNode object) {
		Set<String> names = new TreeSet<>();
		object.fieldNames().forEachRemaining(names::add);
		return names;
	}

	/**
	 * Gets what the current user is for a request authenticated with a token value.
	 *
	 * @param value the token value
	 * @return the answer of GET /auth/me
	 */
	private Answer me(String value) throws Exception {
		return new V1Client(this.server, null).get("/auth/me", "X-Netshot-API-Token", value);
	}

	@Test
	@DisplayName("Create: 201, Location, and the token value in the answer only")
	void create() throws Exception {
		Answer answer = this.admin.post("/api-tokens", "{\"description\": \"CI robot\", \"level\": 100}");
		Assertions.assertEquals("no-store", answer.header("Cache-Control"), "The token value must not be cached");
		Assertions.assertEquals(201, answer.status(), answer.toString());
		JsonNode created = answer.json();
		long id = created.get("id").asLong();
		Assertions.assertEquals("/api/v1/api-tokens/" + id, answer.header("Location"));
		Assertions.assertEquals(Set.of("id", "url", "display", "description", "level", "role", "token"), names(created));
		Assertions.assertEquals("CI robot", created.get("display").asText());
		Assertions.assertEquals("read-write", created.get("role").asText());
		String value = created.get("token").asText();
		Assertions.assertTrue(value.matches("[A-Za-z0-9]{32}"), value);

		// The generated token works
		Answer me = this.me(value);
		Assertions.assertEquals(200, me.status(), me.toString());
		Assertions.assertEquals("API_TOKEN", me.json().get("kind").asText());
		Assertions.assertEquals(id, me.json().get("id").asLong());
		Assertions.assertEquals(100, me.json().get("level").asInt());
		Assertions.assertEquals("CI robot", me.json().get("display").asText());

		// ... but its value is never given again
		Answer get = this.admin.get("/api-tokens/" + id);
		Assertions.assertEquals(200, get.status(), get.toString());
		Assertions.assertEquals(Set.of("id", "url", "display", "description", "level", "role"), names(get.json()));
		Assertions.assertFalse(get.body().contains(value));
		Answer list = this.admin.get("/api-tokens?view=full&limit=100");
		Assertions.assertFalse(list.body().contains(value));
		Assertions.assertFalse(list.body().toLowerCase().contains("hash"));
		Assertions.assertFalse(list.body().contains("token\""), list.body());
	}

	@Test
	@DisplayName("Create with a given value, invalid and duplicate values")
	void givenValue() throws Exception {
		String value = "AbCdEfGhIjKlMnOpQrStUvWxYz012345";
		Answer answer = this.admin.post("/api-tokens", "{\"level\": 10, \"token\": \"" + value + "\"}");
		Assertions.assertEquals(201, answer.status(), answer.toString());
		Assertions.assertEquals(value, answer.json().get("token").asText());
		Assertions.assertEquals("", answer.json().get("description").asText());
		Assertions.assertTrue(answer.json().get("display").asText().startsWith("API token "));
		Assertions.assertEquals(200, this.me(value).status());

		Answer duplicate = this.admin.post("/api-tokens", "{\"level\": 10, \"token\": \"" + value + "\"}");
		Assertions.assertEquals(409, duplicate.status(), duplicate.toString());
		Assertions.assertEquals("NETSHOT_DUPLICATE_API_TOKEN", duplicate.json().get("code").asText());

		Answer invalid = this.admin.post("/api-tokens", "{\"level\": 10, \"token\": \"short\"}");
		Assertions.assertEquals(400, invalid.status(), invalid.toString());
		Assertions.assertEquals("NETSHOT_INVALID_API_TOKEN_FORMAT", invalid.json().get("code").asText());
		Assertions.assertEquals(400, this.admin.post("/api-tokens", "{\"level\": 10, \"token\": null}").status());
		Assertions.assertEquals(400, this.admin.post("/api-tokens", "{\"description\": \"no level\"}").status());
		Assertions.assertEquals(400, this.admin.post("/api-tokens", "{\"level\": 10, \"color\": \"red\"}").status());
	}

	@Test
	@DisplayName("List: the tokens in the brief view, filters and sort")
	void list() throws Exception {
		Answer all = this.admin.get("/api-tokens");
		Assertions.assertEquals(200, all.status(), all.toString());
		JsonNode page = all.json();
		Assertions.assertEquals(TestServer.API_TOKENS.size(), page.get("results").size());
		Assertions.assertEquals(Set.of("id", "url", "display", "description", "level"), names(page.get("results").get(0)));

		Assertions.assertEquals(1, this.admin.get("/api-tokens?level=1000").json().get("results").size());
		Assertions.assertEquals(2, this.admin.get("/api-tokens?level__gte=500").json().get("results").size());
		Assertions.assertEquals(TestServer.API_TOKENS.size(), this.admin.get("/api-tokens?q=test").json().get("results").size());
		Assertions.assertEquals(1000, this.admin.get("/api-tokens?sort=-level&limit=1").json().get("results").get(0).get("level").asInt());
		Assertions.assertEquals(400, this.admin.get("/api-tokens?hashedToken=x").status());
	}

	@Test
	@DisplayName("Delete: 204, the token stops working, then 404")
	void delete() throws Exception {
		Answer created = this.admin.post("/api-tokens", "{\"description\": \"temporary\", \"level\": 10}");
		long id = created.json().get("id").asLong();
		String value = created.json().get("token").asText();
		Assertions.assertEquals(200, this.me(value).status());

		Assertions.assertEquals(204, this.admin.delete("/api-tokens/" + id).status());
		Assertions.assertEquals(401, this.me(value).status());
		Assertions.assertEquals(404, this.admin.get("/api-tokens/" + id).status());
		Answer again = this.admin.delete("/api-tokens/" + id);
		Assertions.assertEquals(404, again.status(), again.toString());
		Assertions.assertEquals("NETSHOT_API_TOKEN_NOT_FOUND", again.json().get("code").asText());
		Assertions.assertEquals(404, this.admin.get("/api-tokens/" + TestServer.UNKNOWN_ID).status());
	}

	@Test
	@DisplayName("Only admins manage the API tokens")
	void permissions() throws Exception {
		V1Client other = new V1Client(this.server, UiUser.LEVEL_EXECUTEREADWRITE);
		Assertions.assertEquals(403, other.get("/api-tokens").status());
		Assertions.assertEquals(403, other.post("/api-tokens", "{\"level\": 10}").status());
		Assertions.assertEquals(403, other.delete("/api-tokens/1").status());
		Assertions.assertEquals(401, new V1Client(this.server, null).get("/api-tokens").status());
	}
}
