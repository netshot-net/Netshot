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
import net.netshot.netshot.testing.api.V1Endpoints;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * The device statistics of the v1 API ({@code GET /devices/stats}), over HTTP: counts by field values and by time.
 */
@ApiTest
@V1Endpoints({
	"GET /devices/stats"
})
@DisplayName("v1 device statistics")
@ResourceLock("DB")
public class V1DeviceStatsTest {

	private V1Client reader;
	private Domain paris;
	private StaticDeviceGroup core;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.reader = new V1Client(server, UiUser.LEVEL_READONLY);
		TestDevices.deleteAll();
		this.paris = TestDevices.addDomain("Paris", "Paris DC");
		Domain lyon = TestDevices.addDomain("Lyon", "Lyon DC");
		Device router1 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(this.paris, null, 1).toBuilder()
			.tags(Set.of("core", "edge")).build());
		Device router2 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(this.paris, null, 2).toBuilder()
			.tags(Set.of("core")).build());
		Device router3 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(lyon, null, 3).toBuilder()
			.status(Device.Status.DISABLED).tags(Set.of()).build());
		this.core = TestDevices.addStaticGroup("Core", router1, router2);
		TestDevices.setChangeDate(router1.id(), Instant.parse("2026-01-10T10:00:00Z"));
		TestDevices.setChangeDate(router2.id(), Instant.parse("2026-01-10T23:30:00Z"));
		TestDevices.setChangeDate(router3.id(), Instant.parse("2026-01-12T05:00:00Z"));
	}

	private JsonNode stats(String query) throws Exception {
		Answer answer = this.reader.get("/devices/stats" + query);
		Assertions.assertEquals(200, answer.status(), query + " -> " + answer);
		return answer.json();
	}

	private static List<String> rows(JsonNode stats, String... properties) {
		List<String> rows = new ArrayList<>();
		for (JsonNode row : stats.get("results")) {
			StringBuilder text = new StringBuilder();
			for (String property : properties) {
				JsonNode value = row.get(property);
				text.append(value == null || value.isNull() ? "null" : value.isObject() ? value.get("display").asText() : value.asText())
					.append(':');
			}
			rows.add(text.append(row.get("count").asLong()).toString());
		}
		return rows;
	}

	@Test
	@DisplayName("Counts by field values, with the filters of the list")
	void byValues() throws Exception {
		JsonNode all = this.stats("");
		Assertions.assertEquals(3, all.get("count").asLong());
		Assertions.assertEquals(List.of("3"), rows(all));

		Assertions.assertEquals(List.of("INPRODUCTION:2", "DISABLED:1"), rows(this.stats("?groupBy=status"), "status"));
		JsonNode byDomain = this.stats("?groupBy=mgmtDomain");
		Assertions.assertEquals(List.of("Paris:2", "Lyon:1"), rows(byDomain, "mgmtDomain"));
		JsonNode domain = byDomain.get("results").get(0).get("mgmtDomain");
		Assertions.assertEquals("/api/v1/domains/" + this.paris.id(), domain.get("url").asText());

		Assertions.assertEquals(Set.of("core:2", "edge:1", "null:1"), Set.copyOf(rows(this.stats("?groupBy=tag"), "tag")));
		Assertions.assertEquals(List.of("Core:2", "null:1"), rows(this.stats("?groupBy=group"), "group"));
		Assertions.assertEquals(List.of("true:3"), rows(this.stats("?groupBy=compliant"), "compliant"));
		Assertions.assertEquals(Set.of("INPRODUCTION:Paris:2", "DISABLED:Lyon:1"),
			Set.copyOf(rows(this.stats("?groupBy=status,mgmtDomain"), "status", "mgmtDomain")));

		// The filters and the search query of the list apply
		JsonNode filtered = this.stats("?status=INPRODUCTION&groupBy=tag");
		Assertions.assertEquals(2, filtered.get("count").asLong());
		Assertions.assertEquals(Set.of("core:2", "edge:1"), Set.copyOf(rows(filtered, "tag")));
		Assertions.assertEquals(List.of("Paris:2"), rows(this.stats("?group=" + this.core.id() + "&groupBy=mgmtDomain"), "mgmtDomain"));
		Assertions.assertEquals(List.of("Lyon:1"),
			rows(this.stats("?query=%5BStatus%5D%20is%20DISABLED&groupBy=mgmtDomain"), "mgmtDomain"));
	}

	@Test
	@DisplayName("Counts by time bucket, aligned in the time zone, empty buckets included")
	void byTime() throws Exception {
		JsonNode utc = this.stats("?interval=day&on=changeDate&from=2026-01-10&to=2026-01-13");
		Assertions.assertEquals("day", utc.get("interval").asText());
		Assertions.assertEquals("UTC", utc.get("timeZone").asText());
		Assertions.assertEquals(List.of("2026-01-10T00:00:00.000Z:2", "2026-01-11T00:00:00.000Z:0", "2026-01-12T00:00:00.000Z:1"),
			rows(utc, "start"));

		// In Paris (UTC+1 in January), 23:30 UTC is the next day
		JsonNode inParis = this.stats("?interval=day&on=changeDate&from=2026-01-10&to=2026-01-13&timeZone=Europe/Paris");
		Assertions.assertEquals(List.of("2026-01-09T23:00:00.000Z:1", "2026-01-10T23:00:00.000Z:1", "2026-01-11T23:00:00.000Z:1"),
			rows(inParis, "start"));

		JsonNode byStatus = this.stats("?interval=day&on=changeDate&from=2026-01-10&to=2026-01-12&groupBy=status");
		Assertions.assertEquals(Set.of("2026-01-10T00:00:00.000Z:INPRODUCTION:2", "2026-01-11T00:00:00.000Z:INPRODUCTION:0"),
			Set.copyOf(rows(byStatus, "start", "status")));

		JsonNode weeks = this.stats("?interval=week&on=changeDate&from=2026-01-05&to=2026-01-19");
		Assertions.assertEquals(List.of("2026-01-05T00:00:00.000Z:2", "2026-01-12T00:00:00.000Z:1"), rows(weeks, "start"),
			"Weeks start on Monday (2026-01-12 is a Monday)");
	}

	@Test
	@DisplayName("Invalid statistics parameters are refused")
	void invalid() throws Exception {
		for (String query : List.of("groupBy=name", "groupBy=color", "groupBy=status,family,driver", "groupBy=status,status",
			"interval=year", "interval=day&on=name", "interval=day&from=2026-01-10&to=2026-01-01", "interval=day&timeZone=Mars/Base",
			"from=2026-01-01", "on=changeDate", "interval=hour&from=2000-01-01", "limit=10", "sort=name", "fields=name",
			"color=red", "interval=day&from=yesterday")) {
			Answer answer = this.reader.get("/devices/stats?" + query);
			Assertions.assertEquals(400, answer.status(), query + " -> " + answer);
		}
	}
}
