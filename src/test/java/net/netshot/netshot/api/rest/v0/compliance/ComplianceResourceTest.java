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
package net.netshot.netshot.api.rest.v0.compliance;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.ws.rs.core.Response;
import net.netshot.netshot.compliance.JavaScriptRule;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.PythonRule;
import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestCompliance;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.v0.V0Client;

@ApiTest
@DisplayName("Compliance tests")
@ResourceLock("DB")
public class ComplianceResourceTest {

	/** API client of the current test, authenticated with the admin token. */
	private V0Client apiClient;

	@BeforeEach
	void createApiClient(V0Client client) {
		this.apiClient = client;
	}

	@AfterEach
	void cleanUpData() {
		TestWiring.get().transactions().run(dsl -> {
			dsl.deleteFrom(Tables.RULE).execute();
			dsl.deleteFrom(Tables.POLICY).execute();
			dsl.deleteFrom(Tables.DEVICE_GROUP).execute();
		});
	}

	@Test
	@DisplayName("List policies")
	void listPolicies() throws IOException, InterruptedException {
		{
			HttpResponse<JsonNode> response = apiClient.get("/policies");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for initial policy list");
			Assertions.assertEquals(0, response.body().size(),
				"Policy list is not empty");
		}

		StaticDeviceGroup group = TestDevices.addStaticGroup("Test group");
		Policy policy = TestCompliance.policy("Test policy", group.id());

		HttpResponse<JsonNode> response = apiClient.get("/policies");
		Assertions.assertEquals(
			Response.Status.OK.getStatusCode(), response.statusCode(),
			"Not getting 200 response for policy list");
		Assertions.assertEquals(1, response.body().size(),
			"Policy list doesn't have 1 element");

		JsonNode policyNode = response.body().get(0);
		Assertions.assertEquals(policy.id(), policyNode.get("id").asLong(),
			"The returned policy doesn't have the right ID");
		Assertions.assertEquals(policy.name(), policyNode.get("name").asText(),
			"The returned policy doesn't have the right name");
		Assertions.assertEquals(1, policyNode.get("targetGroups").size(),
			"The returned policy doesn't have the right number of target groups");
		Assertions.assertEquals(group.id(), policyNode.get("targetGroups").get(0).get("id").asLong(),
			"The returned policy target group doesn't have the right ID");
		Assertions.assertFalse(policyNode.has("rules"),
			"The policy list response should not embed the rules of each policy");
	}

	@Test
	@DisplayName("List rules")
	void listRules() throws IOException, InterruptedException {
		{
			HttpResponse<JsonNode> response = apiClient.get("/rules");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for initial rule list");
			Assertions.assertEquals(0, response.body().size(),
				"Rule list is not empty");
		}

		Policy policy = TestCompliance.policy("Test policy");
		TextRule textRule = TestCompliance.rule(TextRule.builder("Text rule", policy.id()).build());
		JavaScriptRule jsRule = TestCompliance.rule(JavaScriptRule.of("JS rule", policy.id()).toBuilder().enabled(true).build());
		PythonRule pyRule = TestCompliance.rule(PythonRule.of("Python rule", policy.id()));

		HttpResponse<JsonNode> response = apiClient.get("/rules");
		Assertions.assertEquals(
			Response.Status.OK.getStatusCode(), response.statusCode(),
			"Not getting 200 response for rule list");
		Assertions.assertEquals(3, response.body().size(),
			"Rule list doesn't have 3 elements");

		Map<Long, JsonNode> rulesById = new HashMap<>();
		for (JsonNode ruleNode : response.body()) {
			rulesById.put(ruleNode.get("id").asLong(), ruleNode);
		}

		JsonNode textNode = rulesById.get(textRule.id());
		Assertions.assertNotNull(textNode, "The text rule is missing from the rule list");
		Assertions.assertEquals("Text rule", textNode.get("name").asText());
		Assertions.assertEquals("TextRule", textNode.get("type").asText());
		Assertions.assertFalse(textNode.get("enabled").asBoolean());
		Assertions.assertEquals(policy.id(), textNode.get("policyId").asLong());
		Assertions.assertFalse(textNode.has("script"),
			"The rule list response should not expose the heavier rule fields");

		JsonNode jsNode = rulesById.get(jsRule.id());
		Assertions.assertNotNull(jsNode, "The JavaScript rule is missing from the rule list");
		Assertions.assertEquals("JS rule", jsNode.get("name").asText());
		Assertions.assertEquals("JavaScriptRule", jsNode.get("type").asText());
		Assertions.assertTrue(jsNode.get("enabled").asBoolean());
		Assertions.assertEquals(policy.id(), jsNode.get("policyId").asLong());
		Assertions.assertFalse(jsNode.has("script"),
			"The rule list response should not expose the heavier rule fields");

		JsonNode pyNode = rulesById.get(pyRule.id());
		Assertions.assertNotNull(pyNode, "The Python rule is missing from the rule list");
		Assertions.assertEquals("Python rule", pyNode.get("name").asText());
		Assertions.assertEquals("PythonRule", pyNode.get("type").asText());
		Assertions.assertFalse(pyNode.get("enabled").asBoolean());
		Assertions.assertEquals(policy.id(), pyNode.get("policyId").asLong());
	}

}
