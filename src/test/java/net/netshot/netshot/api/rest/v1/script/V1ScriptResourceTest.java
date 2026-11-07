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
package net.netshot.netshot.api.rest.v1.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.api.V1Endpoints;

/**
 * The command scripts of the v1 API, over HTTP.
 */
@ApiTest
@V1Endpoints({
	"GET /scripts",
	"GET /scripts/{id}",
	"POST /scripts",
	"POST /scripts/validation",
	"PUT /scripts/{id}",
	"PATCH /scripts/{id}",
	"DELETE /scripts/{id}",
})
@DisplayName("v1 scripts")
@ResourceLock("DB")
public class V1ScriptResourceTest {

	private static final String CODE = "function run(cli, device) {\\n  cli.command('show clock');\\n}";

	private V1Client admin;
	private V1Client reader;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.admin = new V1Client(server, UiUser.LEVEL_ADMIN);
		this.reader = new V1Client(server, UiUser.LEVEL_READONLY);
		JsonNode page = this.admin.get("/scripts?limit=1000").json();
		for (JsonNode item : page.get("results")) {
			Assertions.assertEquals(204, this.admin.delete("/scripts/" + item.get("id").asLong()).status());
		}
	}

	private static List<String> values(JsonNode page, String property) {
		List<String> values = new ArrayList<>();
		page.get("results").forEach(item -> values.add(item.get(property).asText()));
		return values;
	}

	private static Set<String> keys(JsonNode object) {
		Set<String> keys = new TreeSet<>();
		object.fieldNames().forEachRemaining(keys::add);
		return keys;
	}

	private static String body(String name, String folder, String code) {
		return "{\"name\": \"%s\", \"folder\": \"%s\", \"deviceDriver\": \"CiscoIOS12\", \"script\": \"%s\"}".formatted(name, folder, code);
	}

	@Test
	@DisplayName("Create, read, list (brief view, filters), delete, 404, 409")
	void lifecycle() throws Exception {
		Answer created = this.admin.post("/scripts", body("Clock", "Basics", CODE));
		Assertions.assertEquals(201, created.status(), created.toString());
		JsonNode script = created.json();
		long id = script.get("id").asLong();
		Assertions.assertEquals("/api/v1/scripts/" + id, created.header("Location"));
		Assertions.assertEquals("Clock", script.get("name").asText());
		Assertions.assertEquals("Basics", script.get("folder").asText());
		Assertions.assertEquals("Cisco IOS and IOS-XE", script.get("deviceDriverDescription").asText());
		Assertions.assertFalse(script.get("author").asText().isEmpty());
		Assertions.assertTrue(script.get("userInputDefinitions").isObject());
		Assertions.assertTrue(script.get("script").asText().contains("show clock"));
		Assertions.assertEquals(201, this.admin.post("/scripts", body("Other", "", CODE)).status());

		Assertions.assertEquals(script.get("name"), this.admin.get("/scripts/" + id).json().get("name"));
		Assertions.assertTrue(this.admin.get("/scripts/" + id).json().get("userInputDefinitions").isObject());

		JsonNode page = this.admin.get("/scripts").json();
		Assertions.assertEquals(2, page.get("results").size());
		Assertions.assertEquals(List.of("Clock", "Other"), values(page, "name"));
		Assertions.assertEquals(Set.of("id", "url", "display", "name", "folder", "deviceDriver", "deviceDriverDescription", "author"), keys(page.get("results").get(0)));
		Assertions.assertEquals(List.of("Clock"), values(this.admin.get("/scripts?folder=Basics").json(), "name"));
		Assertions.assertEquals(List.of("Other", "Clock"), values(this.admin.get("/scripts?sort=-name").json(), "name"));
		Assertions.assertEquals(List.of("Other"), values(this.admin.get("/scripts?q=oth").json(), "name"));
		Assertions.assertEquals(2, this.admin.get("/scripts?deviceDriver=CiscoIOS12").json().get("results").size());
		JsonNode withInputs = this.admin.get("/scripts?fields=name,userInputDefinitions").json().get("results").get(0);
		Assertions.assertTrue(withInputs.get("userInputDefinitions").isObject());
		Assertions.assertEquals(400, this.admin.get("/scripts?foo=1").status());

		Assertions.assertEquals(409, this.admin.post("/scripts", body("Clock", "Basics", CODE)).status());
		Assertions.assertEquals(404, this.admin.get("/scripts/" + TestServer.UNKNOWN_ID).status());
		Assertions.assertEquals(404, this.admin.delete("/scripts/" + TestServer.UNKNOWN_ID).status());
		Assertions.assertEquals(204, this.admin.delete("/scripts/" + id).status());
		Assertions.assertEquals(404, this.admin.get("/scripts/" + id).status());
	}

	@Test
	@DisplayName("Replace and update: validated, the author kept, the version incremented, If-Match, 404, 409")
	void change() throws Exception {
		JsonNode created = this.admin.post("/scripts", body("Clock", "Basics", CODE)).json();
		long id = created.get("id").asLong();
		long version = created.get("version").asLong();
		this.admin.post("/scripts", body("Other", "", CODE));

		Answer replaced = this.admin.put("/scripts/" + id, body("Clock 2", "Advanced", CODE.replace("show clock", "show version")));
		Assertions.assertEquals(200, replaced.status(), replaced.toString());
		JsonNode script = replaced.json();
		Assertions.assertEquals("Clock 2", script.get("name").asText());
		Assertions.assertEquals("Advanced", script.get("folder").asText());
		Assertions.assertTrue(script.get("script").asText().contains("show version"));
		Assertions.assertEquals(created.get("author"), script.get("author"));
		Assertions.assertTrue(script.get("userInputDefinitions").isObject());
		Assertions.assertEquals(version + 1, script.get("version").asLong());
		Assertions.assertEquals("\"" + (version + 1) + "\"", replaced.header("ETag"));

		Answer patched = this.admin.patch("/scripts/" + id, "{\"folder\": \"Basics\"}", "If-Match", replaced.header("ETag"));
		Assertions.assertEquals(200, patched.status(), patched.toString());
		Assertions.assertEquals("Clock 2", patched.json().get("name").asText());
		Assertions.assertEquals("Basics", patched.json().get("folder").asText());
		Assertions.assertEquals(412, this.admin.patch("/scripts/" + id, "{\"folder\": \"X\"}", "If-Match", replaced.header("ETag")).status());

		Assertions.assertEquals(400, this.admin.patch("/scripts/" + id, "{\"script\": \"function run( {\"}").status());
		Assertions.assertEquals(409, this.admin.patch("/scripts/" + id, "{\"name\": \"Other\", \"folder\": \"\"}").status());
		Assertions.assertEquals(404, this.admin.put("/scripts/" + TestServer.UNKNOWN_ID, body("X", "", CODE)).status());
		Assertions.assertEquals("Basics", this.admin.get("/scripts/" + id).json().get("folder").asText());
	}

	@Test
	@DisplayName("Validation: the script as it would be created, or the problem; nothing is stored")
	void validation() throws Exception {
		Answer valid = this.admin.post("/scripts/validation", body("Checked", "", CODE));
		Assertions.assertEquals(200, valid.status(), valid.toString());
		Assertions.assertEquals(0, valid.json().get("id").asLong());
		Assertions.assertTrue(valid.json().get("userInputDefinitions").isObject());
		Assertions.assertEquals(0, this.admin.get("/scripts").json().get("results").size());

		Assertions.assertEquals(400, this.admin.post("/scripts/validation", body("Bad", "", "function run( {")).status());
		Assertions.assertEquals(400, this.admin.post("/scripts/validation", body(" ", "", CODE)).status());
		Assertions.assertEquals(400, this.admin.post("/scripts/validation", """
			{"name": "X", "deviceDriver": "NoSuchDriver", "script": "x"}""").status());
		Assertions.assertEquals(400, this.admin.post("/scripts/validation", "{\"name\": \"X\"}").status());
		Assertions.assertEquals(400, this.admin.post("/scripts", body("Bad", "", "function run( {")).status());
		Assertions.assertEquals(0, this.admin.get("/scripts").json().get("results").size());
	}

	@Test
	@DisplayName("Scripts need the execute level (403 for a read-only user)")
	void permissions() throws Exception {
		Assertions.assertEquals(403, this.reader.get("/scripts").status());
		Assertions.assertEquals(403, this.reader.get("/scripts/1").status());
		Assertions.assertEquals(403, this.reader.post("/scripts", body("X", "", CODE)).status());
		Assertions.assertEquals(403, this.reader.post("/scripts/validation", body("X", "", CODE)).status());
		Assertions.assertEquals(403, this.reader.delete("/scripts/1").status());
	}
}
