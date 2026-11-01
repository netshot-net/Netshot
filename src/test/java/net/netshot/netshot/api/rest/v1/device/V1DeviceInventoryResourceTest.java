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

import static net.netshot.netshot.platform.database.schema.Tables.MODULE;

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
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.api.V1Endpoints;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * The interfaces and the modules of the devices of the v1 API, over HTTP.
 */
@ApiTest
@V1Endpoints({
	"GET /devices/{id}/interfaces",
	"GET /devices/{id}/modules"
})
@DisplayName("v1 device interfaces and modules")
@ResourceLock("DB")
public class V1DeviceInventoryResourceTest {

	private V1Client reader;
	private Device router1;
	private Device router2;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.reader = new V1Client(server, UiUser.LEVEL_READONLY);
		TestDevices.deleteAll();
		Domain domain = TestDevices.addDomain("Paris-" + System.nanoTime(), "Paris DC");
		this.router1 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1));
		this.router2 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 2));
		TestWiring.get().transactions().run(dsl -> dsl.update(MODULE).set(MODULE.REMOVED, true)
			.where(MODULE.DEVICE.eq(this.router1.id()), MODULE.SLOT.eq("slot")).execute());
	}

	private static Set<String> keys(JsonNode object) {
		Set<String> keys = new TreeSet<>();
		object.fieldNames().forEachRemaining(keys::add);
		return keys;
	}

	private static List<String> values(JsonNode page, String property) {
		List<String> values = new ArrayList<>();
		page.get("results").forEach(item -> values.add(item.get(property).asText()));
		return values;
	}

	private List<String> interfaces(Device device, String query, String property) throws Exception {
		Answer answer = this.reader.get("/devices/%d/interfaces%s".formatted(device.id(), query));
		Assertions.assertEquals(200, answer.status(), query + " -> " + answer);
		return values(answer.json(), property);
	}

	private List<String> modules(Device device, String query, String property) throws Exception {
		Answer answer = this.reader.get("/devices/%d/modules%s".formatted(device.id(), query));
		Assertions.assertEquals(200, answer.status(), query + " -> " + answer);
		return values(answer.json(), property);
	}

	@Test
	@DisplayName("Interfaces: brief and full views, children have an id but no url")
	void interfaceViews() throws Exception {
		JsonNode page = this.reader.get("/devices/%d/interfaces".formatted(this.router1.id())).json();
		Assertions.assertEquals(2, page.get("count").asInt());
		Assertions.assertEquals(List.of("GigabitEthernet0/0", "GigabitEthernet0/1"), values(page, "name"));
		JsonNode first = page.get("results").get(0);
		Assertions.assertEquals(Set.of("id", "display", "name", "description", "enabled"), keys(first));
		Assertions.assertEquals("GigabitEthernet0/0", first.get("display").asText());
		Assertions.assertTrue(first.get("enabled").asBoolean());

		JsonNode full = this.reader.get("/devices/%d/interfaces?view=full&limit=1".formatted(this.router1.id())).json().get("results").get(0);
		Assertions.assertEquals(Set.of("id", "display", "name", "description", "enabled", "level3", "vrf", "virtualDevice",
			"macAddress", "ipAddresses"), keys(full));
		Assertions.assertEquals("VRF1", full.get("vrf").asText());
		Assertions.assertTrue(full.get("level3").asBoolean());
		Assertions.assertEquals(2, full.get("ipAddresses").size());
		JsonNode ip4 = full.get("ipAddresses").get(0);
		Assertions.assertEquals("10.0.1.1", ip4.get("ip").asText());
		Assertions.assertEquals(25, ip4.get("prefixLength").asInt());
		Assertions.assertEquals(4, ip4.get("version").asInt());
		Assertions.assertEquals("PRIMARY", ip4.get("usage").asText());
		Assertions.assertEquals(6, full.get("ipAddresses").get(1).get("version").asInt());
		Assertions.assertEquals("2016:1:1:1:0:0:0:1", full.get("ipAddresses").get(1).get("ip").asText());

		JsonNode fields = this.reader.get("/devices/%d/interfaces?fields=macAddress".formatted(this.router1.id())).json();
		Assertions.assertEquals(Set.of("id", "macAddress"), keys(fields.get("results").get(0)));
	}

	@Test
	@DisplayName("Interfaces: filters, search, sort, paging")
	void interfaceFilters() throws Exception {
		Assertions.assertEquals(List.of("GigabitEthernet0/1"), this.interfaces(this.router1, "?enabled=false", "name"));
		Assertions.assertEquals(List.of("GigabitEthernet0/0"), this.interfaces(this.router1, "?vrf=VRF1", "name"));
		Assertions.assertEquals(List.of("GigabitEthernet0/1"), this.interfaces(this.router1, "?name__iew=0/1", "name"));
		Assertions.assertEquals(List.of("GigabitEthernet0/1"), this.interfaces(this.router1, "?q=desc%20for%20interface%200/1", "name"));
		Assertions.assertEquals(List.of("GigabitEthernet0/0"), this.interfaces(this.router1, "?ipAddress=10.0.1.1", "name"));
		Assertions.assertEquals(List.of("GigabitEthernet0/1"), this.interfaces(this.router1, "?ipAddress__net=10.0.1.128/25", "name"));
		Assertions.assertEquals(List.of("GigabitEthernet0/0"), this.interfaces(this.router1, "?ipAddress=2016:1:1:1::1", "name"));
		Assertions.assertEquals(List.of("GigabitEthernet0/1"), this.interfaces(this.router1, "?ipAddress__n=10.0.1.1&ipAddress__net=10.0.1.0/24", "name"));
		Assertions.assertEquals(List.of("GigabitEthernet0/1", "GigabitEthernet0/0"), this.interfaces(this.router1, "?sort=-name", "name"));
		Assertions.assertEquals(List.of(), this.interfaces(this.router1, "?macAddress=00:11:22:33:44:55", "name"));
		Assertions.assertEquals(400, this.reader.get("/devices/%d/interfaces?macAddress=nope".formatted(this.router1.id())).status());
		Assertions.assertEquals(400, this.reader.get("/devices/%d/interfaces?ipAddress=nope".formatted(this.router1.id())).status());
		Assertions.assertEquals(400, this.reader.get("/devices/%d/interfaces?bogus=1".formatted(this.router1.id())).status());

		JsonNode page = this.reader.get("/devices/%d/interfaces?limit=1".formatted(this.router1.id())).json();
		Assertions.assertEquals(2, page.get("count").asInt());
		Assertions.assertEquals(1, page.get("results").size());
		Assertions.assertFalse(page.get("next").isNull());
		// Each device has its own interfaces
		Assertions.assertEquals(2, this.interfaces(this.router2, "", "id").size());
		Assertions.assertNotEquals(this.interfaces(this.router1, "", "id"), this.interfaces(this.router2, "", "id"));
	}

	@Test
	@DisplayName("Interfaces: unknown device")
	void interfaceUnknownDevice() throws Exception {
		Answer answer = this.reader.get("/devices/%d/interfaces".formatted(TestServer.UNKNOWN_ID));
		Assertions.assertEquals(404, answer.status(), answer.toString());
		Assertions.assertEquals("NETSHOT_DEVICE_NOT_FOUND", answer.json().get("code").asText());
	}

	@Test
	@DisplayName("Modules: current ones by default, removed ones with removed=true, views and filters")
	void modules() throws Exception {
		Assertions.assertEquals(List.of("chassis"), this.modules(this.router1, "", "slot"));
		JsonNode page = this.reader.get("/devices/%d/modules?removed=true".formatted(this.router1.id())).json();
		Assertions.assertEquals(2, page.get("count").asInt());
		Assertions.assertEquals(List.of("chassis", "slot"), values(page, "slot"));
		JsonNode first = page.get("results").get(0);
		Assertions.assertEquals(Set.of("id", "display", "slot", "partNumber", "serialNumber"), keys(first));
		Assertions.assertEquals("chassis (TESTCHASSIS)", first.get("display").asText());
		Assertions.assertEquals("16161616TEST16", first.get("serialNumber").asText());

		JsonNode full = this.reader.get("/devices/%d/modules?view=full&slot=slot&removed=true".formatted(this.router1.id())).json().get("results").get(0);
		Assertions.assertEquals(Set.of("id", "display", "slot", "partNumber", "serialNumber", "firstSeenDate", "lastSeenDate", "removed"),
			keys(full));
		Assertions.assertTrue(full.get("removed").asBoolean());
		Assertions.assertTrue(full.get("firstSeenDate").asText().endsWith("Z"), full.toString());

		Assertions.assertEquals(List.of("chassis"), this.modules(this.router1, "?removed=false", "slot"));
		Assertions.assertEquals(List.of("chassis", "slot"), this.modules(this.router1, "?removed=true", "slot"));
		Assertions.assertEquals(List.of("chassis"), this.modules(this.router1, "?q=TESTCHASSIS", "slot"));
		Assertions.assertEquals(List.of("slot"), this.modules(this.router1, "?partNumber__isw=testsl&removed=true", "slot"));
		Assertions.assertEquals(List.of("slot", "chassis"), this.modules(this.router1, "?sort=-slot&removed=true", "slot"));
		Assertions.assertEquals(List.of("chassis", "slot"), this.modules(this.router2, "?removed=false", "slot"));
		Assertions.assertEquals(List.of("chassis", "slot"), this.modules(this.router2, "?lastSeenDate__gte=2000-01-01", "slot"));
		Assertions.assertEquals(400, this.reader.get("/devices/%d/modules?removed=maybe".formatted(this.router1.id())).status());
		Assertions.assertEquals(404, this.reader.get("/devices/%d/modules".formatted(TestServer.UNKNOWN_ID)).status());
	}
}
