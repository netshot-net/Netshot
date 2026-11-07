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
 * The hardware compliance rules of the v1 API, over HTTP.
 */
@ApiTest
@V1Endpoints({
	"GET /compliance/hardware-rules",
	"GET /compliance/hardware-rules/{id}",
	"POST /compliance/hardware-rules",
	"PUT /compliance/hardware-rules/{id}",
	"PATCH /compliance/hardware-rules/{id}",
	"DELETE /compliance/hardware-rules/{id}"
})
@DisplayName("v1 hardware rules")
@ResourceLock("DB")
public class V1HardwareRuleResourceTest {

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
		TestWiring.get().transactions().run(dsl -> dsl.deleteFrom(Tables.HARDWARE_RULE).execute());
		this.core = TestDevices.addStaticGroup("Core " + System.nanoTime());
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
	@DisplayName("List: brief view, filters, search, group reference")
	void list() throws Exception {
		Assertions.assertEquals(201, this.admin.post("/compliance/hardware-rules", """
			{"partNumber": "WS-C3750", "endOfSale": "2030-01-01T00:00:00Z", "endOfLife": "2035-01-01T00:00:00Z"}""").status());
		Assertions.assertEquals(201, this.admin.post("/compliance/hardware-rules", """
			{"partNumber": "N9K-.*", "partNumberRegExp": true, "driver": "CiscoIOS12", "group": %d,
			"endOfLife": "2031-01-01T00:00:00Z"}""".formatted(this.core.id())).status());

		JsonNode page = this.reader.get("/compliance/hardware-rules?sort=partNumber").json();
		Assertions.assertEquals(2, page.get("results").size());
		Assertions.assertEquals(List.of("N9K-.*", "WS-C3750"), values(page, "partNumber"));
		Assertions.assertEquals(Set.of("id", "url", "display", "partNumber", "endOfSale", "endOfLife", "group", "driver", "family",
			"familyRegExp", "partNumberRegExp"), keys(page.get("results").get(1)));
		Assertions.assertEquals(List.of("WS-C3750"), values(this.reader.get("/compliance/hardware-rules?q=3750").json(), "partNumber"));
		Assertions.assertEquals(List.of("N9K-.*"), values(this.reader.get("/compliance/hardware-rules?driver=CiscoIOS12").json(), "partNumber"));
		Assertions.assertEquals(List.of("N9K-.*"),
			values(this.reader.get("/compliance/hardware-rules?group=" + this.core.id()).json(), "partNumber"));
		Assertions.assertEquals(List.of("WS-C3750"),
			values(this.reader.get("/compliance/hardware-rules?endOfSale__gt=2029-01-01T00:00:00Z").json(), "partNumber"));
		Assertions.assertEquals(List.of("N9K-.*"), values(this.reader.get("/compliance/hardware-rules?partNumberRegExp=true").json(), "partNumber"));
		Assertions.assertEquals(400, this.reader.get("/compliance/hardware-rules?endOfLife=yesterday").status());

		JsonNode withGroup = this.reader.get("/compliance/hardware-rules?fields=partNumber,group&partNumberRegExp=true").json()
			.get("results").get(0);
		Assertions.assertEquals(this.core.id(), withGroup.get("group").get("id").asLong());
	}

	@Test
	@DisplayName("Create, read, replace, patch and delete")
	void lifecycle() throws Exception {
		Answer created = this.admin.post("/compliance/hardware-rules", """
			{"partNumber": "WS-C3750", "endOfSale": "2030-01-01T00:00:00Z", "group": %d}""".formatted(this.core.id()));
		Assertions.assertEquals(201, created.status(), created.toString());
		long id = created.json().get("id").asLong();
		Assertions.assertEquals("/api/v1/compliance/hardware-rules/" + id, created.header("Location"));
		Assertions.assertEquals(this.core.id(), created.json().get("group").get("id").asLong());
		Assertions.assertTrue(created.json().get("endOfLife").isNull());

		JsonNode read = this.admin.get("/compliance/hardware-rules/" + id).json();
		((ObjectNode) read).put("endOfLife", "2036-01-01T00:00:00Z");
		Answer replaced = this.admin.put("/compliance/hardware-rules/" + id, read.toString());
		Assertions.assertEquals(200, replaced.status(), replaced.toString());
		Assertions.assertTrue(replaced.json().get("endOfLife").asText().startsWith("2036-01-01"));
		Assertions.assertEquals(this.core.id(), replaced.json().get("group").get("id").asLong());

		Answer patched = this.admin.patch("/compliance/hardware-rules/" + id, "{\"group\": null, \"family\": \"Catalyst\", \"endOfSale\": null}");
		Assertions.assertEquals(200, patched.status(), patched.toString());
		Assertions.assertTrue(patched.json().get("group").isNull());
		Assertions.assertTrue(patched.json().get("endOfSale").isNull());
		Assertions.assertEquals("Catalyst", patched.json().get("family").asText());

		Assertions.assertEquals(400, this.admin.post("/compliance/hardware-rules", "{\"group\": %d}".formatted(TestServer.UNKNOWN_ID)).status());
		Assertions.assertEquals(400, this.admin.post("/compliance/hardware-rules", "{\"driver\": \"NoSuchDriver\"}").status());
		Assertions.assertEquals(400, this.admin.post("/compliance/hardware-rules", "{\"endOfLife\": \"soon\"}").status());
		Assertions.assertEquals(404, this.reader.get("/compliance/hardware-rules/" + TestServer.UNKNOWN_ID).status());
		Assertions.assertEquals(404, this.admin.put("/compliance/hardware-rules/" + TestServer.UNKNOWN_ID, "{}").status());
		Assertions.assertEquals(404, this.admin.patch("/compliance/hardware-rules/" + TestServer.UNKNOWN_ID, "{}").status());
		Assertions.assertEquals(403, this.reader.post("/compliance/hardware-rules", "{}").status());
		Assertions.assertEquals(403, this.reader.put("/compliance/hardware-rules/" + id, "{}").status());
		Assertions.assertEquals(403, this.reader.delete("/compliance/hardware-rules/" + id).status());

		Assertions.assertEquals(204, this.admin.delete("/compliance/hardware-rules/" + id).status());
		Assertions.assertEquals(404, this.admin.delete("/compliance/hardware-rules/" + id).status());
	}
}
