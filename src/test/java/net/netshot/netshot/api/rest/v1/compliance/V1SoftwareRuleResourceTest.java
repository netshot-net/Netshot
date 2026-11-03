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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.api.V1Endpoints;

/**
 * The software compliance rules of the v1 API, over HTTP.
 */
@ApiTest
@V1Endpoints({
	"GET /compliance/software-rules",
	"GET /compliance/software-rules/{id}",
	"POST /compliance/software-rules",
	"PUT /compliance/software-rules/{id}",
	"PATCH /compliance/software-rules/{id}",
	"DELETE /compliance/software-rules/{id}"
})
@DisplayName("v1 software rules")
@ResourceLock("DB")
public class V1SoftwareRuleResourceTest {

	private V1Client admin;
	private V1Client reader;
	private StaticDeviceGroup core;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.admin = new V1Client(server, UiUser.LEVEL_ADMIN);
		this.reader = new V1Client(server, UiUser.LEVEL_READONLY);
		TestWiring.get().transactions().run(dsl -> dsl.deleteFrom(Tables.SOFTWARE_RULE).execute());
		this.core = TestDevices.addStaticGroup("Core " + System.nanoTime());
	}

	private long create(String level, String version, Integer priority) throws Exception {
		Answer answer = this.admin.post("/compliance/software-rules", """
			{"level": "%s", "version": "%s"%s}""".formatted(level, version, priority == null ? "" : ", \"priority\": " + priority));
		Assertions.assertEquals(201, answer.status(), answer.toString());
		return answer.json().get("id").asLong();
	}

	// The versions of the rules, in their order
	private List<String> versions() throws Exception {
		List<String> versions = new ArrayList<>();
		this.reader.get("/compliance/software-rules?view=full").json().get("results")
			.forEach(rule -> versions.add(rule.get("version").asText() + ":" + rule.get("priority").asInt()));
		return versions;
	}

	private static Set<String> keys(JsonNode object) {
		Set<String> keys = new TreeSet<>();
		object.fieldNames().forEachRemaining(keys::add);
		return keys;
	}

	@Test
	@DisplayName("List: brief view in order of priority, filters, group reference")
	void list() throws Exception {
		this.create("GOLD", "1", null);
		this.create("SILVER", "2", null);
		Answer withGroup = this.admin.post("/compliance/software-rules", """
			{"level": "BRONZE", "version": "3", "group": %d, "driver": "CiscoIOS12", "familyRegExp": true}""".formatted(this.core.id()));
		Assertions.assertEquals(201, withGroup.status(), withGroup.toString());

		JsonNode page = this.reader.get("/compliance/software-rules").json();
		Assertions.assertEquals(3, page.get("results").size());
		JsonNode first = page.get("results").get(0);
		Assertions.assertEquals(Set.of("id", "url", "display", "priority", "level", "group", "driver", "family", "familyRegExp",
			"version", "versionRegExp", "partNumber", "partNumberRegExp"), keys(first));
		Assertions.assertEquals(1, first.get("priority").asInt());
		Assertions.assertEquals(3, page.get("results").get(2).get("priority").asInt());
		Assertions.assertEquals(this.core.id(), page.get("results").get(2).get("group").get("id").asLong());
		Assertions.assertEquals(List.of("1:1", "2:2", "3:3"), this.versions());

		Assertions.assertEquals(1, this.reader.get("/compliance/software-rules?level=SILVER").json().get("results").size());
		Assertions.assertEquals(1, this.reader.get("/compliance/software-rules?group=" + this.core.id()).json().get("results").size());
		Assertions.assertEquals(1, this.reader.get("/compliance/software-rules?driver=CiscoIOS12").json().get("results").size());
		Assertions.assertEquals(1, this.reader.get("/compliance/software-rules?priority=2").json().get("results").size());
		Assertions.assertEquals(2, this.reader.get("/compliance/software-rules?priority__gt=1").json().get("results").size());
		Assertions.assertEquals(3, this.reader.get("/compliance/software-rules?sort=-priority").json().get("results").get(0).get("priority")
			.asInt());
		Assertions.assertEquals(1, this.reader.get("/compliance/software-rules?q=2").json().get("results").size());
		Assertions.assertEquals(400, this.reader.get("/compliance/software-rules?level=PLATINUM").status());
	}

	@Test
	@DisplayName("Priority: a new rule goes last or at the given position, PUT and PATCH move a rule")
	void priority() throws Exception {
		long a = this.create("GOLD", "a", null);
		long b = this.create("GOLD", "b", null);
		long c = this.create("GOLD", "c", null);
		Assertions.assertEquals(List.of("a:1", "b:2", "c:3"), this.versions());

		this.create("GOLD", "d", 2);
		Assertions.assertEquals(List.of("a:1", "d:2", "b:3", "c:4"), this.versions());
		this.create("GOLD", "e", 99);
		Assertions.assertEquals(List.of("a:1", "d:2", "b:3", "c:4", "e:5"), this.versions());

		Answer moved = this.admin.patch("/compliance/software-rules/" + c, "{\"priority\": 1}");
		Assertions.assertEquals(200, moved.status(), moved.toString());
		Assertions.assertEquals(1, moved.json().get("priority").asInt());
		Assertions.assertEquals(List.of("c:1", "a:2", "d:3", "b:4", "e:5"), this.versions());

		JsonNode read = this.admin.get("/compliance/software-rules/" + a).json();
		((ObjectNode) read).put("priority", 4);
		((ObjectNode) read).put("version", "a2");
		Answer replaced = this.admin.put("/compliance/software-rules/" + a, read.toString());
		Assertions.assertEquals(200, replaced.status(), replaced.toString());
		Assertions.assertEquals(List.of("c:1", "d:2", "b:3", "a2:4", "e:5"), this.versions());

		// Without priority, a rule keeps its position
		Answer kept = this.admin.put("/compliance/software-rules/" + b, "{\"level\": \"SILVER\", \"version\": \"b2\"}");
		Assertions.assertEquals(3, kept.json().get("priority").asInt());
		Assertions.assertEquals("SILVER", kept.json().get("level").asText());
		Assertions.assertEquals(3, this.admin.patch("/compliance/software-rules/" + b, "{\"version\": \"b3\"}").json().get("priority").asInt());

		// A deleted rule leaves no gap
		Assertions.assertEquals(204, this.admin.delete("/compliance/software-rules/" + c).status());
		Assertions.assertEquals(List.of("d:1", "b3:2", "a2:3", "e:4"), this.versions());

		Assertions.assertEquals(400, this.admin.patch("/compliance/software-rules/" + a, "{\"priority\": 0}").status());
		Assertions.assertEquals(400, this.admin.post("/compliance/software-rules", "{\"level\": \"GOLD\", \"priority\": -1}").status());
	}

	@Test
	@DisplayName("CRUD errors: validation, 404, 403, group and driver references")
	void errors() throws Exception {
		long id = this.create("GOLD", "1", null);
		Answer one = this.reader.get("/compliance/software-rules/" + id + "?expand=group");
		Assertions.assertEquals(200, one.status());
		Assertions.assertTrue(one.json().get("group").isNull() || !one.json().has("group"));
		Assertions.assertEquals("", one.json().get("family").asText());
		Assertions.assertEquals(400, this.admin.post("/compliance/software-rules", "{\"version\": \"1\"}").status());
		Assertions.assertEquals(400, this.admin.post("/compliance/software-rules", "{\"level\": \"GOLD\", \"group\": %d}".formatted(TestServer.UNKNOWN_ID))
			.status());
		Assertions.assertEquals(400, this.admin.post("/compliance/software-rules", "{\"level\": \"GOLD\", \"driver\": \"NoSuchDriver\"}").status());
		Assertions.assertEquals(400, this.admin.post("/compliance/software-rules", "{\"level\": \"NOPE\"}").status());
		Assertions.assertEquals(404, this.reader.get("/compliance/software-rules/" + TestServer.UNKNOWN_ID).status());
		Assertions.assertEquals(404, this.admin.put("/compliance/software-rules/" + TestServer.UNKNOWN_ID, "{\"level\": \"GOLD\"}").status());
		Assertions.assertEquals(404, this.admin.patch("/compliance/software-rules/" + TestServer.UNKNOWN_ID, "{\"version\": \"1\"}").status());
		Assertions.assertEquals(404, this.admin.delete("/compliance/software-rules/" + TestServer.UNKNOWN_ID).status());
		Assertions.assertEquals(403, this.reader.post("/compliance/software-rules", "{\"level\": \"GOLD\"}").status());
		Assertions.assertEquals(403, this.reader.put("/compliance/software-rules/" + id, "{\"level\": \"GOLD\"}").status());
		Assertions.assertEquals(403, this.reader.patch("/compliance/software-rules/" + id, "{}").status());
		Assertions.assertEquals(403, this.reader.delete("/compliance/software-rules/" + id).status());
	}
}
