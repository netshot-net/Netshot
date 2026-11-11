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
import java.util.Map;
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
import net.netshot.netshot.compliance.JavaScriptRule;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.inventory.compliance.CheckResult.ResultOption;
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
 * The compliance results across devices, and their statistics, in the v1 API, over HTTP.
 */
@ApiTest
@V1Endpoints({"GET /compliance-results", "GET /compliance-results/stats", "GET /devices/stats", "GET /devices/{id}/compliance-results"})
@DisplayName("v1 compliance results")
@ResourceLock("DB")
public class V1ComplianceResultResourceTest {

	private V1Client reader;
	private Device router1;
	private Device router2;
	private Device router3;
	private Domain paris;
	private long core;
	private long edge;
	private TextRule telnet;
	private JavaScriptRule banner;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.reader = new V1Client(server, UiUser.LEVEL_READONLY);
		TestWiring.get().transactions().run(dsl -> {
			dsl.deleteFrom(Tables.RULE).execute();
			dsl.deleteFrom(Tables.POLICY).execute();
		});
		TestDevices.deleteAll();
		this.paris = TestDevices.addDomain("Paris" + System.nanoTime(), "Paris DC");
		Domain lyon = TestDevices.addDomain("Lyon" + System.nanoTime(), "Lyon DC");
		this.router1 = TestDevices.addFakeCiscoIos(this.paris, 1);
		this.router2 = TestDevices.addFakeCiscoIos(this.paris, 2);
		this.router3 = TestDevices.addFakeCiscoIos(lyon, 3);
		// router2 is in two groups: it must not be duplicated when filtering on both
		this.core = TestDevices.addStaticGroup("Core " + System.nanoTime(), this.router1, this.router2).id();
		this.edge = TestDevices.addStaticGroup("Edge " + System.nanoTime(), this.router2).id();
		Policy security = TestCompliance.policy("Security", this.core);
		Policy hygiene = TestCompliance.policy("Hygiene", this.core);
		this.telnet = TestCompliance.rule(TextRule.builder("No telnet", security.id()).enabled(true).build());
		this.banner = TestCompliance.rule(JavaScriptRule.of("Banner", hygiene.id()));
		TestCompliance.checkResults(
			new CheckResult(this.telnet.id(), this.router1.id(), Instant.parse("2026-10-01T10:00:00Z"), "telnet found",
				ResultOption.NONCONFORMING),
			new CheckResult(this.banner.id(), this.router1.id(), Instant.parse("2026-10-02T10:00:00Z"), "ok", ResultOption.CONFORMING),
			new CheckResult(this.telnet.id(), this.router2.id(), Instant.parse("2026-10-01T11:00:00Z"), "ok", ResultOption.CONFORMING),
			new CheckResult(this.banner.id(), this.router2.id(), Instant.parse("2026-10-03T10:00:00Z"), "no banner",
				ResultOption.NONCONFORMING),
			new CheckResult(this.telnet.id(), this.router3.id(), Instant.parse("2026-10-03T12:00:00Z"), "telnet found",
				ResultOption.NONCONFORMING));
		TestCompliance.exemptions(this.banner.id(), Map.of(this.router1.id(), Instant.parse("2040-01-01T00:00:00Z")));
	}

	private JsonNode get(String path) throws Exception {
		Answer answer = this.reader.get(path);
		Assertions.assertEquals(200, answer.status(), path + " -> " + answer);
		return answer.json();
	}

	private void assertBadRequest(String path) throws Exception {
		Answer answer = this.reader.get(path);
		Assertions.assertEquals(400, answer.status(), path + " -> " + answer);
	}

	private static java.util.stream.Stream<JsonNode> stream(JsonNode array) {
		return java.util.stream.StreamSupport.stream(array.spliterator(), false);
	}

	private static Set<String> keys(JsonNode object) {
		Set<String> keys = new TreeSet<>();
		object.fieldNames().forEachRemaining(keys::add);
		return keys;
	}

	private static List<String> rows(JsonNode stats, String... properties) {
		List<String> rows = new ArrayList<>();
		for (JsonNode row : stats.get("results")) {
			StringBuilder text = new StringBuilder();
			for (String property : properties) {
				JsonNode value = row.get(property);
				text.append(value.isObject() ? value.get("display").asText() : value.asText()).append(':');
			}
			rows.add(text.append(row.get("count").asLong()).toString());
		}
		return rows;
	}

	@Test
	@DisplayName("List: one row per device and rule, brief view with references, no ID of its own")
	void list() throws Exception {
		JsonNode page = this.get("/compliance-results");
		Assertions.assertEquals(5, page.get("results").size());
		Assertions.assertEquals(5, page.get("results").size());
		JsonNode first = page.get("results").get(0);
		Assertions.assertEquals(Set.of("device", "rule", "policy", "result", "checkDate"), keys(first));
		Assertions.assertEquals(this.router1.id(), first.get("device").get("id").asLong());
		Assertions.assertEquals(this.router1.name(), first.get("device").get("name").asText());
		Assertions.assertEquals("Security", first.get("policy").get("name").asText());
		Assertions.assertEquals("TEXT", first.get("rule").get("type").asText());

		// Default order: device name, then policy and rule
		Assertions.assertEquals(List.of(this.router1.id(), this.router1.id(), this.router2.id(), this.router2.id(), this.router3.id()),
			page.get("results").findValues("device").stream().map(d -> d.get("id").asLong()).toList());

		JsonNode full = this.get("/compliance-results?view=full&device=" + this.router1.id() + "&rule=" + this.banner.id())
			.get("results").get(0);
		Assertions.assertEquals("CONFORMING", full.get("result").asText());
		Assertions.assertEquals("ok", full.get("comment").asText());
		Assertions.assertEquals("2040-01-01T00:00:00.000Z", full.get("exemptionExpirationDate").asText());

		JsonNode expanded = this.get("/compliance-results?expand=rule&rule=" + this.telnet.id()).get("results").get(0);
		Assertions.assertTrue(expanded.get("rule").has("regExp"));
	}

	@Test
	@DisplayName("List: filters on the result, rule, policy, device, domain and group, search, sort and paging")
	void filters() throws Exception {
		Assertions.assertEquals(3, this.get("/compliance-results?result=NONCONFORMING").get("results").size());
		Assertions.assertEquals(2, this.get("/compliance-results?result=CONFORMING").get("results").size());
		Assertions.assertEquals(3, this.get("/compliance-results?rule=" + this.telnet.id()).get("results").size());
		Assertions.assertEquals(2, this.get("/compliance-results?ruleName=Banner").get("results").size());
		Assertions.assertEquals(2, this.get("/compliance-results?policyName=Hygiene").get("results").size());
		Assertions.assertEquals(2, this.get("/compliance-results?device=" + this.router2.id()).get("results").size());
		Assertions.assertEquals(2, this.get("/compliance-results?deviceName=" + this.router2.name()).get("results").size());
		Assertions.assertEquals(1, this.get("/compliance-results?device=" + this.router1.id() + "&result=NONCONFORMING").get("results").size());

		// Of the device: its domain, its groups (a device in two groups isn't duplicated)
		Assertions.assertEquals(4, this.get("/compliance-results?domain=" + this.paris.id()).get("results").size());
		Assertions.assertEquals(4, this.get("/compliance-results?group=" + this.core).get("results").size());
		Assertions.assertEquals(2, this.get("/compliance-results?group=" + this.edge).get("results").size());
		JsonNode both = this.get("/compliance-results?group=" + this.core + "&group=" + this.edge);
		Assertions.assertEquals(4, both.get("results").size());
		Assertions.assertEquals(4, both.get("results").size());
		Assertions.assertEquals(1, this.get("/compliance-results?group__n=" + this.core).get("results").size());

		Assertions.assertEquals(3, this.get("/compliance-results?q=telnet").get("results").size());
		Assertions.assertEquals(2, this.get("/compliance-results?q=hygiene").get("results").size());
		Assertions.assertEquals(2, this.get("/compliance-results?checkDate__gte=2026-10-03T00:00:00Z").get("results").size());
		Assertions.assertEquals(1, this.get("/compliance-results?exemptionExpirationDate__gt=2030-01-01T00:00:00Z").get("results").size());

		JsonNode byDate = this.get("/compliance-results?sort=-checkDate");
		Assertions.assertEquals("2026-10-03T12:00:00.000Z", byDate.get("results").get(0).get("checkDate").asText());
		JsonNode page = this.get("/compliance-results?limit=2&offset=1&sort=checkDate&count=true");
		Assertions.assertEquals(5, page.get("count").asInt());
		Assertions.assertEquals(2, page.get("results").size());
		Assertions.assertEquals("2026-10-01T11:00:00.000Z", page.get("results").get(0).get("checkDate").asText());

		// The same rows through the list of the device
		JsonNode ofDevice = this.get("/devices/" + this.router1.id() + "/compliance-results");
		Assertions.assertEquals(2, ofDevice.get("results").size());
	}

	@Test
	@DisplayName("List: invalid parameters are refused")
	void listErrors() throws Exception {
		this.assertBadRequest("/compliance-results?result=NOPE");
		this.assertBadRequest("/compliance-results?unknown=1");
		this.assertBadRequest("/compliance-results?sort=comment");
		this.assertBadRequest("/compliance-results?group=abc");
		this.assertBadRequest("/compliance-results?limit=-1");
	}

	@Test
	@DisplayName("Stats: counts by result, policy, rule, device, group and domain, with the list filters")
	void stats() throws Exception {
		JsonNode byResult = this.get("/compliance-results/stats?groupBy=result");
		Assertions.assertEquals(5, byResult.get("count").asLong());
		Assertions.assertEquals(List.of("NONCONFORMING:3", "CONFORMING:2"), rows(byResult, "result"));

		JsonNode byPolicyResult = this.get("/compliance-results/stats?groupBy=policy,result&policyName=Security");
		Assertions.assertEquals(Set.of("Security:NONCONFORMING:2", "Security:CONFORMING:1"), Set.copyOf(rows(byPolicyResult, "policy", "result")));
		JsonNode policy = byPolicyResult.get("results").get(0).get("policy");
		Assertions.assertEquals(Set.of("id", "url", "display"), keys(policy));

		JsonNode byRule = this.get("/compliance-results/stats?groupBy=rule&result=NONCONFORMING");
		Assertions.assertEquals(Set.of("No telnet:2", "Banner:1"), Set.copyOf(rows(byRule, "rule")));
		Assertions.assertEquals(3, this.get("/compliance-results/stats?groupBy=device&result=NONCONFORMING").get("results").size());
		JsonNode byDevice = this.get("/compliance-results/stats?groupBy=device&device=" + this.router1.id());
		Assertions.assertEquals(List.of(this.router1.name() + ":2"), rows(byDevice, "device"));

		// A device in several groups is counted once per group (and once with no group)
		JsonNode byGroup = this.get("/compliance-results/stats?groupBy=group");
		Assertions.assertEquals(5, byGroup.get("count").asLong());
		Assertions.assertEquals(4, stream(byGroup.get("results")).filter(r -> r.get("group").isObject()
			&& r.get("group").get("id").asLong() == this.core).mapToLong(r -> r.get("count").asLong()).sum());
		Assertions.assertEquals(2, stream(byGroup.get("results")).filter(r -> r.get("group").isObject()
			&& r.get("group").get("id").asLong() == this.edge).mapToLong(r -> r.get("count").asLong()).sum());
		Assertions.assertEquals(1, stream(byGroup.get("results")).filter(r -> r.get("group").isNull())
			.mapToLong(r -> r.get("count").asLong()).sum());

		JsonNode byDomain = this.get("/compliance-results/stats?groupBy=domain");
		Assertions.assertEquals(4, stream(byDomain.get("results")).filter(r -> r.get("domain").isObject()
			&& r.get("domain").get("id").asLong() == this.paris.id()).mapToLong(r -> r.get("count").asLong()).sum());
		Assertions.assertTrue(byDomain.get("results").get(0).get("domain").has("display"));
	}

	@Test
	@DisplayName("Stats: time buckets on the check date")
	void statsByTime() throws Exception {
		JsonNode byDay = this.get("/compliance-results/stats?interval=day&from=2026-10-01T00:00:00Z&to=2026-10-05T00:00:00Z");
		Assertions.assertEquals("checkDate", byDay.get("on").asText());
		Assertions.assertEquals(4, byDay.get("results").size());
		Assertions.assertEquals(List.of(2L, 1L, 2L, 0L), byDay.get("results").findValues("count").stream().map(JsonNode::asLong).toList());

		JsonNode byDayResult = this.get("/compliance-results/stats?interval=day&from=2026-10-03T00:00:00Z&to=2026-10-04T00:00:00Z&groupBy=result");
		Assertions.assertEquals(Set.of("NONCONFORMING:2"), Set.copyOf(rows(byDayResult, "result")));
	}

	@Test
	@DisplayName("Replacements of the former reports: the compliance of the groups, the non compliant devices of a group")
	void formerReports() throws Exception {
		// group-config-compliance-stats: devices by group and compliance
		JsonNode byGroup = this.get("/devices/stats?groupBy=group,compliant&group=" + this.core);
		Assertions.assertEquals(2, byGroup.get("count").asLong());
		Assertions.assertEquals(2, stream(byGroup.get("results")).filter(r -> r.get("group").get("id").asLong() == this.core)
			.mapToLong(r -> r.get("count").asLong()).sum());
		// group-config-non-compliant-devices: the non conforming results of the devices of a group
		JsonNode nonCompliant = this.get("/compliance-results?group=" + this.core + "&result=NONCONFORMING");
		Assertions.assertEquals(List.of(this.router1.id(), this.router2.id()),
			nonCompliant.get("results").findValues("device").stream().map(d -> d.get("id").asLong()).toList());
		// config-compliance-device-statuses: the results of the devices of a domain, by policy and result
		JsonNode statuses = this.get("/compliance-results/stats?groupBy=policy,result&domain=" + this.paris.id());
		Assertions.assertEquals(4, statuses.get("count").asLong());
	}

	@Test
	@DisplayName("Stats: invalid parameters are refused")
	void statsErrors() throws Exception {
		this.assertBadRequest("/compliance-results/stats?groupBy=comment");
		this.assertBadRequest("/compliance-results/stats?groupBy=result,policy,rule");
		this.assertBadRequest("/compliance-results/stats?interval=day&on=exemptionExpirationDate&from=yesterday");
		this.assertBadRequest("/compliance-results/stats?interval=day&on=result");
		this.assertBadRequest("/compliance-results/stats?result=NOPE");
		this.assertBadRequest("/compliance-results/stats?limit=3");
	}

	@Test
	@DisplayName("Paging: every result once whatever the sort, as results are ordered by their key (device, rule) last; no id field")
	void pagingByKey() throws Exception {
		// Many results with the same values, of the same rules on several devices
		List<CheckResult> results = new ArrayList<>();
		for (int shift = 10; shift < 30; shift++) {
			Device device = TestDevices.addFakeCiscoIos(this.paris, shift);
			for (long rule : List.of(this.telnet.id(), this.banner.id())) {
				results.add(new CheckResult(rule, device.id(), Instant.parse("2026-10-05T10:00:00Z"), "same", ResultOption.CONFORMING));
			}
		}
		TestCompliance.checkResults(results.toArray(CheckResult[]::new));
		int total = this.get("/compliance-results?count=true").get("count").asInt();
		for (String sort : List.of("result", "-checkDate", "rule", "-policyName")) {
			Set<String> seen = new java.util.HashSet<>();
			for (int offset = 0; offset < total; offset += 3) {
				for (JsonNode result : this.get("/compliance-results?sort=%s&offset=%d&limit=3".formatted(sort, offset)).get("results")) {
					String key = result.get("device").get("id").asText() + "/" + result.get("rule").get("id").asText();
					Assertions.assertTrue(seen.add(key), "sort=" + sort + ": " + key + " listed twice");
				}
			}
			Assertions.assertEquals(total, seen.size(), "sort=" + sort + ": every result is listed");
		}
		this.assertBadRequest("/compliance-results?id=" + this.telnet.id());
		this.assertBadRequest("/compliance-results?sort=id");
		this.assertBadRequest("/devices/" + this.router1.id() + "/compliance-results?id=" + this.telnet.id());
		Assertions.assertEquals(400, this.reader.get("/compliance/rules/" + this.banner.id() + "/exemptions?id=" + this.router1.id()).status());
	}
}
