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
package net.netshot.netshot.api.rest.v1.device;

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
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.api.V1Endpoints;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * The reference lists built from the devices (families, tags, part numbers, software versions) and the device
 * types of the v1 API, over HTTP.
 */
@ApiTest
@V1Endpoints({
	"GET /device-families",
	"GET /device-tags",
	"GET /device-types",
	"POST /device-types/reload",
	"GET /part-numbers",
	"GET /software-versions"
})
@DisplayName("v1 device catalogs")
@ResourceLock("DB")
public class V1DeviceCatalogResourceTest {

	private V1Client reader;
	private V1Client admin;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.reader = new V1Client(server, UiUser.LEVEL_READONLY);
		this.admin = new V1Client(server, UiUser.LEVEL_ADMIN);
		TestDevices.deleteAll();
		Domain domain = TestDevices.addDomain("Paris-" + System.nanoTime(), "Paris DC");
		TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1).toBuilder()
			.tags(Set.of("core", "edge")).build());
		TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 2).toBuilder()
			.tags(Set.of("core")).family("Cisco ISR").build());
		TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 3).toBuilder()
			.tags(Set.of()).family("Cisco ISR").softwareVersion("17.3.1").build());
	}

	private static Set<String> keys(JsonNode object) {
		Set<String> keys = new TreeSet<>();
		object.fieldNames().forEachRemaining(keys::add);
		return keys;
	}

	// Lists the items of a page as "name=count" texts
	private List<String> list(String path, String nameProperty, String countProperty) throws Exception {
		Answer answer = this.reader.get(path);
		Assertions.assertEquals(200, answer.status(), path + " -> " + answer);
		List<String> items = new ArrayList<>();
		answer.json().get("results").forEach(item -> items.add(item.get(nameProperty).asText()
			+ (countProperty == null ? "" : "=" + item.get(countProperty).asLong())));
		return items;
	}

	@Test
	@DisplayName("Device families: distinct values with their number of devices")
	void families() throws Exception {
		Answer answer = this.reader.get("/device-families");
		Assertions.assertEquals(200, answer.status(), answer.toString());
		JsonNode page = answer.json();
		Assertions.assertEquals(2, page.get("results").size());
		Assertions.assertEquals(Set.of("driver", "family", "deviceCount"), keys(page.get("results").get(0)));
		Assertions.assertEquals(List.of("Cisco ISR=2", "Unknown IOS device=1"), this.list("/device-families", "family", "deviceCount"));
		Assertions.assertEquals(List.of("Unknown IOS device=1"), this.list("/device-families?deviceCount__lt=2", "family", "deviceCount"));
		Assertions.assertEquals(List.of("Unknown IOS device=1"), this.list("/device-families?q=unknown", "family", "deviceCount"));
		Assertions.assertEquals(List.of("Unknown IOS device=1", "Cisco ISR=2"),
			this.list("/device-families?sort=deviceCount", "family", "deviceCount"));
		Assertions.assertEquals(List.of("Cisco ISR=2", "Unknown IOS device=1"),
			this.list("/device-families?driver=CiscoIOS12", "family", "deviceCount"));
		Assertions.assertEquals(List.of(), this.list("/device-families?driver=Nothing", "family", "deviceCount"));
		Assertions.assertEquals(400, this.reader.get("/device-families?deviceCount=many").status());
		Assertions.assertEquals(400, this.reader.get("/device-families?family__regex=a").status());
		Assertions.assertEquals(400, this.reader.get("/device-families?sort=bogus").status());
		JsonNode paged = this.reader.get("/device-families?limit=1&offset=1&count=true").json();
		Assertions.assertEquals(2, paged.get("count").asInt());
		Assertions.assertEquals(1, paged.get("results").size());
		Assertions.assertTrue(paged.get("next").isNull());
		Assertions.assertFalse(paged.get("previous").isNull());
	}

	@Test
	@DisplayName("Device tags: distinct values with their number of devices")
	void tags() throws Exception {
		Assertions.assertEquals(List.of("core=2", "edge=1"), this.list("/device-tags", "name", "deviceCount"));
		Assertions.assertEquals(List.of("edge=1", "core=2"), this.list("/device-tags?sort=deviceCount", "name", "deviceCount"));
		Assertions.assertEquals(List.of("edge=1"), this.list("/device-tags?name__nic=or", "name", "deviceCount"));
		Assertions.assertEquals(List.of("core=2"), this.list("/device-tags?q=COR", "name", "deviceCount"));
	}

	@Test
	@DisplayName("Part numbers: distinct values with their number of modules")
	void partNumbers() throws Exception {
		Assertions.assertEquals(List.of("TESTCHASSIS=3", "TESTSLOT=3"), this.list("/part-numbers", "partNumber", "moduleCount"));
		Assertions.assertEquals(List.of("TESTSLOT=3"), this.list("/part-numbers?partNumber__iew=slot", "partNumber", "moduleCount"));
		Assertions.assertEquals(List.of("TESTSLOT=3", "TESTCHASSIS=3"),
			this.list("/part-numbers?sort=-partNumber", "partNumber", "moduleCount"));
	}

	@Test
	@DisplayName("Software versions: distinct non empty values with their number of devices")
	void softwareVersions() throws Exception {
		Assertions.assertEquals(List.of("16.1.6=2", "17.3.1=1"), this.list("/software-versions", "version", "deviceCount"));
		Assertions.assertEquals(List.of("17.3.1=1"), this.list("/software-versions?version__isw=17", "version", "deviceCount"));
		Assertions.assertEquals(List.of("16.1.6=2"), this.list("/software-versions?deviceCount__gte=2", "version", "deviceCount"));
	}

	@Test
	@DisplayName("Device types: the loaded drivers")
	void deviceTypes() throws Exception {
		Answer answer = this.reader.get("/device-types?name=CiscoIOS12");
		Assertions.assertEquals(200, answer.status(), answer.toString());
		JsonNode page = answer.json();
		Assertions.assertEquals(1, page.get("results").size());
		JsonNode brief = page.get("results").get(0);
		Assertions.assertEquals(Set.of("name", "description", "version"), keys(brief));
		Assertions.assertEquals("CiscoIOS12", brief.get("name").asText());

		JsonNode type = this.reader.get("/device-types?name=CiscoIOS12&view=full").json().get("results").get(0);
		Assertions.assertEquals(Set.of("name", "description", "version", "author", "priority", "protocols", "cliMainModes",
			"sourceHash", "location", "attributes", "options", "accesses"), keys(type));
		Assertions.assertTrue(type.get("protocols").toString().contains("SSH"), type.get("protocols").toString());
		JsonNode running = null;
		for (JsonNode attribute : type.get("attributes")) {
			if (attribute.get("name").asText().equals("runningConfig")) {
				running = attribute;
			}
		}
		Assertions.assertNotNull(running, type.get("attributes").toString());
		Assertions.assertEquals("CONFIG", running.get("level").asText());
		Assertions.assertEquals("LONGTEXT", running.get("type").asText());
		Assertions.assertTrue(running.get("comparable").asBoolean());
		Assertions.assertTrue(type.get("accesses").size() > 0);

		Assertions.assertTrue(this.list("/device-types", "name", null).contains("CiscoIOS12"));
		Assertions.assertEquals(List.of("CiscoIOS12"), this.list("/device-types?name__ie=ciscoios12", "name", null));
		Assertions.assertTrue(this.list("/device-types?q=cisco", "name", null).contains("CiscoIOS12"));
		Assertions.assertTrue(this.list("/device-types?sort=-name", "name", null).get(0).compareTo("CiscoIOS12") >= 0);
		Assertions.assertEquals(400, this.reader.get("/device-types?bogus=1").status());
		Assertions.assertEquals(400, this.reader.get("/device-types?name__regex=a").status());
	}

	@Test
	@DisplayName("Reload of the device drivers (admin)")
	void reloadDrivers() throws Exception {
		int before = this.reader.get("/device-types?limit=1&count=true").json().get("count").asInt();
		Answer reload = this.admin.post("/device-types/reload", null);
		Assertions.assertEquals(204, reload.status(), reload.toString());
		Assertions.assertEquals(before, this.reader.get("/device-types?limit=1&count=true").json().get("count").asInt(),
			"The same drivers are loaded again");
		Assertions.assertEquals(403, this.reader.post("/device-types/reload", null).status());
	}
}
