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
package net.netshot.netshot.api.rest.v1.compliance;

import java.time.Instant;
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
import net.netshot.netshot.compliance.JavaScriptRule;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.PythonRule;
import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestCompliance;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.v1.V1Client;
import net.netshot.netshot.testing.api.v1.V1Client.Answer;
import net.netshot.netshot.testing.api.v1.V1Endpoints;

/**
 * The compliance rules of the v1 API, over HTTP.
 */
@ApiTest
@V1Endpoints({
	"GET /compliance/rules",
	"GET /compliance/rules/{id}",
	"POST /compliance/rules",
	"PUT /compliance/rules/{id}",
	"PATCH /compliance/rules/{id}",
	"DELETE /compliance/rules/{id}",
	"GET /compliance/rules/{id}/exemptions",
	"PUT /compliance/rules/{id}/exemptions",
	"POST /compliance/rules/test"
})
@DisplayName("v1 compliance rules")
@ResourceLock("DB")
public class V1RuleResourceTest {

	private V1Client admin;
	private V1Client reader;
	private Policy policy;
	private Policy other;
	private Device router1;
	private Device router2;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.admin = new V1Client(server, UiUser.LEVEL_ADMIN);
		this.reader = new V1Client(server, UiUser.LEVEL_READONLY);
		TestWiring.get().transactions().run(dsl -> {
			dsl.deleteFrom(Tables.RULE).execute();
			dsl.deleteFrom(Tables.POLICY).execute();
		});
		TestDevices.deleteAll();
		Domain domain = TestDevices.addDomain("Paris", "Paris DC");
		this.router1 = TestDevices.addFakeCiscoIos(domain, 1);
		this.router2 = TestDevices.addFakeCiscoIos(domain, 2);
		this.policy = TestCompliance.policy("Security");
		this.other = TestCompliance.policy("Hygiene");
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

	@Test
	@DisplayName("List: brief view of the three kinds, filters, policy reference")
	void list() throws Exception {
		TestCompliance.rule(TextRule.builder("Text rule", this.policy.id()).field("runningConfig").text("telnet").build());
		TestCompliance.rule(JavaScriptRule.of("JS rule", this.policy.id()).toBuilder().enabled(true).build());
		TestCompliance.rule(PythonRule.of("Python rule", this.other.id()));

		JsonNode page = this.reader.get("/compliance/rules").json();
		Assertions.assertEquals(3, page.get("results").size());
		Assertions.assertEquals(List.of("Text rule", "JS rule", "Python rule"), values(page, "name"), "without sort: by ID");
		JsonNode js = page.get("results").get(1);
		Assertions.assertEquals(Set.of("id", "url", "display", "type", "name", "enabled", "policy"), keys(js));
		Assertions.assertEquals("JAVASCRIPT", js.get("type").asText());
		Assertions.assertEquals(this.policy.id(), js.get("policy").get("id").asLong());
		Assertions.assertEquals("Security", js.get("policy").get("name").asText());
		Assertions.assertEquals(Set.of("id", "url", "display", "name"), keys(js.get("policy")));

		Assertions.assertEquals(List.of("Text rule"), values(this.reader.get("/compliance/rules?type=TEXT").json(), "name"));
		Assertions.assertEquals(List.of("JS rule"), values(this.reader.get("/compliance/rules?enabled=true").json(), "name"));
		Assertions.assertEquals(List.of("Python rule"), values(this.reader.get("/compliance/rules?policy=" + this.other.id()).json(), "name"));
		Assertions.assertEquals(List.of("Text rule"), values(this.reader.get("/compliance/rules?q=telnet").json(), "name"));
		Assertions.assertEquals(List.of("Python rule"), values(this.reader.get("/compliance/rules?name__ic=PYTH").json(), "name"));
		Assertions.assertEquals(List.of("Text rule", "Python rule", "JS rule"), values(this.reader.get("/compliance/rules?sort=-name").json(), "name"));
		Assertions.assertEquals(400, this.reader.get("/compliance/rules?type=OTHER").status());

		JsonNode full = this.reader.get("/compliance/rules?view=full&type=TEXT").json().get("results").get(0);
		Assertions.assertEquals("runningConfig", full.get("field").asText());
		JsonNode expanded = this.reader.get("/compliance/rules?expand=policy&type=PYTHON").json().get("results").get(0);
		Assertions.assertTrue(expanded.get("policy").has("targetGroups"));
	}

	@Test
	@DisplayName("Create, read, replace, patch and delete rules of each kind")
	void lifecycle() throws Exception {
		Answer created = this.admin.post("/compliance/rules", """
			{"type": "TEXT", "name": "No telnet", "policy": %d, "field": "runningConfig", "text": "transport input telnet",
			"invert": true, "deviceDriver": "CiscoIOS12"}""".formatted(this.policy.id()));
		Assertions.assertEquals(201, created.status(), created.toString());
		JsonNode rule = created.json();
		long id = rule.get("id").asLong();
		Assertions.assertEquals("/api/v1/compliance/rules/" + id, created.header("Location"));
		Assertions.assertEquals("TEXT", rule.get("type").asText());
		Assertions.assertFalse(rule.get("enabled").asBoolean());
		Assertions.assertTrue(rule.get("invert").asBoolean());
		Assertions.assertEquals("CiscoIOS12", rule.get("deviceDriver").asText());

		// GET -> PUT round trip (the policy is sent back as an object)
		JsonNode read = this.admin.get("/compliance/rules/" + id).json();
		((ObjectNode) read).put("enabled", true);
		((ObjectNode) read).put("text", "transport input ssh");
		Answer replaced = this.admin.put("/compliance/rules/" + id, read.toString());
		Assertions.assertEquals(200, replaced.status(), replaced.toString());
		Assertions.assertTrue(replaced.json().get("enabled").asBoolean());
		Assertions.assertEquals("transport input ssh", replaced.json().get("text").asText());

		// PUT is a full replacement: the optional properties not given are cleared
		Answer cleared = this.admin.put("/compliance/rules/" + id, "{\"type\": \"TEXT\", \"name\": \"No telnet\", \"field\": \"x\"}");
		Assertions.assertEquals(200, cleared.status(), cleared.toString());
		Assertions.assertTrue(cleared.json().get("deviceDriver").isNull());
		Assertions.assertFalse(cleared.json().get("enabled").asBoolean());

		Answer patched = this.admin.patch("/compliance/rules/" + id, "{\"regExp\": true, \"text\": \"telnet.*\"}");
		Assertions.assertEquals(200, patched.status(), patched.toString());
		Assertions.assertTrue(patched.json().get("regExp").asBoolean());
		Assertions.assertEquals("x", patched.json().get("field").asText());

		// Script rules: the template is the default script
		Answer js = this.admin.post("/compliance/rules", "{\"type\": \"JAVASCRIPT\", \"name\": \"JS\", \"policy\": %d}".formatted(this.policy.id()));
		Assertions.assertEquals(201, js.status(), js.toString());
		Assertions.assertTrue(js.json().get("script").asText().contains("function check(device)"));
		Answer py = this.admin.post("/compliance/rules", "{\"type\": \"PYTHON\", \"name\": \"Py\", \"policy\": %d, \"enabled\": true, \"script\": \"x\"}"
			.formatted(this.policy.id()));
		Assertions.assertEquals(201, py.status(), py.toString());
		Assertions.assertTrue(py.json().get("enabled").asBoolean());
		Assertions.assertEquals("x", py.json().get("script").asText());

		// Errors
		long jsId = js.json().get("id").asLong();
		Assertions.assertEquals(400, this.admin.patch("/compliance/rules/" + jsId, "{\"type\": \"PYTHON\"}").status());
		Assertions.assertEquals(400, this.admin.put("/compliance/rules/" + jsId, "{\"type\": \"PYTHON\", \"name\": \"JS\"}").status());
		Assertions.assertEquals(400, this.admin.patch("/compliance/rules/" + jsId, "{\"policy\": %d}".formatted(this.other.id())).status());
		Assertions.assertEquals(400, this.admin.post("/compliance/rules", "{\"type\": \"JAVASCRIPT\", \"name\": \"No policy\"}").status());
		Assertions.assertEquals(400, this.admin.post("/compliance/rules",
			"{\"type\": \"JAVASCRIPT\", \"name\": \"X\", \"policy\": %d}".formatted(TestServer.UNKNOWN_ID)).status());
		Assertions.assertEquals(400, this.admin.post("/compliance/rules", "{\"name\": \"No type\", \"policy\": %d}".formatted(this.policy.id()))
			.status());
		Assertions.assertEquals(400, this.admin.post("/compliance/rules", "{\"type\": \"JAVASCRIPT\", \"policy\": %d}".formatted(this.policy.id()))
			.status());
		Assertions.assertEquals(409, this.admin.post("/compliance/rules", "{\"type\": \"JAVASCRIPT\", \"name\": \"JS\", \"policy\": %d}"
			.formatted(this.policy.id())).status());
		Assertions.assertEquals(404, this.reader.get("/compliance/rules/" + TestServer.UNKNOWN_ID).status());
		Assertions.assertEquals(404, this.admin.put("/compliance/rules/" + TestServer.UNKNOWN_ID, "{\"type\": \"PYTHON\", \"name\": \"X\"}").status());
		Assertions.assertEquals(404, this.admin.patch("/compliance/rules/" + TestServer.UNKNOWN_ID, "{\"name\": \"X\"}").status());
		Assertions.assertEquals(403, this.reader.post("/compliance/rules", "{\"type\": \"PYTHON\", \"name\": \"X\", \"policy\": 1}").status());
		Assertions.assertEquals(403, this.reader.put("/compliance/rules/" + id, "{\"type\": \"TEXT\", \"name\": \"X\"}").status());
		Assertions.assertEquals(403, this.reader.delete("/compliance/rules/" + id).status());

		Assertions.assertEquals(204, this.admin.delete("/compliance/rules/" + id).status());
		Assertions.assertEquals(404, this.admin.delete("/compliance/rules/" + id).status());
		Assertions.assertEquals(404, this.admin.get("/compliance/rules/" + id).status());
	}

	@Test
	@DisplayName("Exemptions: list, filters, replace the whole set")
	void exemptions() throws Exception {
		JavaScriptRule rule = TestCompliance.rule(JavaScriptRule.of("JS rule", this.policy.id()));
		String path = "/compliance/rules/" + rule.id() + "/exemptions";
		Assertions.assertEquals(0, this.reader.get(path).json().get("results").size());

		Instant in2030 = Instant.parse("2030-01-01T00:00:00Z");
		Instant in2040 = Instant.parse("2040-01-01T00:00:00Z");
		Answer put = this.admin.put(path, """
			{"exemptions": [{"device": %d, "expirationDate": "%s"}, {"device": %d, "expirationDate": "%s"}]}"""
			.formatted(this.router1.id(), in2030, this.router2.id(), in2040));
		Assertions.assertEquals(200, put.status(), put.toString());
		Assertions.assertEquals(2, put.json().get("results").size());

		JsonNode page = this.reader.get(path + "?sort=-expirationDate").json();
		Assertions.assertEquals(this.router2.id(), page.get("results").get(0).get("device").get("id").asLong());
		Assertions.assertEquals("router00002", page.get("results").get(0).get("device").get("name").asText());
		Assertions.assertEquals(Instant.parse("2040-01-01T00:00:00Z"), Instant.parse(page.get("results").get(0).get("expirationDate").asText()));
		Assertions.assertEquals(1, this.reader.get(path + "?device=" + this.router1.id()).json().get("results").size());
		Assertions.assertEquals(1, this.reader.get(path + "?limit=1").json().get("results").size());

		// The set given replaces the whole current one; the listed objects can be sent back
		JsonNode current = this.reader.get(path + "?device=" + this.router1.id()).json();
		Answer back = this.admin.put(path, "{\"exemptions\": " + current.get("results").toString() + "}");
		Assertions.assertEquals(200, back.status(), back.toString());
		Assertions.assertEquals(this.router1.id(), back.json().get("results").get(0).get("device").get("id").asLong());
		Assertions.assertEquals(1, back.json().get("results").size());

		Assertions.assertEquals(400, this.admin.put(path, "{\"exemptions\": [{\"device\": %d, \"expirationDate\": \"%s\"}]}"
			.formatted(TestServer.UNKNOWN_ID, in2030)).status());
		Assertions.assertEquals(400, this.admin.put(path, "{\"exemptions\": [{\"device\": %d, \"expirationDate\": \"%s\"}, {\"device\": %d, \"expirationDate\": \"%s\"}]}"
			.formatted(this.router1.id(), in2030, this.router1.id(), in2040)).status());
		Assertions.assertEquals(400, this.admin.put(path, "{\"exemptions\": [{\"device\": %d}]}".formatted(this.router1.id())).status());
		Assertions.assertEquals(1, this.reader.get(path).json().get("results").size());
		Assertions.assertEquals(403, this.reader.put(path, "{\"exemptions\": []}").status());
		Assertions.assertEquals(404, this.admin.put("/compliance/rules/" + TestServer.UNKNOWN_ID + "/exemptions", "{\"exemptions\": []}").status());
		Assertions.assertEquals(404, this.reader.get("/compliance/rules/" + TestServer.UNKNOWN_ID + "/exemptions").status());

		Assertions.assertEquals(0, this.admin.put(path, "{\"exemptions\": []}").json().get("results").size());
	}

	@Test
	@DisplayName("Test a rule definition against a device")
	void testRule() throws Exception {
		String conforming = """
			{"rule": {"type": "JAVASCRIPT", "name": "t", "script": "function check(device) { return CONFORMING; }"}, "device": %d}"""
			.formatted(this.router1.id());
		Answer ok = this.admin.post("/compliance/rules/test", conforming);
		Assertions.assertEquals(200, ok.status(), ok.toString());
		Assertions.assertEquals("CONFORMING", ok.json().get("result").asText());

		Answer nok = this.admin.post("/compliance/rules/test", """
			{"rule": {"type": "JAVASCRIPT", "name": "t", "script": "function check(device) { return NONCONFORMING; }"}, "device": %d}"""
			.formatted(this.router1.id()));
		Assertions.assertEquals(200, nok.status(), nok.toString());
		Assertions.assertEquals("NONCONFORMING", nok.json().get("result").asText());

		Answer text = this.admin.post("/compliance/rules/test", """
			{"rule": {"type": "TEXT", "name": "t", "field": "name", "text": "nothing-like-this"}, "device": %d}"""
			.formatted(this.router1.id()));
		Assertions.assertEquals(200, text.status(), text.toString());
		Assertions.assertEquals("NONCONFORMING", text.json().get("result").asText());

		Assertions.assertEquals(404, this.admin.post("/compliance/rules/test", conforming.replace(String.valueOf(this.router1.id()),
			String.valueOf(TestServer.UNKNOWN_ID))).status());
		Assertions.assertEquals(400, this.admin.post("/compliance/rules/test", "{\"device\": %d}".formatted(this.router1.id())).status());
		Assertions.assertEquals(400, this.admin.post("/compliance/rules/test", "{\"rule\": {\"type\": \"NOPE\", \"name\": \"t\"}, \"device\": 1}")
			.status());
		Assertions.assertEquals(403, this.reader.post("/compliance/rules/test", conforming).status());
		int stored = TestWiring.get().transactions().read(dsl -> dsl.fetchCount(Tables.RULE));
		Assertions.assertEquals(0, stored, "A tested rule isn't stored");
	}
}
