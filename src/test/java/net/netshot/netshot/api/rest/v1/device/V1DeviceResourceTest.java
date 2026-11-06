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
package net.netshot.netshot.api.rest.v1.device;

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

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Endpoints;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * The devices of the v1 API, over HTTP: the filters, the search query, the views and the references.
 */
@ApiTest
@V1Endpoints({
	"GET /devices",
	"GET /devices/{id}",
	"DELETE /devices/{id}"
})
@DisplayName("v1 devices")
@ResourceLock("DB")
public class V1DeviceResourceTest {

	private V1Client admin;
	private Domain paris;
	private Domain lyon;
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
		TestDevices.deleteAll();
		this.paris = TestDevices.addDomain(Domain.builder().name("Paris-" + System.nanoTime()).description("Paris DC")
			.server4Address(new Network4Address("10.1.1.1")).build());
		this.lyon = TestDevices.addDomain(Domain.builder().name("Lyon-" + System.nanoTime()).description("Lyon DC")
			.server4Address(new Network4Address("10.2.2.2")).build());
		this.router1 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(this.paris, null, 1).toBuilder()
			.tags(Set.of("core", "edge")).location("Paris 1").build());
		this.router2 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(this.paris, null, 2).toBuilder()
			.tags(Set.of("core")).location("Paris 2").family("Cisco ISR").build());
		this.router3 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(this.lyon, null, 3).toBuilder()
			.status(Device.Status.DISABLED).tags(Set.of()).location("Lyon").build());
		this.core = TestDevices.addStaticGroup("Core-" + System.nanoTime(), this.router1, this.router2);
	}

	private static List<String> names(JsonNode page) {
		List<String> names = new ArrayList<>();
		page.get("results").forEach(d -> names.add(d.get("name").asText()));
		return names;
	}

	private static Set<String> keys(JsonNode object) {
		Set<String> keys = new TreeSet<>();
		object.fieldNames().forEachRemaining(keys::add);
		return keys;
	}

	private List<String> list(String query) throws Exception {
		Answer answer = this.admin.get("/devices" + query);
		Assertions.assertEquals(200, answer.status(), query + " -> " + answer);
		return names(answer.json());
	}

	@Test
	@DisplayName("List: brief view by default, filters on fields, references, tags, groups, compliance")
	void listFilters() throws Exception {
		JsonNode page = this.admin.get("/devices").json();
		Assertions.assertEquals(3, page.get("results").size());
		Assertions.assertEquals(List.of("router00001", "router00002", "router00003"), names(page));
		Assertions.assertEquals(Set.of("id", "url", "display", "name", "mgmtAddress", "status", "family", "driver", "driverDescription",
			"networkClass", "softwareLevel", "compliant", "endOfLife", "endOfSale"), keys(page.get("results").get(0)));

		Assertions.assertEquals(List.of("router00001", "router00002"), this.list("?status=INPRODUCTION"));
		Assertions.assertEquals(List.of("router00003"), this.list("?status__n=INPRODUCTION"));
		Assertions.assertEquals(List.of("router00003"), this.list("?mgmtDomain=" + this.lyon.id()));
		Assertions.assertEquals(List.of("router00002"), this.list("?family__ic=isr"));
		Assertions.assertEquals(List.of("router00001", "router00002"), this.list("?location__isw=paris"));
		Assertions.assertEquals(List.of("router00001", "router00002"), this.list("?tag=core"));
		Assertions.assertEquals(List.of("router00001"), this.list("?tag=edge"));
		Assertions.assertEquals(List.of("router00003"), this.list("?tag__empty=true"));
		Assertions.assertEquals(List.of("router00002", "router00003"), this.list("?tag__n=edge"));
		Assertions.assertEquals(List.of("router00001", "router00002"), this.list("?group=" + this.core.id()));
		Assertions.assertEquals(List.of("router00003"), this.list("?group__n=" + this.core.id()));
		Assertions.assertEquals(3, this.list("?compliant=true").size());
		Assertions.assertEquals(List.of("router00002"), this.list("?mgmtAddress=172.16.0.2"));
		Assertions.assertEquals(List.of("router00003", "router00002", "router00001"), this.list("?sort=-name"));
		Assertions.assertEquals(List.of("router00002"), this.list("?q=isr"));
		Assertions.assertEquals(400, this.admin.get("/devices?status=BROKEN").status());
		Assertions.assertEquals(400, this.admin.get("/devices?color=red").status());
	}

	@Test
	@DisplayName("Snapshot dates: exposed, filterable (including never) and sortable (never last), not changed by a device update")
	void snapshotDates() throws Exception {
		TestDevices.setSnapshotDate(this.router1.id(), true, Instant.parse("2026-01-10T10:00:00Z"));
		TestDevices.setSnapshotDate(this.router2.id(), true, Instant.parse("2026-03-10T10:00:00Z"));
		TestDevices.setSnapshotDate(this.router2.id(), false, Instant.parse("2026-04-10T10:00:00Z"));

		JsonNode device = this.admin.get("/devices/" + this.router2.id()).json();
		Assertions.assertEquals(Instant.parse("2026-03-10T10:00:00Z"), Instant.parse(device.get("lastSnapshotSuccessDate").asText()));
		Assertions.assertEquals(Instant.parse("2026-04-10T10:00:00Z"), Instant.parse(device.get("lastSnapshotFailureDate").asText()));
		Assertions.assertTrue(this.admin.get("/devices/" + this.router1.id()).json().get("lastSnapshotFailureDate").isNull());

		Assertions.assertEquals(List.of("router00001"), this.list("?status=INPRODUCTION&lastSnapshotSuccessDate__lt=2026-02-01T00:00:00Z"));
		Assertions.assertEquals(List.of("router00003"), this.list("?lastSnapshotSuccessDate__empty=true"));
		Assertions.assertEquals(List.of("router00002"), this.list("?lastSnapshotFailureDate__gt=2026-04-01T00:00:00Z"));
		Assertions.assertEquals(List.of("router00001", "router00002", "router00003"), this.list("?sort=lastSnapshotSuccessDate"));
		Assertions.assertEquals(List.of("router00002", "router00001"), this.list("?status=INPRODUCTION&sort=-lastSnapshotSuccessDate"));
		// The devices never snapshotted come last, in both directions
		Assertions.assertEquals(List.of("router00002", "router00001", "router00003"), this.list("?sort=-lastSnapshotSuccessDate"));
		Assertions.assertEquals(List.of("router00001", "router00002"),
			this.list("?lastSnapshotSuccessDate__empty=false&sort=lastSnapshotSuccessDate"));

		Device edited = TestDevices.save(TestDevices.find(this.router2.id()).toBuilder().comments("edited").build());
		Assertions.assertEquals(Instant.parse("2026-03-10T10:00:00Z"), edited.lastSnapshotSuccessDate());
		Assertions.assertEquals(Instant.parse("2026-04-10T10:00:00Z"), edited.lastSnapshotFailureDate());
	}

	@Test
	@DisplayName("List: the Netshot search query, combined with the filters")
	void listQuery() throws Exception {
		Assertions.assertEquals(List.of("router00002"), this.list("?query=%5BFamily%5D%20is%20%22Cisco%20ISR%22"));
		Assertions.assertEquals(List.of("router00001"),
			this.list("?query=%5BLocation%5D%20contains%20%22Paris%22&tag=edge"));
		Answer invalid = this.admin.get("/devices?query=%5BNothing%5D%20is%201");
		Assertions.assertEquals(400, invalid.status(), invalid.toString());
		Assertions.assertEquals("NETSHOT_INVALID_SEARCH_STRING", invalid.json().get("code").asText());
		Assertions.assertEquals("query", invalid.json().get("errors").get(0).get("field").asText());
	}

	@Test
	@DisplayName("List: full view, fields, light query only when enough")
	void listViews() throws Exception {
		JsonNode full = this.admin.get("/devices?view=full&name=router00001").json().get("results").get(0);
		Assertions.assertEquals("Paris 1", full.get("location").asText());
		Assertions.assertEquals(Set.of("id", "url", "display", "name", "description", "server4Address"), keys(full.get("mgmtDomain")));
		Assertions.assertEquals(3, full.get("attributes").size());
		Assertions.assertTrue(full.get("ownerGroups").get(0).has("folder"));
		Assertions.assertFalse(full.get("ownerGroups").get(0).has("hiddenFromReports"));

		JsonNode light = this.admin.get("/devices?fields=name,family,driverDescription&name=router00002").json().get("results").get(0);
		Assertions.assertEquals(Set.of("id", "name", "family", "driverDescription"), keys(light));
		Assertions.assertEquals("Cisco ISR", light.get("family").asText());

		JsonNode located = this.admin.get("/devices?fields=name,location,mgmtDomain.description&name=router00003").json()
			.get("results").get(0);
		Assertions.assertEquals("Lyon", located.get("location").asText());
		Assertions.assertEquals(Set.of("id", "description"), keys(located.get("mgmtDomain")));
		Assertions.assertEquals("Lyon DC", located.get("mgmtDomain").get("description").asText());

		// An expanded reference is written even when the view or the fields don't include it
		JsonNode brief = this.admin.get("/devices?expand=mgmtDomain&name=router00001").json().get("results").get(0);
		Assertions.assertEquals("Paris DC", brief.get("mgmtDomain").get("description").asText());
		Assertions.assertTrue(brief.get("mgmtDomain").has("changeDate"), "full view of the domain");
		Assertions.assertFalse(brief.has("location"), "brief view of the device");
		JsonNode named = this.admin.get("/devices?fields=name&expand=mgmtDomain&name=router00001").json().get("results").get(0);
		Assertions.assertEquals(Set.of("id", "name", "mgmtDomain"), keys(named));
		Assertions.assertTrue(named.get("mgmtDomain").has("changeDate"));
	}

	@Test
	@DisplayName("Get: full representation, expand, omit, ETag, 404")
	void get() throws Exception {
		Answer answer = this.admin.get("/devices/" + this.router1.id());
		Assertions.assertEquals(200, answer.status(), answer.toString());
		Assertions.assertNotNull(answer.header("ETag"));
		JsonNode device = answer.json();
		Assertions.assertEquals("router00001", device.get("name").asText());
		Assertions.assertEquals("/api/v1/devices/" + this.router1.id(), device.get("url").asText());
		Assertions.assertEquals(Set.of("id", "url", "display", "name", "description", "server4Address"), keys(device.get("mgmtDomain")));
		Assertions.assertEquals(List.of("core", "edge"), List.of(device.get("tags").get(0).asText(), device.get("tags").get(1).asText()));
		Assertions.assertEquals("TEXT", device.get("attributes").get(1).get("type").asText());
		Assertions.assertEquals("0x1202", device.get("attributes").get(1).get("value").asText());
		JsonNode access = device.get("accesses").get(0);
		Assertions.assertEquals("SSH", access.get("specificCredentialSet").get("type").asText());
		Assertions.assertEquals("user", access.get("specificCredentialSet").get("username").asText());
		Assertions.assertFalse(access.toString().contains("\"pass\""), "secret written: " + access);
		Assertions.assertTrue(access.get("specificCredentialSet").get("hasPassword").asBoolean());

		JsonNode expanded = this.admin.get("/devices/" + this.router1.id() + "?expand=mgmtDomain,ownerGroups").json();
		Assertions.assertEquals("Paris DC", expanded.get("mgmtDomain").get("description").asText());
		Assertions.assertTrue(expanded.get("ownerGroups").get(0).has("hiddenFromReports"));

		JsonNode omitted = this.admin.get("/devices/" + this.router1.id() + "?omit=attributes,accesses,options").json();
		Assertions.assertFalse(omitted.has("attributes"));
		Assertions.assertTrue(omitted.has("location"));

		Assertions.assertEquals(400, this.admin.get("/devices/" + this.router1.id() + "?expand=location").status());
		Assertions.assertEquals(400, this.admin.get("/devices/" + this.router1.id() + "?omit=mgmtDomain&expand=mgmtDomain").status());
		Answer missing = this.admin.get("/devices/" + TestServer.UNKNOWN_ID);
		Assertions.assertEquals(404, missing.status());
		Assertions.assertEquals("NETSHOT_DEVICE_NOT_FOUND", missing.json().get("code").asText());
	}

	@Test
	@DisplayName("Delete: 204 then 404, permissions")
	void delete(TestServer server) throws Exception {
		Assertions.assertEquals(403, new V1Client(server, UiUser.LEVEL_READONLY).delete("/devices/" + this.router3.id()).status());
		Assertions.assertEquals(204, this.admin.delete("/devices/" + this.router3.id()).status());
		Assertions.assertEquals(404, this.admin.delete("/devices/" + this.router3.id()).status());
		Assertions.assertEquals(2, this.admin.get("/devices").json().get("results").size());
	}
}
