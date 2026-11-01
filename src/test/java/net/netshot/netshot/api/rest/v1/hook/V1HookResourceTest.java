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
package net.netshot.netshot.api.rest.v1.hook;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.api.V1Endpoints;

@ApiTest
@V1Endpoints({
	"GET /hooks",
	"GET /hooks/{id}",
	"POST /hooks",
	"PUT /hooks/{id}",
	"PATCH /hooks/{id}",
	"DELETE /hooks/{id}"
})
@DisplayName("v1 hooks")
@ResourceLock("DB")
public class V1HookResourceTest {

	private TestServer server;
	private V1Client admin;

	@BeforeEach
	void setUp(TestServer testServer) {
		this.server = testServer;
		this.admin = new V1Client(testServer, UiUser.LEVEL_ADMIN);
		TestWiring.get().transactions().run(dsl -> {
			dsl.deleteFrom(Tables.HOOK_TRIGGER).execute();
			dsl.deleteFrom(Tables.HOOK).execute();
		});
	}

	private static String webHook(String name, String targetUrl) {
		return """
			{"type": "WEB", "name": "%s", "action": "POST_JSON", "targetUrl": "%s",
			"triggers": [{"type": "POST_TASK", "item": "TakeSnapshotTask"}, {"type": "POST_TASK", "item": "RunDeviceScriptTask"}]}"""
			.formatted(name, targetUrl);
	}

	private long create(String body) throws Exception {
		Answer answer = this.admin.post("/hooks", body);
		Assertions.assertEquals(201, answer.status(), answer.toString());
		return answer.json().get("id").asLong();
	}

	private static Set<String> names(JsonNode object) {
		Set<String> names = new TreeSet<>();
		object.fieldNames().forEachRemaining(names::add);
		return names;
	}

	private static List<String> items(JsonNode hook) {
		List<String> items = new java.util.ArrayList<>();
		hook.get("triggers").forEach(t -> items.add(t.get("item").asText()));
		return items;
	}

	@Test
	@DisplayName("Create: 201, Location, full representation with defaults")
	void create() throws Exception {
		Answer answer = this.admin.post("/hooks", webHook("Notify", "https://example.com/hook"));
		Assertions.assertEquals(201, answer.status(), answer.toString());
		JsonNode hook = answer.json();
		long id = hook.get("id").asLong();
		Assertions.assertEquals("/api/v1/hooks/" + id, answer.header("Location"));
		Assertions.assertNull(answer.header("ETag"));
		Assertions.assertEquals(Set.of("id", "url", "display", "type", "name", "enabled", "triggers", "action", "targetUrl",
			"httpsCaTrustMode", "httpsCustomCaCertificate"), names(hook));
		Assertions.assertEquals("/api/v1/hooks/" + id, hook.get("url").asText());
		Assertions.assertEquals("Notify", hook.get("display").asText());
		Assertions.assertEquals("WEB", hook.get("type").asText());
		Assertions.assertTrue(hook.get("enabled").asBoolean());
		Assertions.assertEquals("POST_JSON", hook.get("action").asText());
		Assertions.assertEquals("https://example.com/hook", hook.get("targetUrl").asText());
		Assertions.assertEquals("SYSTEM_TRUSTSTORE", hook.get("httpsCaTrustMode").asText());
		Assertions.assertEquals(List.of("RunDeviceScriptTask", "TakeSnapshotTask"), items(hook));
	}

	@Test
	@DisplayName("Invalid hooks are refused with problem details")
	void invalid() throws Exception {
		Answer noType = this.admin.post("/hooks", "{\"name\": \"A\", \"action\": \"POST_JSON\", \"targetUrl\": \"https://example.com/\"}");
		Assertions.assertEquals(400, noType.status(), noType.toString());
		Assertions.assertEquals(400, this.admin.post("/hooks", "{\"type\": \"EMAIL\", \"name\": \"A\"}").status());
		Assertions.assertEquals(400, this.admin.post("/hooks", "{\"type\": \"WEB\", \"action\": \"POST_JSON\", \"targetUrl\": \"https://e.com/\"}").status());
		Assertions.assertEquals(400, this.admin.post("/hooks", "{\"type\": \"WEB\", \"name\": \"A\", \"targetUrl\": \"https://e.com/\"}").status());
		Assertions.assertEquals(400, this.admin.post("/hooks", "{\"type\": \"WEB\", \"name\": \"A\", \"action\": \"POST_JSON\"}").status());
		Assertions.assertEquals(400, this.admin.post("/hooks", webHook("A", "ftp://example.com/")).status());
		Assertions.assertEquals(400, this.admin.post("/hooks", webHook("A", "not a url")).status());
		Assertions.assertEquals(400, this.admin.post("/hooks", webHook("  ", "https://example.com/")).status());
		Assertions.assertEquals(400, this.admin.post("/hooks", "{\"type\": \"WEB\", \"name\": \"A\", \"action\": \"SING\", \"targetUrl\": \"https://e.com/\"}").status());
		Assertions.assertEquals(400, this.admin.post("/hooks",
			"{\"type\": \"WEB\", \"name\": \"A\", \"action\": \"POST_JSON\", \"targetUrl\": \"https://e.com/\", \"triggers\": [{\"item\": \"x\"}]}").status());
		Assertions.assertEquals(400, this.admin.post("/hooks",
			"{\"type\": \"WEB\", \"name\": \"A\", \"action\": \"POST_JSON\", \"targetUrl\": \"https://e.com/\", \"httpsCaTrustMode\": \"CUSTOM_CA\"}").status());
		Assertions.assertEquals(400, this.admin.post("/hooks",
			"{\"type\": \"WEB\", \"name\": \"A\", \"action\": \"POST_JSON\", \"targetUrl\": \"https://e.com/\", \"color\": \"red\"}").status());

		this.create(webHook("Dup", "https://example.com/"));
		Answer duplicate = this.admin.post("/hooks", webHook("Dup", "https://example.com/other"));
		Assertions.assertEquals(409, duplicate.status(), duplicate.toString());
		Assertions.assertEquals("urn:netshot:error:duplicate-hook", duplicate.json().get("type").asText());

		// A representation read can be sent back
		Answer roundTrip = this.admin.post("/hooks",
			"{\"id\": 5, \"url\": \"x\", \"display\": \"x\", \"type\": \"WEB\", \"name\": \"Round\", \"enabled\": false,"
				+ " \"action\": \"POST_XML\", \"targetUrl\": \"https://example.com/r\", \"triggers\": []}");
		Assertions.assertEquals(201, roundTrip.status(), roundTrip.toString());
		Assertions.assertFalse(roundTrip.json().get("enabled").asBoolean());
	}

	@Test
	@DisplayName("List: envelope, brief view, filters and sort")
	void list() throws Exception {
		this.create(webHook("Zulu", "https://example.com/z"));
		this.create(webHook("Alpha", "https://example.com/a"));
		this.create("{\"type\": \"WEB\", \"name\": \"Off\", \"enabled\": false, \"action\": \"POST_XML\", \"targetUrl\": \"https://example.com/o\"}");

		Answer all = this.admin.get("/hooks");
		Assertions.assertEquals(200, all.status(), all.toString());
		JsonNode page = all.json();
		Assertions.assertEquals(3, page.get("count").asLong());
		Assertions.assertEquals("Alpha", page.get("results").get(0).get("name").asText());
		Assertions.assertEquals(Set.of("id", "url", "display", "type", "name", "enabled", "targetUrl", "httpsCaTrustMode"), names(page.get("results").get(0)));

		Assertions.assertEquals(1, this.admin.get("/hooks?enabled=false").json().get("count").asLong());
		Assertions.assertEquals(3, this.admin.get("/hooks?type=WEB").json().get("count").asLong());
		Assertions.assertEquals(1, this.admin.get("/hooks?q=zul").json().get("count").asLong());
		Assertions.assertEquals("Zulu", this.admin.get("/hooks?sort=-name&limit=1").json().get("results").get(0).get("name").asText());
		Assertions.assertEquals(2, this.admin.get("/hooks?view=full").json().get("results").get(0).get("triggers").size());
		Assertions.assertEquals(400, this.admin.get("/hooks?color=red").status());
	}

	@Test
	@DisplayName("Get: 404 for an unknown hook")
	void get() throws Exception {
		long id = this.create(webHook("One", "https://example.com/1"));
		Assertions.assertEquals(200, this.admin.get("/hooks/" + id).status());
		Answer unknown = this.admin.get("/hooks/" + TestServer.UNKNOWN_ID);
		Assertions.assertEquals(404, unknown.status(), unknown.toString());
		Assertions.assertEquals("NETSHOT_HOOK_NOT_FOUND", unknown.json().get("code").asText());
	}

	@Test
	@DisplayName("Replace: absent optional properties take their defaults")
	void replace() throws Exception {
		long id = this.create(webHook("Before", "https://example.com/before"));
		Answer replaced = this.admin.put("/hooks/" + id,
			"{\"type\": \"WEB\", \"name\": \"After\", \"enabled\": false, \"action\": \"POST_XML\", \"targetUrl\": \"http://example.com/after\"}");
		Assertions.assertEquals(200, replaced.status(), replaced.toString());
		JsonNode hook = replaced.json();
		Assertions.assertEquals(id, hook.get("id").asLong());
		Assertions.assertEquals("After", hook.get("name").asText());
		Assertions.assertFalse(hook.get("enabled").asBoolean());
		Assertions.assertEquals(0, hook.get("triggers").size(), "Absent triggers are replaced by none");
		Assertions.assertEquals("POST_XML", hook.get("action").asText());
		Assertions.assertEquals(200, this.admin.get("/hooks/" + id).status());

		Assertions.assertEquals(404, this.admin.put("/hooks/" + TestServer.UNKNOWN_ID, webHook("X", "https://example.com/")).status());
		Assertions.assertEquals(400, this.admin.put("/hooks/" + id, "{\"type\": \"WEB\", \"name\": \"After\"}").status());
	}

	@Test
	@DisplayName("Update with a merge patch")
	void patch() throws Exception {
		long id = this.create(webHook("Patched", "https://example.com/p"));
		Answer enabled = this.admin.patch("/hooks/" + id, "{\"enabled\": false}");
		Assertions.assertEquals(200, enabled.status(), enabled.toString());
		Assertions.assertFalse(enabled.json().get("enabled").asBoolean());
		Assertions.assertEquals("Patched", enabled.json().get("name").asText());
		Assertions.assertEquals(2, enabled.json().get("triggers").size());

		Answer triggers = this.admin.patch("/hooks/" + id, "{\"triggers\": [{\"type\": \"POST_TASK\", \"item\": \"CheckComplianceTask\"}]}");
		Assertions.assertEquals(200, triggers.status(), triggers.toString());
		Assertions.assertEquals(List.of("CheckComplianceTask"), items(triggers.json()));

		Answer url = this.admin.patch("/hooks/" + id, "{\"targetUrl\": \"ftp://example.com/\"}");
		Assertions.assertEquals(400, url.status(), url.toString());
		Assertions.assertEquals("https://example.com/p", this.admin.get("/hooks/" + id).json().get("targetUrl").asText());

		Assertions.assertEquals(404, this.admin.patch("/hooks/" + TestServer.UNKNOWN_ID, "{\"enabled\": true}").status());
	}

	@Test
	@DisplayName("Delete: 204, then 404")
	void delete() throws Exception {
		long id = this.create(webHook("Gone", "https://example.com/g"));
		Assertions.assertEquals(204, this.admin.delete("/hooks/" + id).status());
		Assertions.assertEquals(404, this.admin.get("/hooks/" + id).status());
		Assertions.assertEquals(404, this.admin.delete("/hooks/" + id).status());
	}

	@Test
	@DisplayName("Only admins manage the hooks")
	void permissions() throws Exception {
		V1Client other = new V1Client(this.server, UiUser.LEVEL_EXECUTEREADWRITE);
		Assertions.assertEquals(403, other.get("/hooks").status());
		Assertions.assertEquals(403, other.post("/hooks", webHook("X", "https://example.com/")).status());
		Assertions.assertEquals(401, new V1Client(this.server, null).get("/hooks").status());
	}
}
