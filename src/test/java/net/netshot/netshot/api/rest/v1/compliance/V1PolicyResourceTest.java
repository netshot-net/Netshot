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
package net.netshot.netshot.api.rest.v1.compliance;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestCompliance;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.api.V1Endpoints;

/**
 * The compliance policies of the v1 API, over HTTP.
 */
@ApiTest
@V1Endpoints({
	"GET /compliance/policies",
	"GET /compliance/policies/{id}",
	"GET /compliance/policies/{id}/rules",
	"POST /compliance/policies",
	"PUT /compliance/policies/{id}",
	"PATCH /compliance/policies/{id}",
	"DELETE /compliance/policies/{id}"
})
@DisplayName("v1 compliance policies")
@ResourceLock("DB")
public class V1PolicyResourceTest {

	private V1Client admin;
	private V1Client reader;
	private StaticDeviceGroup core;
	private StaticDeviceGroup edge;

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.admin = new V1Client(server, UiUser.LEVEL_ADMIN);
		this.reader = new V1Client(server, UiUser.LEVEL_READONLY);
		TestWiring.get().transactions().run(dsl -> {
			dsl.deleteFrom(Tables.RULE).execute();
			dsl.deleteFrom(Tables.POLICY).execute();
		});
		TestDevices.deleteAll();
		this.core = TestDevices.addStaticGroup("Core " + System.nanoTime());
		this.edge = TestDevices.addStaticGroup("Edge " + System.nanoTime());
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
	@DisplayName("List: brief view, filters, search, group references, rules of a policy")
	void list() throws Exception {
		Policy security = TestCompliance.policy("Security", this.core.id());
		Policy hygiene = TestCompliance.policy("Hygiene", this.edge.id());
		TestCompliance.rule(TextRule.builder("No telnet", security.id()).build());
		TestCompliance.rule(TextRule.builder("Banner", security.id()).enabled(true).build());
		TestCompliance.rule(TextRule.builder("Other", hygiene.id()).build());

		JsonNode page = this.reader.get("/compliance/policies").json();
		Assertions.assertEquals(2, page.get("results").size());
		Assertions.assertEquals(List.of("Security", "Hygiene"), values(page, "name"), "without sort: by ID");
		Assertions.assertEquals(Set.of("id", "url", "display", "name"), keys(page.get("results").get(0)));
		Assertions.assertEquals(List.of("Security"), values(this.reader.get("/compliance/policies?name__ic=SECUR").json(), "name"));
		Assertions.assertEquals(List.of("Hygiene"),
			values(this.reader.get("/compliance/policies?targetGroup=" + this.edge.id()).json(), "name"));
		Assertions.assertEquals(List.of("Security", "Hygiene"), values(this.reader.get("/compliance/policies?sort=-name").json(), "name"));
		Assertions.assertEquals(List.of("Security"), values(this.reader.get("/compliance/policies?q=curi").json(), "name"));

		JsonNode withGroups = this.reader.get("/compliance/policies?fields=name,targetGroups&name=Security").json().get("results").get(0);
		Assertions.assertEquals(this.core.id(), withGroups.get("targetGroups").get(0).get("id").asLong());
		Assertions.assertEquals(Set.of("id", "url", "display", "name", "type", "folder"), keys(withGroups.get("targetGroups").get(0)));

		JsonNode rules = this.reader.get("/compliance/policies/" + security.id() + "/rules?sort=name").json();
		Assertions.assertEquals(List.of("Banner", "No telnet"), values(rules, "name"));
		Assertions.assertEquals(1, this.reader.get("/compliance/policies/" + security.id() + "/rules?enabled=true").json()
			.get("results").size());
		Assertions.assertEquals(404, this.reader.get("/compliance/policies/" + TestServer.UNKNOWN_ID + "/rules").status());
		Assertions.assertEquals(400, this.reader.get("/compliance/policies?sort=nothing").status());
	}

	@Test
	@DisplayName("Create, read, replace, patch and delete")
	void lifecycle() throws Exception {
		Answer created = this.admin.post("/compliance/policies", """
			{"name": "Security", "targetGroups": [%d]}""".formatted(this.core.id()));
		Assertions.assertEquals(201, created.status(), created.toString());
		JsonNode policy = created.json();
		long id = policy.get("id").asLong();
		Assertions.assertEquals("/api/v1/compliance/policies/" + id, created.header("Location"));
		Assertions.assertEquals(this.core.id(), policy.get("targetGroups").get(0).get("id").asLong());

		// GET -> PUT round trip (the groups are sent back as objects)
		JsonNode read = this.admin.get("/compliance/policies/" + id).json();
		((ObjectNode) read).put("name", "Security 2");
		Answer replaced = this.admin.put("/compliance/policies/" + id, read.toString());
		Assertions.assertEquals(200, replaced.status(), replaced.toString());
		Assertions.assertEquals("Security 2", replaced.json().get("name").asText());
		Assertions.assertEquals(1, replaced.json().get("targetGroups").size());

		Answer patched = this.admin.patch("/compliance/policies/" + id, "{\"targetGroups\": [%d, %d]}".formatted(this.core.id(), this.edge.id()));
		Assertions.assertEquals(200, patched.status(), patched.toString());
		Assertions.assertEquals(2, patched.json().get("targetGroups").size());
		Answer expanded = this.reader.get("/compliance/policies/" + id + "?expand=targetGroups");
		Assertions.assertTrue(expanded.json().get("targetGroups").get(0).has("hiddenFromReports"));

		Assertions.assertEquals(400, this.admin.patch("/compliance/policies/" + id, "{\"targetGroups\": [%d]}".formatted(TestServer.UNKNOWN_ID))
			.status());
		Assertions.assertEquals(400, this.admin.patch("/compliance/policies/" + id, "{\"name\": \" \"}").status());
		Assertions.assertEquals(409, this.admin.post("/compliance/policies", "{\"name\": \"Security 2\"}").status());
		Assertions.assertEquals(400, this.admin.post("/compliance/policies", "{}").status());
		Assertions.assertEquals(404, this.admin.put("/compliance/policies/" + TestServer.UNKNOWN_ID, "{\"name\": \"X\"}").status());
		Assertions.assertEquals(404, this.admin.patch("/compliance/policies/" + TestServer.UNKNOWN_ID, "{\"name\": \"X\"}").status());
		Assertions.assertEquals(404, this.reader.get("/compliance/policies/" + TestServer.UNKNOWN_ID).status());
		Assertions.assertEquals(403, this.reader.post("/compliance/policies", "{\"name\": \"X\"}").status());
		Assertions.assertEquals(403, this.reader.put("/compliance/policies/" + id, "{\"name\": \"X\"}").status());
		Assertions.assertEquals(403, this.reader.delete("/compliance/policies/" + id).status());

		Assertions.assertEquals(204, this.admin.delete("/compliance/policies/" + id).status());
		Assertions.assertEquals(404, this.admin.delete("/compliance/policies/" + id).status());
	}
}
