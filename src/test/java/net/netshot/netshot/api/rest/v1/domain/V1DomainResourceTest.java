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
package net.netshot.netshot.api.rest.v1.domain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;

/**
 * The domains of the v1 API, over HTTP: also the reference test of the v1 conventions (envelope, filters,
 * selection, problem details, ETags, PUT/PATCH, formats).
 */
@ApiTest
@DisplayName("v1 domains")
@ResourceLock("DB")
public class V1DomainResourceTest {

	private TestServer server;
	private V1Client admin;

	@BeforeEach
	void setUp(TestServer testServer) {
		this.server = testServer;
		this.admin = new V1Client(testServer, UiUser.LEVEL_ADMIN);
		TestWiring.get().transactions().run(dsl -> {
			dsl.deleteFrom(Tables.DEVICE_CREDENTIAL_SET).execute();
			dsl.deleteFrom(Tables.DEVICE).execute();
			dsl.deleteFrom(Tables.DOMAIN).execute();
		});
	}

	private static String domainJson(String name, String description, String address) {
		return """
			{"name": %s, "description": %s, "server4Address": %s}"""
			.formatted(quote(name), quote(description), quote(address));
	}

	private static String quote(String text) {
		return text == null ? "null" : "\"" + text + "\"";
	}

	private long create(String name, String description, String address) throws IOException, InterruptedException {
		Answer answer = this.admin.post("/domains", domainJson(name, description, address));
		Assertions.assertEquals(201, answer.status(), answer.toString());
		return answer.json().get("id").asLong();
	}

	private static Set<String> names(JsonNode object) {
		Set<String> names = new java.util.TreeSet<>();
		object.fieldNames().forEachRemaining(names::add);
		return names;
	}

	private static List<String> values(JsonNode list, String property) {
		List<String> values = new ArrayList<>();
		list.get("results").forEach(item -> values.add(item.get(property).asText()));
		return values;
	}

	@Test
	@DisplayName("Create a domain: 201, Location, ETag, full representation")
	void create() throws Exception {
		Answer answer = this.admin.post("/domains", domainJson("Paris", "Paris DC", "10.0.1.1"));
		Assertions.assertEquals(201, answer.status(), answer.toString());
		JsonNode domain = answer.json();
		long id = domain.get("id").asLong();
		Assertions.assertEquals("/api/v1/domains/" + id, answer.header("Location"));
		Assertions.assertEquals("\"0\"", answer.header("ETag"));
		Assertions.assertEquals(Set.of("id", "url", "display", "version", "changeDate", "name", "description",
			"server4Address", "server6Address"), names(domain));
		Assertions.assertEquals("/api/v1/domains/" + id, domain.get("url").asText());
		Assertions.assertEquals("Paris", domain.get("display").asText());
		Assertions.assertEquals("10.0.1.1", domain.get("server4Address").asText());
		Assertions.assertTrue(domain.get("changeDate").asText().matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z"),
			domain.get("changeDate").asText());
	}

	@Test
	@DisplayName("Invalid bodies are refused with problem details")
	void invalidBodies() throws Exception {
		Answer missing = this.admin.post("/domains", "{\"description\": \"x\", \"server4Address\": \"10.0.0.1\"}");
		Assertions.assertEquals(400, missing.status(), missing.toString());
		Assertions.assertTrue(missing.header("Content-Type").startsWith("application/problem+json"));
		Assertions.assertEquals("NETSHOT_INVALID_REQUEST_PARAMETER", missing.json().get("code").asText());
		Assertions.assertEquals("name", missing.json().get("errors").get(0).get("field").asText());

		Answer unknown = this.admin.post("/domains", "{\"name\": \"A\", \"server4Address\": \"10.0.0.1\", \"color\": \"red\"}");
		Assertions.assertEquals(400, unknown.status(), unknown.toString());
		Assertions.assertEquals("color", unknown.json().get("errors").get(0).get("field").asText());

		Answer malformed = this.admin.post("/domains", "{\"name\": ");
		Assertions.assertEquals(400, malformed.status(), malformed.toString());

		Answer badAddress = this.admin.post("/domains", domainJson("A", null, "300.0.0.1"));
		Assertions.assertEquals(400, badAddress.status(), badAddress.toString());

		this.create("A", null, "10.0.0.1");
		Answer duplicate = this.admin.post("/domains", domainJson("A", null, "10.0.0.2"));
		Assertions.assertEquals(409, duplicate.status(), duplicate.toString());
		JsonNode problem = duplicate.json();
		Assertions.assertEquals("urn:netshot:error:duplicate-domain", problem.get("type").asText());
		Assertions.assertEquals("Duplicate domain", problem.get("title").asText());
		Assertions.assertEquals(409, problem.get("status").asInt());
		Assertions.assertEquals(111, problem.get("errorCode").asInt());
		Assertions.assertEquals("/api/v1/domains", problem.get("instance").asText());

		// Read-only properties are ignored, so that a read representation can be sent back
		Answer roundTrip = this.admin.post("/domains",
			"{\"id\": 5, \"url\": \"x\", \"version\": 3, \"name\": \"B\", \"server4Address\": \"10.0.0.3\", \"server6Address\": \"::\"}");
		Assertions.assertEquals(201, roundTrip.status(), roundTrip.toString());
	}

	@Test
	@DisplayName("List: envelope, brief view, count and pages")
	void listPages() throws Exception {
		for (int i = 1; i <= 5; i++) {
			this.create("D" + i, "Domain " + i, "10.0.0." + i);
		}
		Answer first = this.admin.get("/domains?limit=2");
		Assertions.assertEquals(200, first.status(), first.toString());
		JsonNode page = first.json();
		Assertions.assertEquals(5, page.get("count").asLong());
		Assertions.assertEquals(0, page.get("offset").asInt());
		Assertions.assertEquals(2, page.get("limit").asInt());
		Assertions.assertEquals("/api/v1/domains?offset=2&limit=2", page.get("next").asText());
		Assertions.assertTrue(page.get("previous").isNull());
		Assertions.assertEquals(List.of("D1", "D2"), values(page, "name"));
		Assertions.assertEquals(Set.of("id", "url", "display", "name"), names(page.get("results").get(0)));

		JsonNode last = this.admin.get("/domains?limit=2&offset=4").json();
		Assertions.assertEquals(List.of("D5"), values(last, "name"));
		Assertions.assertTrue(last.get("next").isNull());
		Assertions.assertEquals("/api/v1/domains?offset=2&limit=2", last.get("previous").asText());

		JsonNode uncounted = this.admin.get("/domains?limit=2&count=false").json();
		Assertions.assertTrue(uncounted.get("count").isNull());
		Assertions.assertFalse(uncounted.get("next").isNull());

		// No maximum by default (netshot.http.api.pagesize.max)
		Assertions.assertEquals(200, this.admin.get("/domains?limit=100000").status());
		Assertions.assertEquals(400, this.admin.get("/domains?limit=0").status());
		Assertions.assertEquals(400, this.admin.get("/domains?offset=-1").status());
		Assertions.assertEquals(400, this.admin.get("/domains?limit=abc").status());
	}

	@Test
	@DisplayName("List: filters, search and sort")
	void listFilters() throws Exception {
		this.create("Paris-1", "Paris DC", "10.1.0.1");
		this.create("Paris-2", "Paris backup", "10.1.0.2");
		this.create("Lyon", "Lyon 100% DC", "192.168.0.1");
		this.create("lyon_b", null, "192.168.0.2");

		Assertions.assertEquals(List.of("Lyon"), values(this.admin.get("/domains?name=Lyon").json(), "name"));
		Assertions.assertEquals(List.of("Lyon", "lyon_b"), values(this.admin.get("/domains?name__isw=lyon").json(), "name"));
		Assertions.assertEquals(List.of("Lyon", "lyon_b"), values(this.admin.get("/domains?name__ic=YON").json(), "name"));
		Assertions.assertEquals(List.of("Paris-1", "Paris-2"), values(this.admin.get("/domains?name__nic=yon").json(), "name"));
		Assertions.assertEquals(List.of("Lyon", "Paris-1"), values(this.admin.get("/domains?name=Lyon&name=Paris-1").json(), "name"));
		Assertions.assertEquals(List.of("Lyon", "lyon_b"),
			values(this.admin.get("/domains?name__n=Paris-1&name__n=Paris-2").json(), "name"));
		// LIKE wildcards are literal
		Assertions.assertEquals(List.of("lyon_b"), values(this.admin.get("/domains?name__ic=_").json(), "name"));
		Assertions.assertEquals(List.of("Lyon"), values(this.admin.get("/domains?description__ic=100%25").json(), "name"));
		Assertions.assertEquals(List.of("lyon_b"), values(this.admin.get("/domains?description__empty=true").json(), "name"));
		Assertions.assertEquals(List.of("Paris-1", "Paris-2"), values(this.admin.get("/domains?name__regex=%5EP.*%5Cd$").json(), "name"));
		Assertions.assertEquals(List.of("Lyon", "lyon_b"),
			values(this.admin.get("/domains?server4Address__net=192.168.0.0/16").json(), "name"));
		Assertions.assertEquals(List.of("Paris-2"), values(this.admin.get("/domains?server4Address=10.1.0.2").json(), "name"));
		// Free text search: name and description
		Assertions.assertEquals(List.of("Paris-2"), values(this.admin.get("/domains?q=BACKUP").json(), "name"));
		// Several filters: all must match
		Assertions.assertEquals(List.of("Paris-1"), values(this.admin.get("/domains?name__isw=paris&description__ic=dc").json(), "name"));
		// Sort
		Assertions.assertEquals(List.of("Paris-2", "Paris-1"), values(this.admin.get("/domains?name__isw=paris&sort=-name").json(), "name"));
		Assertions.assertEquals(List.of("lyon_b", "Lyon", "Paris-2", "Paris-1"), values(this.admin.get("/domains?sort=-id").json(), "name"));
		Assertions.assertEquals(List.of("Paris-1", "Paris-2", "Lyon", "lyon_b"), values(this.admin.get("/domains?sort=id").json(), "name"));
		Assertions.assertEquals(List.of("Paris-1", "Paris-2"),
			values(this.admin.get("/domains?changeDate__gte=2000-01-01&name__isw=P").json(), "name"));

		// Strict parameters
		for (String query : List.of("color=red", "name__foo=x", "server4Address__ic=10", "server4Address=abc",
			"server4Address__net=10.0.0.0", "changeDate__gt=yesterday", "sort=description", "sort=foo", "name__regex=(",
			"description__empty=maybe", "id=x")) {
			Answer answer = this.admin.get("/domains?" + query);
			Assertions.assertEquals(400, answer.status(), query + " -> " + answer);
			Assertions.assertEquals("NETSHOT_INVALID_REQUEST_PARAMETER", answer.json().get("code").asText(), query);
		}
		Answer unknown = this.admin.get("/domains?color=red");
		Assertions.assertEquals("color", unknown.json().get("errors").get(0).get("field").asText());
	}

	@Test
	@DisplayName("Selection: view, fields, omit")
	void selection() throws Exception {
		long id = this.create("Paris", "Paris DC", "10.0.1.1");
		Assertions.assertEquals(Set.of("id", "url", "display", "version", "changeDate", "name", "description",
			"server4Address", "server6Address"), names(this.admin.get("/domains?view=full").json().get("results").get(0)));
		Assertions.assertEquals(Set.of("id", "name", "description"),
			names(this.admin.get("/domains?fields=name,description").json().get("results").get(0)));
		Assertions.assertEquals(Set.of("id", "url", "display", "name"), names(this.admin.get("/domains/" + id + "?view=brief").json()));
		Assertions.assertEquals(Set.of("id", "server4Address"), names(this.admin.get("/domains/" + id + "?fields=server4Address").json()));
		Assertions.assertEquals(Set.of("id", "url", "display", "version", "changeDate", "name", "server4Address", "server6Address"),
			names(this.admin.get("/domains/" + id + "?omit=description").json()));
		Assertions.assertEquals(400, this.admin.get("/domains/" + id + "?fields=color").status());
		Assertions.assertEquals(400, this.admin.get("/domains/" + id + "?view=tiny").status());
		Assertions.assertEquals(400, this.admin.get("/domains/" + id + "?expand=name").status());
		Assertions.assertEquals(400, this.admin.get("/domains/" + id + "?name=Paris").status());
	}

	@Test
	@DisplayName("Get, ETag and conditional GET")
	void getAndEtag() throws Exception {
		long id = this.create("Paris", "Paris DC", "10.0.1.1");
		Answer answer = this.admin.get("/domains/" + id);
		Assertions.assertEquals(200, answer.status());
		Assertions.assertEquals("\"0\"", answer.header("ETag"));
		Answer notModified = this.admin.get("/domains/" + id, "If-None-Match", "\"0\"");
		Assertions.assertEquals(304, notModified.status());
		Assertions.assertEquals(200, this.admin.get("/domains/" + id, "If-None-Match", "\"7\"").status());

		Answer missing = this.admin.get("/domains/" + TestServer.UNKNOWN_ID);
		Assertions.assertEquals(404, missing.status());
		Assertions.assertEquals("NETSHOT_DOMAIN_NOT_FOUND", missing.json().get("code").asText());
		Assertions.assertEquals(404, this.admin.get("/nothing").status());
		Assertions.assertTrue(this.admin.get("/nothing").header("Content-Type").startsWith("application/problem+json"));
	}

	@Test
	@DisplayName("PUT replaces the domain, If-Match checks the version")
	void replace() throws Exception {
		long id = this.create("Paris", "Paris DC", "10.0.1.1");
		Answer replaced = this.admin.put("/domains/" + id, domainJson("Paris 2", null, "10.0.1.2"), "If-Match", "\"0\"");
		Assertions.assertEquals(200, replaced.status(), replaced.toString());
		Assertions.assertEquals("Paris 2", replaced.json().get("name").asText());
		Assertions.assertEquals("10.0.1.2", replaced.json().get("server4Address").asText());
		Assertions.assertEquals(1, replaced.json().get("version").asInt());
		Assertions.assertEquals("\"1\"", replaced.header("ETag"));

		Answer stale = this.admin.put("/domains/" + id, domainJson("Paris 3", null, "10.0.1.2"), "If-Match", "\"0\"");
		Assertions.assertEquals(412, stale.status(), stale.toString());
		Assertions.assertEquals("NETSHOT_VERSION_MISMATCH", stale.json().get("code").asText());
		Assertions.assertEquals(400, this.admin.put("/domains/" + id, domainJson("Paris 3", null, "10.0.1.2"), "If-Match", "W/\"1\"").status());

		// Absent optional property: reset (replacement), absent required property: refused
		Answer noDescription = this.admin.put("/domains/" + id, "{\"name\": \"Paris\", \"server4Address\": \"10.0.1.1\"}");
		Assertions.assertEquals(200, noDescription.status(), noDescription.toString());
		Assertions.assertEquals(400, this.admin.put("/domains/" + id, "{\"name\": \"Paris\"}").status());
		Assertions.assertEquals(404, this.admin.put("/domains/" + TestServer.UNKNOWN_ID, domainJson("X", null, "10.0.0.9")).status());
	}

	@Test
	@DisplayName("PATCH merges the given properties")
	void patch() throws Exception {
		long id = this.create("Paris", "Paris DC", "10.0.1.1");
		Answer patched = this.admin.patch("/domains/" + id, "{\"description\": \"Main DC\"}");
		Assertions.assertEquals(200, patched.status(), patched.toString());
		Assertions.assertEquals("Paris", patched.json().get("name").asText());
		Assertions.assertEquals("Main DC", patched.json().get("description").asText());
		Assertions.assertEquals("10.0.1.1", patched.json().get("server4Address").asText());

		Answer cleared = this.admin.patch("/domains/" + id, "{\"description\": null}");
		Assertions.assertEquals(200, cleared.status(), cleared.toString());
		Assertions.assertEquals("", cleared.json().get("description").asText());

		Assertions.assertEquals(412, this.admin.patch("/domains/" + id, "{\"name\": \"X\"}", "If-Match", "\"0\"").status());
		Assertions.assertEquals(200, this.admin.patch("/domains/" + id, "{\"name\": \"X\"}", "If-Match", "\"2\"").status());
		Assertions.assertEquals(400, this.admin.patch("/domains/" + id, "{\"color\": \"red\"}").status());
		Assertions.assertEquals(400, this.admin.patch("/domains/" + id, "[1]").status());
		Assertions.assertEquals(400, this.admin.patch("/domains/" + id, "{\"name\": null}").status());
		Assertions.assertEquals(404, this.admin.patch("/domains/" + TestServer.UNKNOWN_ID, "{\"name\": \"X\"}").status());

		Answer xml = this.admin.send("PATCH", "/domains/" + id, "<domain><description>From XML</description></domain>",
			"Content-Type", "application/xml");
		Assertions.assertEquals(200, xml.status(), xml.toString());
		Assertions.assertEquals("From XML", xml.json().get("description").asText());
	}

	@Test
	@DisplayName("DELETE: 204, then 404; If-Match")
	void delete() throws Exception {
		long id = this.create("Paris", "Paris DC", "10.0.1.1");
		Assertions.assertEquals(412, this.admin.delete("/domains/" + id, "If-Match", "\"5\"").status());
		Assertions.assertEquals(204, this.admin.delete("/domains/" + id, "If-Match", "\"0\"").status());
		Answer again = this.admin.delete("/domains/" + id);
		Assertions.assertEquals(404, again.status());
		Assertions.assertEquals("NETSHOT_DOMAIN_NOT_FOUND", again.json().get("code").asText());
	}

	@Test
	@DisplayName("XML and YAML")
	void formats() throws Exception {
		Answer created = this.admin.send("POST", "/domains",
			"<domain><name>Paris</name><description xsi:nil=\"true\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"/>"
				+ "<server4Address>10.0.1.1</server4Address></domain>",
			"Content-Type", "application/xml", "Accept", "application/xml");
		Assertions.assertEquals(201, created.status(), created.toString());
		Assertions.assertTrue(created.body().startsWith("<domain>"), created.body());
		Assertions.assertTrue(created.body().contains("<name>Paris</name>"), created.body());

		Answer list = this.admin.get("/domains?view=full", "Accept", "application/xml");
		Assertions.assertEquals(200, list.status(), list.toString());
		Assertions.assertTrue(list.body().startsWith("<list><count>1</count>"), list.body());
		Assertions.assertTrue(list.body().contains("<results><id>"), list.body());
		Assertions.assertTrue(list.body().contains("<previous xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>"),
			list.body());

		Answer yaml = this.admin.get("/domains", "Accept", "application/yaml");
		Assertions.assertEquals(200, yaml.status(), yaml.toString());
		Assertions.assertTrue(yaml.body().contains("count: 1"), yaml.body());

		Answer xmlProblem = this.admin.get("/domains/" + TestServer.UNKNOWN_ID, "Accept", "application/xml");
		Assertions.assertEquals(404, xmlProblem.status());
		Assertions.assertTrue(xmlProblem.header("Content-Type").startsWith("application/problem+xml"), xmlProblem.toString());
		Assertions.assertTrue(xmlProblem.body().startsWith("<problem>"), xmlProblem.body());
	}

	@Test
	@DisplayName("Authentication and permissions")
	void permissions() throws Exception {
		Answer anonymous = new V1Client(this.server, null).get("/domains");
		Assertions.assertEquals(401, anonymous.status());
		Assertions.assertEquals("application/problem+json", anonymous.header("Content-Type"));
		V1Client readOnly = new V1Client(this.server, UiUser.LEVEL_READONLY);
		Assertions.assertEquals(200, readOnly.get("/domains").status());
		Answer forbidden = readOnly.post("/domains", domainJson("A", null, "10.0.0.1"));
		Assertions.assertEquals(403, forbidden.status());
	}

	@Test
	@DisplayName("OpenAPI document, and v0 unchanged beside v1")
	void openApi() throws Exception {
		Answer doc = new V1Client(this.server, null).get("/openapi.json");
		Assertions.assertEquals(200, doc.status(), doc.toString());
		JsonNode openApi = doc.json();
		Assertions.assertTrue(openApi.get("paths").has("/domains/{id}"), doc.body());
		Assertions.assertTrue(openApi.get("components").get("schemas").has("Domain"), doc.body());
		Assertions.assertFalse(openApi.get("paths").has("/serverinfo"), "v0 resources in the v1 document");
		Assertions.assertTrue(openApi.get("paths").has("/devices"), doc.body());

		this.create("Paris", null, "10.0.1.1");
		java.net.http.HttpResponse<String> v0 = java.net.http.HttpClient.newHttpClient().send(
			java.net.http.HttpRequest.newBuilder(java.net.URI.create(this.server.apiUrl() + "/domains"))
				.header("X-Netshot-API-Token", TestServer.API_TOKENS.get(UiUser.LEVEL_ADMIN))
				.header("Accept", "application/json").build(),
			java.net.http.HttpResponse.BodyHandlers.ofString());
		Assertions.assertEquals(200, v0.statusCode());
		Assertions.assertTrue(v0.body().startsWith("[{"), v0.body());
	}
}
