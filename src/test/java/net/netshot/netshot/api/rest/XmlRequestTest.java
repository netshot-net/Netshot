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
package net.netshot.netshot.api.rest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.hibernate.Session;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.quartz.Scheduler;
import org.quartz.impl.SchedulerRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.UriBuilder;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.TestDrivers;
import net.netshot.netshot.TestNetwork;
import net.netshot.netshot.WithDatabaseTest;
import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.engine.TaskManager;

/**
 * Checks that the request bodies are accepted in XML as well as in JSON: what gets stored from
 * an XML request must be what gets stored from the equivalent JSON request. The root element of
 * the XML request is not significant.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class XmlRequestTest extends WithDatabaseTest {

	private static final String API_URL = TestNetwork.freeLocalApiUrl();
	private static final String TOKEN = "hLXg3ABu1qWJPle3Z3Z22X0aLkx5T354";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private HttpClient client;

	@BeforeAll
	void boot() throws Exception {
		Properties config = getFreshDatabaseConfig("xmlrequesttest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "INFO");
		config.setProperty("netshot.http.ssl.enabled", "false");
		URI uri = UriBuilder.fromUri(API_URL).replacePath("/").build();
		config.setProperty("netshot.http.baseurl", uri.toString());
		TestConfig.init(config);
		Netshot.loadModuleConfigs();
		Database.update();
		Wiring.init(TestDrivers.registry());
		TaskManager.init(Wiring.get().taskJobFactory());
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.createMutationQuery("delete from net.netshot.netshot.aaa.user.UiUser").executeUpdate();
			session.persist(new ApiToken("XML test token", TOKEN, UiUser.LEVEL_ADMIN));
			session.getTransaction().commit();
		}
		RestServer.init(Wiring.get().binder());
		Thread.sleep(1000);
		this.client = HttpClient.newHttpClient();
	}

	@AfterAll
	void shutdownSchedulers() throws Exception {
		RestServer.shutdown();
		for (Scheduler scheduler : SchedulerRepository.getInstance().lookupAll()) {
			scheduler.clear();
			// Other test classes may rely on a running scheduler.
			scheduler.start();
		}
	}

	private JsonNode call(String method, String path, String contentType, String body) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder()
			.uri(URI.create(API_URL + path))
			.header("Accept", "application/json")
			.header("X-Netshot-API-Token", TOKEN);
		if (body == null) {
			request.method(method, HttpRequest.BodyPublishers.noBody());
		}
		else {
			request.header("Content-Type", contentType);
			request.method(method, HttpRequest.BodyPublishers.ofString(body));
		}
		HttpResponse<String> response = this.client.send(request.build(), HttpResponse.BodyHandlers.ofString());
		Assertions.assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,
			"%s %s answered %d: %s".formatted(method, path, response.statusCode(), response.body()));
		return MAPPER.readTree(response.body());
	}

	private JsonNode json(String method, String path, String body) throws Exception {
		return this.call(method, path, "application/json", body);
	}

	private JsonNode xml(String method, String path, String body) throws Exception {
		return this.call(method, path, "application/xml", body);
	}

	/** Finds an element, by name, in the list the path returns. */
	private JsonNode byName(String path, String name) throws Exception {
		for (JsonNode node : this.json("GET", path, null)) {
			if (name.equals(node.path("name").asText())) {
				return node;
			}
		}
		throw new AssertionError("No element named %s in %s".formatted(name, path));
	}

	/** Copy of the node without the properties which differ between two creations. */
	private static JsonNode normalized(JsonNode node) {
		ObjectNode copy = node.deepCopy();
		copy.remove("id");
		copy.remove("name");
		copy.remove("changeDate");
		return copy;
	}

	private static Set<Long> groupIds(JsonNode policy) {
		Set<Long> ids = new HashSet<>();
		for (JsonNode group : policy.get("targetGroups")) {
			ids.add(group.get("id").asLong());
		}
		return ids;
	}

	@Test
	void simpleRequest() throws Exception {
		JsonNode fromJson = this.json("POST", "/domains", """
			{"name": "Json DC", "description": "A data center", "ipAddress": "10.254.1.1"}""");
		JsonNode fromXml = this.xml("POST", "/domains", """
			<domain><name>Xml DC</name><description>A data center</description><ipAddress>10.254.1.1</ipAddress></domain>""");
		Assertions.assertEquals("Xml DC", fromXml.get("name").asText());
		Assertions.assertNotEquals(fromJson.get("id").asLong(), fromXml.get("id").asLong());
		Assertions.assertEquals(normalized(fromJson), normalized(fromXml));
		Assertions.assertEquals(normalized(this.byName("/domains", "Json DC")),
			normalized(this.byName("/domains", "Xml DC")));

		// PUT, with another root element: the root element is not significant
		this.xml("PUT", "/domains/" + fromXml.get("id").asLong(), """
			<anything><name>Xml DC 2</name><description>Renamed</description><ipAddress>10.254.1.9</ipAddress></anything>""");
		JsonNode updated = this.byName("/domains", "Xml DC 2");
		Assertions.assertEquals("Renamed", updated.get("description").asText());
		Assertions.assertEquals("10.254.1.9", updated.get("ipAddress").asText());
	}

	@Test
	void polymorphicRequest() throws Exception {
		this.json("POST", "/credentialsets", """
			{"type": "SSH", "name": "Json SSH", "username": "user", "password": "pass", "superPassword": "super"}""");
		this.xml("POST", "/credentialsets", """
			<credentialSet type="SSH"><name>Xml SSH</name><username>user</username><password>pass</password>\
			<superPassword>super</superPassword></credentialSet>""");
		JsonNode fromJson = this.byName("/credentialsets", "Json SSH");
		JsonNode fromXml = this.byName("/credentialsets", "Xml SSH");
		Assertions.assertEquals("SSH", fromXml.get("type").asText());
		Assertions.assertEquals("user", fromXml.get("username").asText());
		Assertions.assertEquals(normalized(fromJson), normalized(fromXml));
	}

	@Test
	void listRequest() throws Exception {
		JsonNode group1 = this.xml("POST", "/groups", """
			<group><name>Xml group 1</name><type>DynamicDeviceGroup</type><query>[Name] CONTAINS "a"</query></group>""");
		JsonNode group2 = this.json("POST", "/groups", """
			{"name": "Json group 2", "type": "DynamicDeviceGroup", "query": "[Name] CONTAINS \\"b\\""}""");
		long id1 = group1.get("id").asLong();
		long id2 = group2.get("id").asLong();
		this.json("POST", "/policies", """
			{"name": "Json policy", "targetGroups": [%d, %d]}""".formatted(id1, id2));
		this.xml("POST", "/policies", """
			<policy><name>Xml policy</name><targetGroups><targetGroups>%d</targetGroups><targetGroups>%d</targetGroups></targetGroups></policy>"""
			.formatted(id1, id2));
		JsonNode fromJson = this.byName("/policies", "Json policy");
		JsonNode fromXml = this.byName("/policies", "Xml policy");
		Assertions.assertEquals(Set.of(id1, id2), groupIds(fromXml));
		Assertions.assertEquals(groupIds(fromJson), groupIds(fromXml));
	}
}
