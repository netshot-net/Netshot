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
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Endpoints;

/**
 * The compliance results of a device in the v1 API, over HTTP.
 */
@ApiTest
@V1Endpoints({"GET /devices/{id}/compliance-results"})
@DisplayName("v1 device compliance results")
@ResourceLock("DB")
public class V1DeviceComplianceResultResourceTest {

	private V1Client reader;
	private Device router1;
	private Device router2;
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
		Domain domain = TestDevices.addDomain("Paris", "Paris DC");
		this.router1 = TestDevices.addFakeCiscoIos(domain, 1);
		this.router2 = TestDevices.addFakeCiscoIos(domain, 2);
		long core = TestDevices.addStaticGroup("Core " + System.nanoTime(), this.router1).id();
		long other = TestDevices.addStaticGroup("Other " + System.nanoTime(), this.router2).id();
		Policy security = TestCompliance.policy("Security", core);
		Policy hygiene = TestCompliance.policy("Hygiene", core);
		Policy elsewhere = TestCompliance.policy("Elsewhere", other);
		this.telnet = TestCompliance.rule(TextRule.builder("No telnet", security.id()).enabled(true).build());
		this.banner = TestCompliance.rule(JavaScriptRule.of("Banner", hygiene.id()));
		TestCompliance.rule(JavaScriptRule.of("Not for router1", elsewhere.id()));
		TestCompliance.checkResults(new CheckResult(this.telnet.id(), this.router1.id(), Instant.parse("2026-10-01T10:00:00Z"),
			"telnet found", ResultOption.NONCONFORMING));
		TestCompliance.exemptions(this.banner.id(), Map.of(this.router1.id(), Instant.parse("2040-01-01T00:00:00Z")));
	}

	private static List<String> ruleNames(JsonNode page) {
		List<String> names = new ArrayList<>();
		page.get("results").forEach(item -> names.add(item.get("rule").get("name").asText()));
		return names;
	}

	private static Set<String> keys(JsonNode object) {
		Set<String> keys = new TreeSet<>();
		object.fieldNames().forEachRemaining(keys::add);
		return keys;
	}

	@Test
	@DisplayName("List: rules of the policies applying to the device, with result and exemption")
	void list() throws Exception {
		String path = "/devices/" + this.router1.id() + "/compliance-results";
		JsonNode page = this.reader.get(path).json();
		Assertions.assertEquals(2, page.get("count").asInt());
		Assertions.assertEquals(Set.of("No telnet", "Banner"), Set.copyOf(ruleNames(page)));

		JsonNode telnetResult = page.get("results").get(0).get("rule").get("name").asText().equals("No telnet")
			? page.get("results").get(0) : page.get("results").get(1);
		Assertions.assertEquals(Set.of("device", "rule", "policy", "result", "checkDate"), keys(telnetResult));
		Assertions.assertEquals(this.router1.id(), telnetResult.get("device").get("id").asLong());
		Assertions.assertEquals("NONCONFORMING", telnetResult.get("result").asText());
		Assertions.assertEquals(this.telnet.id(), telnetResult.get("rule").get("id").asLong());
		Assertions.assertEquals("TEXT", telnetResult.get("rule").get("type").asText());
		Assertions.assertEquals("Security", telnetResult.get("policy").get("name").asText());

		JsonNode full = this.reader.get(path + "?view=full&rule=" + this.banner.id()).json().get("results").get(0);
		Assertions.assertTrue(full.get("result").isNull());
		Assertions.assertEquals("2040-01-01T00:00:00.000Z", full.get("exemptionExpirationDate").asText());

		Assertions.assertEquals(List.of("No telnet"), ruleNames(this.reader.get(path + "?result=NONCONFORMING").json()));
		Assertions.assertEquals(List.of("Banner"), ruleNames(this.reader.get(path + "?policyName=Hygiene").json()));
		Assertions.assertEquals(List.of("No telnet"), ruleNames(this.reader.get(path + "?q=telnet").json()));
		Assertions.assertEquals(List.of("Banner", "No telnet"), ruleNames(this.reader.get(path + "?sort=ruleName").json()));
		Assertions.assertEquals(1, this.reader.get(path + "?limit=1").json().get("results").size());
		Assertions.assertEquals(2, this.reader.get(path + "?limit=1").json().get("count").asInt());
		Assertions.assertEquals(400, this.reader.get(path + "?result=NOPE").status());
		JsonNode expanded = this.reader.get(path + "?expand=rule&rule=" + this.telnet.id()).json().get("results").get(0);
		Assertions.assertTrue(expanded.get("rule").has("regExp"));

		// The other device only has its own policy's rules; a device out of any policy has none
		Assertions.assertEquals(List.of("Not for router1"),
			ruleNames(this.reader.get("/devices/" + this.router2.id() + "/compliance-results").json()));
		Assertions.assertEquals(404, this.reader.get("/devices/" + TestServer.UNKNOWN_ID + "/compliance-results").status());
	}
}
