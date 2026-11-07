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
package net.netshot.netshot.api.rest.v1.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestProperty;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.api.V1Endpoints;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * The configurations of the v1 API, over HTTP: lists, filters, views, item downloads and comparisons.
 */
@ApiTest
@V1Endpoints({
	"GET /configs",
	"GET /configs/stats",
	"GET /configs/{id}",
	"GET /configs/{id}/items/{item}",
	"GET /configs/{id}/diff",
	"GET /devices/{id}/configs"
})
@TestProperty(name = "netshot.snapshots.binary.path", value = V1ConfigResourceTest.BINARY_PATH)
@DisplayName("v1 configurations")
@ResourceLock("DB")
public class V1ConfigResourceTest {

	/** Where the binary files of the configurations are stored by the tests. */
	static final String BINARY_PATH = "/tmp/netshot-v1-config-test";

	private static final Instant JANUARY = Instant.parse("2026-01-01T10:00:00Z");
	private static final Instant FEBRUARY = Instant.parse("2026-02-01T10:00:00Z");
	private static final Instant MARCH = Instant.parse("2026-03-01T10:00:00Z");

	private V1Client admin;
	private V1Client reader;
	private Domain paris;
	private Device router1;
	private Device router2;
	private StaticDeviceGroup core;
	private long first;
	private long second;
	private long other;
	private ConfigBinaryFileAttribute backup;

	@BeforeAll
	static void loadDrivers() throws Exception {
		Files.createDirectories(Path.of(BINARY_PATH));
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.admin = new V1Client(server, UiUser.LEVEL_ADMIN);
		this.reader = new V1Client(server, UiUser.LEVEL_READONLY);
		TestDevices.deleteAll();
		this.paris = TestDevices.addDomain("Paris-" + System.nanoTime(), "Paris DC");
		this.router1 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(this.paris, null, 1));
		this.router2 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(this.paris, null, 2));
		this.core = TestDevices.addStaticGroup("Core-" + System.nanoTime(), this.router1);
		Config base = FakeDeviceFactory.getFakeCiscoIosConfig(this.router1);
		this.first = TestDevices.addConfig(this.router1, base.toBuilder().changeDate(JANUARY).author("alice").build());
		List<ConfigAttribute> changed = new ArrayList<>();
		for (ConfigAttribute attribute : base.attributes()) {
			if (attribute instanceof ConfigLongTextAttribute longText) {
				changed.add(new ConfigLongTextAttribute(longText.name(),
					longText.text().replace("service timestamps debug datetime msec", "service timestamps debug uptime")
						+ "ntp server 10.0.0.1\n"));
			}
			else {
				changed.add(attribute);
			}
		}
		this.backup = ConfigBinaryFileAttribute.create("backup", "backup.tar.gz").withFile(5, "sha256:abcdef");
		changed.add(this.backup);
		this.second = TestDevices.addConfig(this.router1, base.toBuilder().changeDate(FEBRUARY).author("bob")
			.attributes(changed).build());
		Files.writeString(this.backup.getFilePath(this.router1.id(), this.second), "hello");
		this.other = TestDevices.addConfig(this.router2, FakeDeviceFactory.getFakeCiscoIosConfig(this.router2).toBuilder()
			.changeDate(MARCH).author("alice").build());
	}

	private static Set<String> keys(JsonNode object) {
		Set<String> keys = new TreeSet<>();
		object.fieldNames().forEachRemaining(keys::add);
		return keys;
	}

	private List<Long> ids(String path) throws Exception {
		Answer answer = this.reader.get(path);
		Assertions.assertEquals(200, answer.status(), path + " -> " + answer);
		List<Long> ids = new ArrayList<>();
		answer.json().get("results").forEach(item -> ids.add(item.get("id").asLong()));
		return ids;
	}

	@Test
	@DisplayName("List: latest first, brief view with device reference and no attributes, filters")
	void list() throws Exception {
		JsonNode page = this.reader.get("/configs").json();
		Assertions.assertEquals(3, page.get("results").size());
		Assertions.assertEquals(List.of(this.other, this.second, this.first), this.ids("/configs"));
		JsonNode item = page.get("results").get(1);
		Assertions.assertEquals(Set.of("id", "url", "display", "device", "changeDate", "author"), keys(item));
		Assertions.assertTrue(item.get("url").asText().endsWith("/api/v1/configs/" + this.second), item.toString());
		Assertions.assertEquals(FEBRUARY, Instant.parse(item.get("changeDate").asText()));
		Assertions.assertEquals("bob", item.get("author").asText());
		Assertions.assertEquals(Set.of("id", "url", "display", "name", "mgmtAddress", "status", "family", "driver", "driverDescription",
			"networkClass", "softwareLevel", "compliant", "endOfLife", "endOfSale"), keys(item.get("device")));
		Assertions.assertEquals(this.router1.id(), item.get("device").get("id").asLong());
		Assertions.assertEquals(this.router1.name(), item.get("device").get("name").asText());

		Assertions.assertEquals(List.of(this.second, this.first), this.ids("/configs?device=" + this.router1.id()));
		Assertions.assertEquals(List.of(this.other, this.first), this.ids("/configs?author=alice"));
		Assertions.assertEquals(List.of(this.second), this.ids("/configs?q=BOB"));
		Assertions.assertEquals(List.of(this.other, this.second), this.ids("/configs?changeDate__gte=2026-02-01"));
		Assertions.assertEquals(List.of(this.second, this.first), this.ids("/configs?changeDate__lt=2026-03-01T00:00:00Z"));
		Assertions.assertEquals(List.of(this.first, this.second, this.other), this.ids("/configs?sort=changeDate"));
		Assertions.assertEquals(List.of(this.second, this.first), this.ids("/configs?group=" + this.core.id()));
		Assertions.assertEquals(List.of(this.other, this.second, this.first), this.ids("/configs?domain=" + this.paris.id()));
		Assertions.assertEquals(List.of(), this.ids("/configs?domain=" + TestServer.UNKNOWN_ID));
		Assertions.assertEquals(List.of(this.other), this.ids("/configs?group__n=" + this.core.id()));
		Assertions.assertEquals(400, this.reader.get("/configs?changeDate=yesterday").status());
		Assertions.assertEquals(400, this.reader.get("/configs?sort=customHash").status());
		Assertions.assertEquals(400, this.reader.get("/configs?bogus=1").status());

		JsonNode paged = this.reader.get("/configs?limit=2&offset=1&count=false").json();
		Assertions.assertTrue(paged.get("count").isNull());
		Assertions.assertEquals(2, paged.get("results").size());
	}

	@Test
	@DisplayName("List: full view has the attributes, never the long texts")
	void listFull() throws Exception {
		JsonNode item = this.reader.get("/configs?view=full&device=%d&limit=1".formatted(this.router1.id())).json().get("results").get(0);
		Assertions.assertEquals(this.second, item.get("id").asLong());
		Assertions.assertTrue(item.has("attributes"));
		Assertions.assertTrue(item.has("customHash"));
		Assertions.assertFalse(item.toString().contains("hostname"), "no long text in a list");
		JsonNode fields = this.reader.get("/configs?fields=author,attributes&limit=1").json().get("results").get(0);
		Assertions.assertEquals(Set.of("id", "author", "attributes"), keys(fields));
		JsonNode expanded = this.reader.get("/configs?expand=device&fields=device&limit=1").json().get("results").get(0);
		Assertions.assertEquals("Unknown IOS device", expanded.get("device").get("family").asText(), expanded.toString());
	}

	@Test
	@DisplayName("Get: attributes with their types, sizes and download paths")
	void get() throws Exception {
		Answer answer = this.reader.get("/configs/" + this.second);
		Assertions.assertEquals(200, answer.status(), answer.toString());
		Assertions.assertNotNull(answer.header("ETag"));
		JsonNode config = answer.json();
		Assertions.assertEquals(this.second, config.get("id").asLong());
		Assertions.assertEquals("bob", config.get("author").asText());
		Assertions.assertEquals(this.router1.id(), config.get("device").get("id").asLong());
		JsonNode attributes = config.get("attributes");
		List<String> names = new ArrayList<>();
		attributes.forEach(a -> names.add(a.get("name").asText()));
		Assertions.assertEquals(List.of("backup", "iosImageFile", "iosVersion", "runningConfig"), names);
		JsonNode file = attributes.get(0);
		Assertions.assertEquals("BINARYFILE", file.get("type").asText());
		Assertions.assertEquals("backup.tar.gz", file.get("originalName").asText());
		Assertions.assertEquals(5, file.get("fileSize").asInt());
		Assertions.assertEquals("sha256:abcdef", file.get("checksum").asText());
		Assertions.assertTrue(file.get("url").asText().endsWith("/configs/%d/items/backup".formatted(this.second)), file.toString());
		Assertions.assertEquals("TEXT", attributes.get(2).get("type").asText());
		Assertions.assertEquals("16.1.6", attributes.get(2).get("text").asText());
		JsonNode longText = attributes.get(3);
		Assertions.assertEquals("LONGTEXT", longText.get("type").asText());
		Assertions.assertEquals(Set.of("type", "name", "url"), keys(longText));
		Assertions.assertEquals(404, this.reader.get("/configs/" + TestServer.UNKNOWN_ID).status());
		Assertions.assertEquals(400, this.reader.get("/configs/%d?bogus=1".formatted(this.second)).status());
	}

	@Test
	@DisplayName("Items: text and file downloads, media types, errors")
	void items() throws Exception {
		Answer text = this.reader.get("/configs/%d/items/runningConfig".formatted(this.second), "Accept", "text/plain");
		Assertions.assertEquals(200, text.status(), text.toString());
		Assertions.assertTrue(text.header("Content-Type").startsWith("text/plain"), text.header("Content-Type"));
		Assertions.assertTrue(text.header("Content-Disposition").startsWith("attachment; filename=\""), text.header("Content-Disposition"));
		Assertions.assertTrue(text.header("Content-Disposition").endsWith("_runningConfig.cfg\""), text.header("Content-Disposition"));
		Assertions.assertTrue(text.body().contains("hostname " + this.router1.name()), text.body());
		Assertions.assertTrue(text.body().contains("ntp server 10.0.0.1"));
		Assertions.assertEquals(String.valueOf(text.body().getBytes(StandardCharsets.UTF_8).length), text.header("Content-Length"));

		Answer file = this.reader.get("/configs/%d/items/backup".formatted(this.second), "Accept", "application/octet-stream");
		Assertions.assertEquals(200, file.status(), file.toString());
		Assertions.assertEquals("application/octet-stream", file.header("Content-Type"));
		Assertions.assertTrue(file.header("Content-Disposition").endsWith("_backup_backup.tar.gz\""), file.header("Content-Disposition"));
		Assertions.assertEquals("hello", file.body());
		Assertions.assertEquals("5", file.header("Content-Length"));

		// The default media type of the client works too
		Assertions.assertEquals(200, this.reader.get("/configs/%d/items/runningConfig".formatted(this.first), "Accept", "*/*").status());
		// A short value isn't an item to download, nor is an unknown name
		Assertions.assertEquals(404, this.reader.get("/configs/%d/items/iosVersion".formatted(this.second), "Accept", "*/*").status());
		Assertions.assertEquals(404, this.reader.get("/configs/%d/items/nothing".formatted(this.second), "Accept", "*/*").status());
		Answer unknown = this.reader.get("/configs/%d/items/runningConfig".formatted(TestServer.UNKNOWN_ID), "Accept", "*/*");
		Assertions.assertEquals(404, unknown.status(), unknown.toString());
		Assertions.assertEquals(404, this.admin.get("/configs/%d/items/runningConfig".formatted(TestServer.UNKNOWN_ID), "Accept", "text/plain").status());
	}

	@Test
	@DisplayName("Diff: against another configuration, against the previous one, references")
	void diff() throws Exception {
		Answer answer = this.reader.get("/configs/%d/diff?with=%d".formatted(this.second, this.first));
		Assertions.assertEquals(200, answer.status(), answer.toString());
		JsonNode diff = answer.json();
		Assertions.assertEquals(Set.of("original", "revised", "originalDate", "revisedDate", "deltas"), keys(diff));
		Assertions.assertEquals(this.first, diff.get("original").get("id").asLong());
		Assertions.assertEquals(this.second, diff.get("revised").get("id").asLong());
		Assertions.assertEquals(Set.of("id", "url", "display", "device", "changeDate", "author"), keys(diff.get("revised")));
		Assertions.assertEquals(JANUARY, Instant.parse(diff.get("originalDate").asText()));
		Assertions.assertEquals(FEBRUARY, Instant.parse(diff.get("revisedDate").asText()));
		JsonNode deltas = diff.get("deltas");
		Assertions.assertEquals(1, deltas.size(), deltas.toString());
		JsonNode changes = deltas.elements().next();
		Assertions.assertEquals(2, changes.size(), changes.toString());
		Set<String> types = new TreeSet<>();
		changes.forEach(change -> types.add(change.get("type").asText()));
		Assertions.assertEquals(Set.of("CHANGE", "INSERT"), types);
		JsonNode change = changes.get(0);
		Assertions.assertEquals(Set.of("type", "originalPosition", "revisedPosition", "originalLines", "revisedLines", "preContext",
			"postContext", "hierarchy"), keys(change));
		Assertions.assertEquals(List.of("service timestamps debug datetime msec"),
			List.of(change.get("originalLines").get(0).asText()));
		Assertions.assertEquals("service timestamps debug uptime", change.get("revisedLines").get(0).asText());

		// By default, the original is the previous configuration of the device
		JsonNode previous = this.reader.get("/configs/%d/diff".formatted(this.second)).json();
		Assertions.assertEquals(this.first, previous.get("original").get("id").asLong());
		Assertions.assertEquals(deltas, previous.get("deltas"));
		// The first configuration of a device is compared with an empty one
		JsonNode initial = this.reader.get("/configs/%d/diff".formatted(this.first)).json();
		Assertions.assertTrue(initial.get("original").isNull(), initial.toString());
		Assertions.assertTrue(initial.get("deltas").size() > 0);
		// Same configuration: no difference
		Assertions.assertEquals(0, this.reader.get("/configs/%d/diff?with=%d".formatted(this.second, this.second)).json().get("deltas").size());
		// Expanded configurations
		JsonNode expanded = this.reader.get("/configs/%d/diff?with=%d&expand=original&fields=original".formatted(this.second, this.first)).json();
		Assertions.assertEquals(Set.of("original"), keys(expanded));
		Assertions.assertTrue(expanded.get("original").has("attributes"), expanded.toString());
		Assertions.assertFalse(expanded.toString().contains("hostname"));

		Assertions.assertEquals(404, this.reader.get("/configs/%d/diff".formatted(TestServer.UNKNOWN_ID)).status());
		Assertions.assertEquals(404, this.reader.get("/configs/%d/diff?with=%d".formatted(this.second, TestServer.UNKNOWN_ID)).status());
		Assertions.assertEquals(400, this.reader.get("/configs/%d/diff?with=abc".formatted(this.second)).status());
		Assertions.assertEquals(400, this.reader.get("/configs/%d/diff?with=0".formatted(this.second)).status());
		Assertions.assertEquals(400, this.reader.get("/configs/%d/diff?other=1".formatted(this.second)).status());
	}

	@Test
	@DisplayName("Device configurations: the list filtered on the device")
	void deviceConfigs() throws Exception {
		Assertions.assertEquals(List.of(this.second, this.first), this.ids("/devices/%d/configs".formatted(this.router1.id())));
		Assertions.assertEquals(List.of(this.other), this.ids("/devices/%d/configs".formatted(this.router2.id())));
		Assertions.assertEquals(List.of(this.first), this.ids("/devices/%d/configs?author=alice".formatted(this.router1.id())));
		Assertions.assertEquals(List.of(this.first, this.second), this.ids("/devices/%d/configs?sort=changeDate".formatted(this.router1.id())));
		JsonNode page = this.reader.get("/devices/%d/configs?limit=1&count=true".formatted(this.router1.id())).json();
		Assertions.assertEquals(2, page.get("count").asInt());
		Assertions.assertFalse(page.get("next").isNull());
		Assertions.assertEquals(404, this.reader.get("/devices/%d/configs".formatted(TestServer.UNKNOWN_ID)).status());
		Assertions.assertEquals(400, this.reader.get("/devices/%d/configs?bogus=1".formatted(this.router1.id())).status());
	}

	@Test
	@DisplayName("Stats: configuration changes by month, by device and author")
	void stats() throws Exception {
		JsonNode months = this.reader.get("/configs/stats?interval=month&from=2026-01-01&to=2026-04-01").json();
		Assertions.assertEquals("changeDate", months.get("on").asText());
		List<Long> counts = new ArrayList<>();
		months.get("results").forEach(r -> counts.add(r.get("count").asLong()));
		Assertions.assertEquals(List.of(1L, 1L, 1L), counts);
		JsonNode byDevice = this.reader.get("/configs/stats?groupBy=device").json();
		Assertions.assertEquals(3, byDevice.get("count").asLong());
		Assertions.assertEquals("router00001", byDevice.get("results").get(0).get("device").get("display").asText());
		Assertions.assertEquals(2, byDevice.get("results").get(0).get("count").asLong());
		JsonNode byAuthor = this.reader.get("/configs/stats?groupBy=author&device=" + this.router1.id()).json();
		Assertions.assertEquals(2, byAuthor.get("results").size());
		Assertions.assertEquals(400, this.reader.get("/configs/stats?groupBy=customHash").status());
	}

	@Test
	@DisplayName("Not authenticated")
	void unauthenticated(TestServer server) throws Exception {
		V1Client anonymous = new V1Client(server, null);
		Assertions.assertEquals(401, anonymous.get("/configs").status());
		Assertions.assertEquals(401, anonymous.get("/configs/" + this.first).status());
		Assertions.assertEquals(401, anonymous.get("/devices/%d/configs".formatted(this.router1.id())).status());
	}
}
