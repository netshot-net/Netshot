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
package net.netshot.netshot.api.rest.v1.diagnostic;

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
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.diagnostic.DiagnosticBinaryResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.api.V1Endpoints;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * The diagnostics and the diagnostic results of the devices of the v1 API, over HTTP.
 */
@ApiTest
@V1Endpoints({
	"GET /diagnostics",
	"GET /diagnostics/{id}",
	"POST /diagnostics",
	"PUT /diagnostics/{id}",
	"PATCH /diagnostics/{id}",
	"DELETE /diagnostics/{id}",
	"GET /devices/{id}/diagnostic-results",
})
@DisplayName("v1 diagnostics")
@ResourceLock("DB")
public class V1DiagnosticResourceTest {

	private V1Client admin;
	private V1Client reader;
	private StaticDeviceGroup core;
	private Device router1;
	private TestServer server;
	private Domain domain;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer testServer) throws Exception {
		this.server = testServer;
		this.admin = new V1Client(testServer, UiUser.LEVEL_ADMIN);
		this.reader = new V1Client(testServer, UiUser.LEVEL_READONLY);
		TestDevices.deleteAll();
		JsonNode page = this.admin.get("/diagnostics?limit=1000").json();
		for (JsonNode item : page.get("results")) {
			Assertions.assertEquals(204, this.admin.delete("/diagnostics/" + item.get("id").asLong()).status());
		}
		this.domain = TestDevices.addDomain("Paris", "Paris DC");
		this.router1 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(this.domain, null, 1));
		this.core = TestDevices.addStaticGroup("Core", this.router1);
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

	private long create(String body) throws Exception {
		Answer answer = this.admin.post("/diagnostics", body);
		Assertions.assertEquals(201, answer.status(), answer.toString());
		return answer.json().get("id").asLong();
	}

	private long createSimple(String name) throws Exception {
		return this.create("""
			{"name": "%s", "type": "SIMPLE", "resultType": "TEXT", "cliMode": "enable", "command": "show version",
			"deviceDriver": "CiscoIOS12", "enabled": true, "targetGroup": %d}""".formatted(name, this.core.id()));
	}

	@Test
	@DisplayName("List: brief view, filters, sort, search, 403 for no token")
	void list() throws Exception {
		this.createSimple("Version");
		this.create("""
			{"name": "Counter", "type": "JAVASCRIPT", "resultType": "NUMERIC", "script": "function diagnose(cli, device, diagnosticResult) {}"}""");
		this.create("""
			{"name": "Flag", "type": "PYTHON", "resultType": "BINARY", "script": "def diagnose(cli, device, diagnostic_result): pass",
			"enabled": true}""");
		JsonNode page = this.reader.get("/diagnostics").json();
		Assertions.assertEquals(3, page.get("count").asInt());
		Assertions.assertEquals(List.of("Counter", "Flag", "Version"), values(page, "name"));
		Assertions.assertEquals(Set.of("id", "url", "display", "name", "type", "resultType", "enabled", "targetGroup"), keys(page.get("results").get(0)));
		Assertions.assertEquals(List.of("Version", "Flag", "Counter"), values(this.reader.get("/diagnostics?sort=-name").json(), "name"));
		Assertions.assertEquals(List.of("Counter"), values(this.reader.get("/diagnostics?type=JAVASCRIPT").json(), "name"));
		Assertions.assertEquals(List.of("Counter"), values(this.reader.get("/diagnostics?resultType=NUMERIC").json(), "name"));
		Assertions.assertEquals(List.of("Flag", "Version"), values(this.reader.get("/diagnostics?enabled=true").json(), "name"));
		Assertions.assertEquals(List.of("Version"), values(this.reader.get("/diagnostics?targetGroup=" + this.core.id()).json(), "name"));
		Assertions.assertEquals(List.of("Version"), values(this.reader.get("/diagnostics?name__ic=VERS").json(), "name"));
		Assertions.assertEquals(List.of("Flag"), values(this.reader.get("/diagnostics?q=fla").json(), "name"));
		Assertions.assertEquals(400, this.reader.get("/diagnostics?type=OTHER").status());

		// The group reference is in the full view, brief unless expanded
		JsonNode full = this.reader.get("/diagnostics?view=full&name=Version").json().get("results").get(0);
		Assertions.assertEquals(this.core.id(), full.get("targetGroup").get("id").asLong());
		Assertions.assertFalse(full.get("targetGroup").has("hiddenFromReports"));
		JsonNode expanded = this.reader.get("/diagnostics?view=full&name=Version&expand=targetGroup").json().get("results").get(0);
		Assertions.assertTrue(expanded.get("targetGroup").has("hiddenFromReports"));

		V1Client anonymous = new V1Client(this.server, null);
		Assertions.assertEquals(401, anonymous.get("/diagnostics").status());
	}

	@Test
	@DisplayName("Create, read, replace, patch and delete; types, validations, 404, 403")
	void lifecycle() throws Exception {
		Answer created = this.admin.post("/diagnostics", """
			{"name": "Version", "type": "SIMPLE", "resultType": "TEXT", "cliMode": "enable", "command": "show version",
			"deviceDriver": "CiscoIOS12", "modifierPattern": "(\\\\d+)", "targetGroup": %d}""".formatted(this.core.id()));
		Assertions.assertEquals(201, created.status(), created.toString());
		JsonNode diagnostic = created.json();
		long id = diagnostic.get("id").asLong();
		Assertions.assertEquals("/api/v1/diagnostics/" + id, created.header("Location"));
		Assertions.assertEquals("SIMPLE", diagnostic.get("type").asText());
		Assertions.assertFalse(diagnostic.get("enabled").asBoolean());
		Assertions.assertEquals("Cisco IOS and IOS-XE", diagnostic.get("deviceDriverDescription").asText());
		Assertions.assertEquals("", diagnostic.get("modifierReplacement").asText());
		Assertions.assertEquals(this.core.id(), diagnostic.get("targetGroup").get("id").asLong());

		// GET -> PUT round trip
		JsonNode read = this.reader.get("/diagnostics/" + id).json();
		Assertions.assertEquals(diagnostic, read);
		((ObjectNode) read).put("command", "show clock").put("enabled", true);
		Answer replaced = this.admin.put("/diagnostics/" + id, read.toString());
		Assertions.assertEquals(200, replaced.status(), replaced.toString());
		Assertions.assertEquals("show clock", replaced.json().get("command").asText());
		Assertions.assertEquals(this.core.id(), replaced.json().get("targetGroup").get("id").asLong());

		// PUT: absent optional properties go back to their default (here, no target group)
		Answer defaults = this.admin.put("/diagnostics/" + id, """
			{"name": "Version", "type": "SIMPLE", "resultType": "TEXT", "cliMode": "enable", "command": "show clock"}""");
		Assertions.assertEquals(200, defaults.status(), defaults.toString());
		Assertions.assertTrue(defaults.json().get("targetGroup").isNull() || !defaults.json().has("targetGroup"));
		Assertions.assertFalse(defaults.json().get("enabled").asBoolean());

		// PATCH: the group by ID or object, cleared with null
		Answer patched = this.admin.patch("/diagnostics/" + id, "{\"targetGroup\": %d, \"enabled\": true}".formatted(this.core.id()));
		Assertions.assertEquals(200, patched.status(), patched.toString());
		Assertions.assertEquals(this.core.id(), patched.json().get("targetGroup").get("id").asLong());
		Assertions.assertTrue(patched.json().get("enabled").asBoolean());
		Assertions.assertEquals("show clock", patched.json().get("command").asText());
		Assertions.assertEquals(200, this.admin.patch("/diagnostics/" + id, "{\"targetGroup\": {\"id\": %d}}".formatted(this.core.id())).status());
		Answer cleared = this.admin.patch("/diagnostics/" + id, "{\"targetGroup\": null}");
		Assertions.assertEquals(200, cleared.status(), cleared.toString());
		Assertions.assertTrue(cleared.json().get("targetGroup") == null || cleared.json().get("targetGroup").isNull());

		// Invalid requests
		Assertions.assertEquals(400, this.admin.patch("/diagnostics/" + id, "{\"type\": \"JAVASCRIPT\"}").status(), "the type can't change");
		Assertions.assertEquals(400, this.admin.patch("/diagnostics/" + id, "{\"resultType\": \"DATE\"}").status());
		Assertions.assertEquals(400, this.admin.patch("/diagnostics/" + id, "{\"targetGroup\": " + TestServer.UNKNOWN_ID + "}").status());
		Assertions.assertEquals(400, this.admin.patch("/diagnostics/" + id, "{\"targetGroup\": \"x\"}").status());
		Assertions.assertEquals(400, this.admin.patch("/diagnostics/" + id, "{\"command\": \" \"}").status());
		Assertions.assertEquals(400, this.admin.patch("/diagnostics/" + id, "{\"name\": \"a\\\"b\"}").status());
		Assertions.assertEquals(409, this.admin.post("/diagnostics", """
			{"name": "Version", "type": "PYTHON", "resultType": "TEXT", "script": "x"}""").status());
		Assertions.assertEquals(400, this.admin.post("/diagnostics", "{\"name\": \"X\", \"resultType\": \"TEXT\"}").status());
		Assertions.assertEquals(400, this.admin.post("/diagnostics", "{\"name\": \"X\", \"type\": \"OTHER\", \"resultType\": \"TEXT\"}").status());
		Assertions.assertEquals(400, this.admin.post("/diagnostics", "{\"name\": \"X\", \"type\": \"PYTHON\", \"resultType\": \"TEXT\"}").status(),
			"a script is required");
		Assertions.assertEquals(403, this.reader.post("/diagnostics", """
			{"name": "Y", "type": "PYTHON", "resultType": "TEXT", "script": "x"}""").status());
		Assertions.assertEquals(403, this.reader.put("/diagnostics/" + id, read.toString()).status());
		Assertions.assertEquals(403, this.reader.patch("/diagnostics/" + id, "{}").status());
		Assertions.assertEquals(403, this.reader.delete("/diagnostics/" + id).status());

		// 404
		long unknown = TestServer.UNKNOWN_ID;
		Assertions.assertEquals(404, this.reader.get("/diagnostics/" + unknown).status());
		Assertions.assertEquals(404, this.admin.put("/diagnostics/" + unknown, read.toString()).status());
		Assertions.assertEquals(404, this.admin.patch("/diagnostics/" + unknown, "{}").status());
		Assertions.assertEquals(404, this.admin.delete("/diagnostics/" + unknown).status());

		Assertions.assertEquals(204, this.admin.delete("/diagnostics/" + id).status());
		Assertions.assertEquals(404, this.admin.get("/diagnostics/" + id).status());
	}

	@Test
	@DisplayName("Diagnostic results of a device: typed values, diagnostic reference, filters, 404")
	void results() throws Exception {
		long version = this.createSimple("Version");
		long count = this.create("""
			{"name": "Counter", "type": "JAVASCRIPT", "resultType": "NUMERIC", "script": "function diagnose(cli, device, diagnosticResult) {}"}""");
		long flag = this.create("""
			{"name": "Flag", "type": "PYTHON", "resultType": "BINARY", "script": "def diagnose(cli, device, diagnostic_result): pass"}""");
		Device device = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(this.domain, null, 2), null,
			DiagnosticTextResult.of(version, "Version", "15.2"), DiagnosticNumericResult.of(count, "Counter", 42.0),
			DiagnosticBinaryResult.of(flag, "Flag", true));

		JsonNode page = this.reader.get("/devices/" + device.id() + "/diagnostic-results").json();
		Assertions.assertEquals(3, page.get("count").asInt());
		Assertions.assertEquals(List.of("Counter", "Flag", "Version"), values(page, "display"));
		JsonNode counter = page.get("results").get(0);
		Assertions.assertEquals("NUMERIC", counter.get("type").asText());
		Assertions.assertEquals(42.0, counter.get("value").asDouble());
		Assertions.assertEquals(count, counter.get("diagnostic").get("id").asLong());
		Assertions.assertEquals("Counter", counter.get("diagnostic").get("name").asText());
		Assertions.assertTrue(counter.get("creationDate").asText().endsWith("Z"));
		Assertions.assertTrue(counter.has("lastCheckDate"));
		Assertions.assertTrue(page.get("results").get(1).get("value").asBoolean());
		Assertions.assertEquals("15.2", page.get("results").get(2).get("value").asText());
		Assertions.assertFalse(counter.get("diagnostic").has("command"), "the diagnostic is brief");

		JsonNode expanded = this.reader.get("/devices/" + device.id() + "/diagnostic-results?expand=diagnostic&type=TEXT").json();
		Assertions.assertEquals(1, expanded.get("count").asInt());
		JsonNode diagnostic = expanded.get("results").get(0).get("diagnostic");
		Assertions.assertEquals("show version", diagnostic.get("command").asText());
		Assertions.assertEquals(this.core.id(), diagnostic.get("targetGroup").get("id").asLong());

		Assertions.assertEquals(List.of("Flag"), values(this.reader.get("/devices/" + device.id() + "/diagnostic-results?diagnostic=" + flag).json(),
			"display"));
		Assertions.assertEquals(List.of("Version", "Flag", "Counter"), values(
			this.reader.get("/devices/" + device.id() + "/diagnostic-results?sort=-diagnosticName").json(), "display"));
		Assertions.assertEquals(List.of("Counter"), values(
			this.reader.get("/devices/" + device.id() + "/diagnostic-results?type=NUMERIC&lastCheckDate__gt=2000-01-01").json(), "display"));
		Assertions.assertEquals(400, this.reader.get("/devices/" + device.id() + "/diagnostic-results?type=OTHER").status());

		// Another device has no result of this one
		Assertions.assertEquals(0, this.reader.get("/devices/" + this.router1.id() + "/diagnostic-results").json().get("count").asInt());
		Assertions.assertEquals(404, this.reader.get("/devices/" + TestServer.UNKNOWN_ID + "/diagnostic-results").status());

		// Deleting a diagnostic deletes its results
		Assertions.assertEquals(204, this.admin.delete("/diagnostics/" + count).status());
		Assertions.assertEquals(2, this.reader.get("/devices/" + device.id() + "/diagnostic-results").json().get("count").asInt());
	}
}
