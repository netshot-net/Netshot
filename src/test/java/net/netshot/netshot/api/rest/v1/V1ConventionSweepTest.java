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
				if (endpoint.isUnpaged()) {
					// A single page, as documented: no paging, sort or search
					for (String query : List.of("limit=0", "offset=1", "sort=id", "q=x", "count=false", "count=true")) {
						assertProblem(this.admin.get(path + "?" + query), 400, path + "?" + query);
					}
				}
				else {
					Answer all = this.admin.get(path + "?limit=0&count=false");
					Assertions.assertEquals(200, all.status(), path + "?limit=0 -> " + all);
					Assertions.assertEquals(0, all.json().get("limit").asInt(), path + "?limit=0");
					Assertions.assertTrue(all.json().get("next").isNull(), path + "?limit=0");
					Assertions.assertTrue(json.get("count").isNull(), path + ": not counted by default");
					Answer counted = this.admin.get(path + "?limit=0&count=true");
					Assertions.assertEquals(counted.json().get("results").size(), counted.json().get("count").asInt(), path + "?count=true");
				}
				for (String query : List.of("limit=-1", "offset=-1", "sort=zzzUnknown", "fields=zzzUnknown", "view=zzz", "count=maybe")) {
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

	/** The common query parameters, whose documentation is checked against what each GET endpoint accepts. */
	private static final List<String> COMMON_PARAMETERS = List.of(Selection.VIEW, Selection.FIELDS, Selection.OMIT, Selection.EXPAND,
		ListParams.OFFSET, ListParams.LIMIT, ListParams.SORT, ListParams.SEARCH, ListParams.COUNT,
		StatsParams.GROUP_BY, StatsParams.INTERVAL, StatsParams.ON, StatsParams.FROM, StatsParams.TO, StatsParams.TIME_ZONE);

	// Whether the answer refuses the parameter as unknown (rather than its value, or not at all)
	private static boolean refusedAsUnknown(Answer answer, String parameter) {
		if (answer.status() != 400) {
			return false;
		}
		JsonNode errors = answer.json().get("errors");
		if (errors == null || errors.isEmpty() || !parameter.equals(errors.get(0).get("field").asText())) {
			return false;
		}
		String message = errors.get(0).get("message").asText();
		return message.startsWith("Unknown parameter") || message.startsWith("Unknown filter parameter")
			|| message.endsWith("doesn't apply to statistics");
	}

	@Test
	@DisplayName("OpenAPI: the lists and statistics name their schema, to declare their filters")
	void listsNameTheirSchema() throws Exception {
		Set<String> unnamed = new TreeSet<>();
		for (Endpoint endpoint : V1Resources.endpoints()) {
			boolean filtered = (endpoint.method().getReturnType() == ListResponse.class && !endpoint.isUnpaged()) || endpoint.isStats();
			if (filtered != endpoint.method().isAnnotationPresent(ListFilters.class)) {
				unnamed.add(endpoint.key());
			}
		}
		Assertions.assertEquals(Set.of(), unnamed, "Lists and statistics without @ListFilters (or other endpoints with it)");
	}

	// A valid value of a documented filter parameter, from its schema
	private static String sampleValue(JsonNode parameter) {
		JsonNode schema = parameter.get("schema");
		if (schema.has("items")) {
			schema = schema.get("items");
		}
		String description = schema.path("description").asText();
		if (schema.has("enum")) {
			return schema.get("enum").get(0).asText();
		}
		return switch (schema.path("type").asText()) {
			case "boolean" -> "true";
			case "integer" -> "1";
			default -> description.startsWith("IP subnet") ? "10.0.0.0/8"
				: description.startsWith("IP address") ? "10.0.0.1"
				: description.contains("date") ? "2026-01-01" : "a";
		};
	}

	@TestFactory
	@DisplayName("OpenAPI: each documented filter is accepted")
	Stream<DynamicTest> documentedFilters() throws Exception {
		JsonNode paths = this.anonymous.get("/openapi.json").json().get("paths");
		return V1Resources.endpoints().stream().filter(e -> (e.isList() && !e.isUnpaged()) || e.isStats())
			.map(endpoint -> DynamicTest.dynamicTest(endpoint.key(), () -> {
				String path = endpoint.hasPathParameters() ? endpoint.unknownPath() : endpoint.template();
				int filters = 0;
				for (JsonNode parameter : paths.get(endpoint.template()).get("get").get("parameters")) {
					if (!parameter.path("description").asText().startsWith("Filter: ")) {
						continue;
					}
					filters++;
					String query = parameter.get("name").asText() + "=" + sampleValue(parameter);
					// (an unknown object is reported once the parameters are read)
					Answer answer = send(this.admin, endpoint, path + "?" + query);
					Assertions.assertNotEquals(400, answer.status(), endpoint.key() + "?" + query + " -> " + answer);
				}
				Assertions.assertTrue(filters > 0, endpoint.key() + ": no documented filter");
			}));
	}

	@TestFactory
	@DisplayName("OpenAPI: each GET endpoint documents the common parameters it accepts, and only them")
	Stream<DynamicTest> documentedParameters() throws Exception {
		JsonNode paths = this.anonymous.get("/openapi.json").json().get("paths");
		return V1Resources.endpoints().stream().filter(e -> "GET".equals(e.httpMethod()) && !PUBLIC.contains(e.key()))
			.map(endpoint -> DynamicTest.dynamicTest(endpoint.key(), () -> {
				JsonNode operation = paths.get(endpoint.template()).get("get");
				Set<String> documented = new TreeSet<>();
				if (operation.has("parameters")) {
					operation.get("parameters").forEach(p -> documented.add(p.get("name").asText()));
				}
				String path = endpoint.hasPathParameters() ? endpoint.unknownPath() : endpoint.template();
				for (String parameter : COMMON_PARAMETERS) {
					Answer answer = send(this.admin, endpoint, path + "?" + parameter + "=1");
					if (documented.contains(parameter)) {
						Assertions.assertFalse(refusedAsUnknown(answer, parameter),
							endpoint.key() + ": documented '" + parameter + "' refused -> " + answer);
					}
					else if (answer.status() != 404) {
						// (an unknown object can be reported before the parameters are checked)
						Assertions.assertTrue(refusedAsUnknown(answer, parameter),
							endpoint.key() + ": undocumented '" + parameter + "' not refused -> " + answer);
					}
				}
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
