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
package net.netshot.netshot.api.rest.v1.group;

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
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * The device groups of the v1 API, over HTTP.
 */
@ApiTest
@DisplayName("v1 groups")
@ResourceLock("DB")
public class V1GroupResourceTest {

	private V1Client admin;
	private V1Client reader;
	private Device router1;
	private Device router2;
	private Device router3;
	private StaticDeviceGroup core;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.admin = new V1Client(server, UiUser.LEVEL_ADMIN);
		this.reader = new V1Client(server, UiUser.LEVEL_READONLY);
		TestDevices.deleteAll();
		Domain domain = TestDevices.addDomain("Paris", "Paris DC");
		this.router1 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1).toBuilder().location("Paris").build());
		this.router2 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 2).toBuilder().location("Paris").build());
		this.router3 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 3).toBuilder().location("Lyon").build());
		this.core = TestDevices.addStaticGroup("Core", this.router1, this.router2);
		TestDevices.addDynamicGroup("Lyon devices", "[Location] is \"Lyon\"");
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

	private List<String> deviceNames(long groupId) throws Exception {
		Answer answer = this.admin.get("/groups/" + groupId + "/devices?sort=name");
		Assertions.assertEquals(200, answer.status(), answer.toString());
		return values(answer.json(), "name");
	}

	@Test
	@DisplayName("List: brief view, filters (type, name, device membership), devices of a group")
	void list() throws Exception {
		JsonNode page = this.reader.get("/groups?sort=name").json();
		Assertions.assertEquals(2, page.get("count").asInt());
		Assertions.assertEquals(List.of("Core", "Lyon devices"), values(page, "name"));
		Assertions.assertEquals(Set.of("id", "url", "display", "name", "folder", "type"), keys(page.get("results").get(0)));
		Assertions.assertEquals(List.of("Lyon devices"), values(this.reader.get("/groups?type=DYNAMIC").json(), "name"));
		Assertions.assertEquals(List.of("Core"), values(this.reader.get("/groups?name__ic=OR").json(), "name"));
		Assertions.assertEquals(List.of("Core"), values(this.reader.get("/groups?device=" + this.router1.id()).json(), "name"));
		Assertions.assertEquals(List.of("Lyon devices"), values(this.reader.get("/groups?device=" + this.router3.id()).json(), "name"));
		Assertions.assertEquals(400, this.reader.get("/groups?type=OTHER").status());

		Assertions.assertEquals(List.of("router00001", "router00002"), this.deviceNames(this.core.id()));
		JsonNode filtered = this.reader.get("/groups/" + this.core.id() + "/devices?name__iew=2").json();
		Assertions.assertEquals(List.of("router00002"), values(filtered, "name"));
		Assertions.assertEquals(1, filtered.get("count").asInt());
		Assertions.assertEquals(404, this.reader.get("/groups/" + TestServer.UNKNOWN_ID + "/devices").status());
	}

	@Test
	@DisplayName("Create, replace, patch and delete; the devices of a static group are kept unless given")
	void lifecycle() throws Exception {
		Answer created = this.admin.post("/groups", """
			{"name": "Edge", "type": "STATIC", "folder": "Network", "devices": [%d, %d]}"""
			.formatted(this.router3.id(), this.router1.id()));
		Assertions.assertEquals(201, created.status(), created.toString());
		JsonNode group = created.json();
		long id = group.get("id").asLong();
		Assertions.assertEquals("/api/v1/groups/" + id, created.header("Location"));
		Assertions.assertEquals("\"0\"", created.header("ETag"));
		Assertions.assertEquals("STATIC", group.get("type").asText());
		Assertions.assertFalse(group.has("devices"), "The devices are write-only");
		Assertions.assertEquals(List.of("router00001", "router00003"), this.deviceNames(id));

		// GET -> PUT round trip: the devices are unchanged
		JsonNode read = this.admin.get("/groups/" + id).json();
		((com.fasterxml.jackson.databind.node.ObjectNode) read).put("folder", "Network/Edge");
		Answer replaced = this.admin.put("/groups/" + id, read.toString(), "If-Match", "\"0\"");
		Assertions.assertEquals(200, replaced.status(), replaced.toString());
		Assertions.assertEquals("Network/Edge", replaced.json().get("folder").asText());
		Assertions.assertEquals(List.of("router00001", "router00003"), this.deviceNames(id));

		// The devices given replace the members
		Answer patched = this.admin.patch("/groups/" + id, "{\"devices\": [%d]}".formatted(this.router2.id()));
		Assertions.assertEquals(200, patched.status(), patched.toString());
		Assertions.assertEquals(List.of("router00002"), this.deviceNames(id));
		Assertions.assertEquals(2, patched.json().get("version").asInt());

		// The type can't change, an unknown device is refused, the version is checked
		Assertions.assertEquals(400, this.admin.patch("/groups/" + id, "{\"type\": \"DYNAMIC\"}").status());
		Assertions.assertEquals(400, this.admin.patch("/groups/" + id, "{\"devices\": [%d]}".formatted(TestServer.UNKNOWN_ID)).status());
		Assertions.assertEquals(412, this.admin.patch("/groups/" + id, "{\"folder\": \"X\"}", "If-Match", "\"0\"").status());
		Assertions.assertEquals(409, this.admin.post("/groups", "{\"name\": \"Edge\", \"type\": \"STATIC\"}").status());
		Assertions.assertEquals(400, this.admin.post("/groups", "{\"name\": \"X\"}").status());
		Assertions.assertEquals(403, this.reader.post("/groups", "{\"name\": \"X\", \"type\": \"STATIC\"}").status());

		Assertions.assertEquals(412, this.admin.delete("/groups/" + id, "If-Match", "\"0\"").status());
		Assertions.assertEquals(204, this.admin.delete("/groups/" + id, "If-Match", "\"2\"").status());
		Assertions.assertEquals(404, this.admin.delete("/groups/" + id).status());
	}

	@Test
	@DisplayName("Dynamic group: the members follow the query")
	void dynamic() throws Exception {
		Answer created = this.admin.post("/groups", """
			{"name": "Paris devices", "type": "DYNAMIC", "query": "[Location] is \\"Paris\\""}""");
		Assertions.assertEquals(201, created.status(), created.toString());
		long id = created.json().get("id").asLong();
		Assertions.assertEquals(List.of("router00001", "router00002"), this.deviceNames(id));
		Answer patched = this.admin.patch("/groups/" + id, "{\"query\": \"[Location] is \\\"Lyon\\\"\"}");
		Assertions.assertEquals(200, patched.status(), patched.toString());
		Assertions.assertEquals(List.of("router00003"), this.deviceNames(id));
		Answer invalid = this.admin.patch("/groups/" + id, "{\"query\": \"[Nothing] is 1\"}");
		Assertions.assertEquals(400, invalid.status(), invalid.toString());
	}
}
