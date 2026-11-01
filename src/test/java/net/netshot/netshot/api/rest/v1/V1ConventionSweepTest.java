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
package net.netshot.netshot.api.rest.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;

import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.api.V1Resources;
import net.netshot.netshot.testing.api.V1Resources.Endpoint;

/**
 * Checks the conventions of the v1 API on every endpoint, found by scanning the resources (a new endpoint is checked
 * without anything to declare): authentication (401) and roles (403) on every endpoint, 404 for unknown objects, and
 * the list and statistics parameters, formats and errors (problem details) on every list.
 */
@ApiTest
@DisplayName("v1 conventions on every endpoint")
@ResourceLock("DB")
public class V1ConventionSweepTest {

	/** The endpoints which need no authentication (any change here is a security decision). */
	private static final Set<String> PUBLIC = Set.of(
		"GET /health",
		"POST /auth/session",
		"DELETE /auth/session",
		"GET /system/cluster/master",
		"GET /openapi.json",
		"GET /openapi.yaml");

	/** The endpoints which don't answer 404 for an unknown object, and why. */
	private static final Map<String, String> NOT_404 = Map.of();

	private static final String PROBLEM = "application/problem+json";

	private TestServer server;
	private V1Client anonymous;
	private V1Client admin;

	@BeforeEach
	void setUp(TestServer testServer) {
		this.server = testServer;
		this.anonymous = new V1Client(testServer, null);
		this.admin = new V1Client(testServer, User.LEVEL_ADMIN);
	}

	// Sends a request the endpoint accepts (its media types), so that the answer is about the conventions checked
	private static Answer send(V1Client client, Endpoint endpoint, String path) throws Exception {
		boolean body = !"GET".equals(endpoint.httpMethod()) && !"DELETE".equals(endpoint.httpMethod());
		return client.send(endpoint.httpMethod(), path, body ? "{}" : null, "Accept", endpoint.accept() + ", " + PROBLEM,
			"Content-Type", body ? endpoint.contentType() : null);
	}

	private static void assertProblem(Answer answer, int status, String context) {
		Assertions.assertEquals(status, answer.status(), context + " -> " + answer);
		String type = answer.header("Content-Type");
		Assertions.assertTrue(type != null && type.startsWith(PROBLEM), context + ": not a problem (" + type + ")");
	}

	@Test
	@DisplayName("The public endpoints are the expected ones")
	void publicEndpoints() throws Exception {
		Set<String> found = new TreeSet<>();
		for (Endpoint endpoint : V1Resources.endpoints()) {
			if (endpoint.role() == null) {
				found.add(endpoint.key());
			}
		}
		Assertions.assertEquals(new TreeSet<>(PUBLIC), found, "Endpoints without @RolesAllowed (a new public endpoint "
			+ "must be added to PUBLIC on purpose)");
	}

	@TestFactory
	@DisplayName("Without authentication: 401 with problem details")
	Stream<DynamicTest> unauthenticated() throws Exception {
		return V1Resources.endpoints().stream().filter(e -> e.role() != null).map(endpoint ->
			DynamicTest.dynamicTest(endpoint.key(), () ->
				assertProblem(send(this.anonymous, endpoint, endpoint.unknownPath()), 401, endpoint.key())));
	}

	@TestFactory
	@DisplayName("With the level just below the role: 403 with problem details")
	Stream<DynamicTest> insufficientLevel() throws Exception {
		List<DynamicTest> tests = new ArrayList<>();
		for (Endpoint endpoint : V1Resources.endpoints()) {
			User.Role role = endpoint.role();
			if (role == null || role == User.Role.READONLY) {
				continue;
			}
			User.Role below = User.Role.values()[role.ordinal() - 1];
			tests.add(DynamicTest.dynamicTest(endpoint.key() + " (as " + below.getName() + ")", () ->
				assertProblem(send(new V1Client(this.server, below.getLevel()), endpoint, endpoint.unknownPath()), 403,
					endpoint.key())));
		}
		return tests.stream();
	}

	@TestFactory
	@DisplayName("Unknown object: 404 with problem details")
	Stream<DynamicTest> unknownObjects() throws Exception {
		return V1Resources.endpoints().stream()
			.filter(e -> e.hasPathParameters() && ("GET".equals(e.httpMethod()) || "DELETE".equals(e.httpMethod())))
			.filter(e -> !NOT_404.containsKey(e.key()))
			.map(endpoint -> DynamicTest.dynamicTest(endpoint.key(), () ->
				assertProblem(send(this.admin, endpoint, endpoint.unknownPath()), 404, endpoint.key())));
	}

	@TestFactory
	@DisplayName("Lists: envelope, strict parameters, formats")
	Stream<DynamicTest> lists() throws Exception {
		return V1Resources.endpoints().stream().filter(e -> e.isList() && !e.hasPathParameters()).map(endpoint ->
			DynamicTest.dynamicTest(endpoint.key(), () -> {
				String path = endpoint.template();
				Answer page = this.admin.get(path);
				Assertions.assertEquals(200, page.status(), path + " -> " + page);
				JsonNode json = page.json();
				Set<String> keys = new TreeSet<>();
				json.fieldNames().forEachRemaining(keys::add);
				Assertions.assertEquals(Set.of("count", "limit", "next", "offset", "previous", "results"), keys, path);
				Assertions.assertTrue(json.get("results").isArray(), path);

				Answer unknown = this.admin.get(path + "?zzzUnknown=1");
				assertProblem(unknown, 400, path + "?zzzUnknown=1");
				Assertions.assertEquals("zzzUnknown", unknown.json().get("errors").get(0).get("field").asText(), path);
				for (String query : List.of("limit=0", "offset=-1", "sort=zzzUnknown", "fields=zzzUnknown", "view=zzz", "count=maybe")) {
					assertProblem(this.admin.get(path + "?" + query), 400, path + "?" + query);
				}

				Answer xml = this.admin.get(path, "Accept", "application/xml");
				Assertions.assertEquals(200, xml.status(), path + " (XML) -> " + xml);
				Assertions.assertTrue(xml.body().startsWith("<list>"), path + " (XML): " + xml.body());
				Answer yaml = this.admin.get(path, "Accept", "application/yaml");
				Assertions.assertEquals(200, yaml.status(), path + " (YAML) -> " + yaml);
				Assertions.assertTrue(yaml.body().contains("results:"), path + " (YAML): " + yaml.body());
				Answer csv = this.admin.get(path, "Accept", "text/csv");
				Assertions.assertEquals(200, csv.status(), path + " (CSV) -> " + csv);
				Assertions.assertTrue(csv.header("Content-Type").startsWith("text/csv"), path + " (CSV)");
			}));
	}

	@TestFactory
	@DisplayName("Statistics: counts, strict parameters")
	Stream<DynamicTest> stats() throws Exception {
		return V1Resources.endpoints().stream().filter(e -> e.isStats() && !e.hasPathParameters()).map(endpoint ->
			DynamicTest.dynamicTest(endpoint.key(), () -> {
				String path = endpoint.template();
				Answer stats = this.admin.get(path);
				Assertions.assertEquals(200, stats.status(), path + " -> " + stats);
				Assertions.assertTrue(stats.json().has("results"), path);
				for (String query : List.of("groupBy=zzzUnknown", "interval=century", "zzzUnknown=1", "limit=10")) {
					assertProblem(this.admin.get(path + "?" + query), 400, path + "?" + query);
				}
				Answer daily = this.admin.get(path + "?interval=day");
				Assertions.assertEquals(200, daily.status(), path + "?interval=day -> " + daily);
				Assertions.assertEquals(7, daily.json().get("results").size(), path + ": 7 daily buckets by default");
			}));
	}

	@Test
	@DisplayName("The exceptions are still needed")
	void exceptionsAreCurrent() throws Exception {
		Set<String> keys = new TreeSet<>();
		V1Resources.endpoints().forEach(e -> keys.add(e.key()));
		for (String key : NOT_404.keySet()) {
			Assertions.assertTrue(keys.contains(key), "Stale exception: " + key);
		}
	}
}
