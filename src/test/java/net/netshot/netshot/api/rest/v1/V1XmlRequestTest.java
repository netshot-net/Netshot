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
package net.netshot.netshot.api.rest.v1;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.v1.V1Client;
import net.netshot.netshot.testing.api.v1.V1Client.Answer;

/**
 * Checks that the v1 request bodies are accepted in XML as well as in JSON: what gets stored from an XML
 * request must be what gets stored from the equivalent JSON request, for a simple object, a polymorphic one
 * (credential set) and a list of IDs (target groups of a policy). The root element of the XML request is not
 * significant.
 */
@ApiTest
@DisplayName("v1 request bodies in XML")
class V1XmlRequestTest {

	private V1Client admin;

	@BeforeEach
	void setUp(TestServer server) {
		this.admin = new V1Client(server, UiUser.LEVEL_ADMIN);
	}

	/**
	 * Sends a request, expecting a success.
	 *
	 * @param method the HTTP method
	 * @param path the path under the v1 API
	 * @param contentType the type of the body
	 * @param body the body
	 * @return the answer, as JSON
	 */
	private JsonNode send(String method, String path, String contentType, String body) throws Exception {
		Answer answer = this.admin.send(method, path, body, "Content-Type", contentType);
		Assertions.assertTrue(answer.status() >= 200 && answer.status() < 300, "%s %s -> %s".formatted(method, path, answer));
		return answer.json();
	}

	private JsonNode json(String method, String path, String body) throws Exception {
		return this.send(method, path, "application/json", body);
	}

	private JsonNode xml(String method, String path, String body) throws Exception {
		return this.send(method, path, "application/xml", body);
	}

	/**
	 * Copy of an object without the properties which differ between two creations.
	 *
	 * @param node the object
	 * @return the normalized copy
	 */
	private static JsonNode normalized(JsonNode node) {
		ObjectNode copy = node.deepCopy();
		copy.remove("id");
		copy.remove("url");
		copy.remove("name");
		copy.remove("display");
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
	@DisplayName("Simple object")
	void simpleRequest() throws Exception {
		JsonNode fromJson = this.json("POST", "/domains", """
			{"name": "Json DC", "description": "A data center", "server4Address": "10.254.1.1"}""");
		JsonNode fromXml = this.xml("POST", "/domains", """
			<domain><name>Xml DC</name><description>A data center</description><server4Address>10.254.1.1</server4Address></domain>""");
		Assertions.assertEquals("Xml DC", fromXml.get("name").asText());
		Assertions.assertNotEquals(fromJson.get("id").asLong(), fromXml.get("id").asLong());
		Assertions.assertEquals(normalized(fromJson), normalized(fromXml));

		// PUT, with another root element: the root element is not significant
		long id = fromXml.get("id").asLong();
		this.xml("PUT", "/domains/" + id, """
			<anything><name>Xml DC 2</name><description>Renamed</description><server4Address>10.254.1.9</server4Address></anything>""");
		JsonNode updated = this.admin.get("/domains/" + id).json();
		Assertions.assertEquals("Xml DC 2", updated.get("name").asText());
		Assertions.assertEquals("Renamed", updated.get("description").asText());
		Assertions.assertEquals("10.254.1.9", updated.get("server4Address").asText());
	}

	@Test
	@DisplayName("Polymorphic object")
	void polymorphicRequest() throws Exception {
		JsonNode fromJson = this.json("POST", "/credential-sets", """
			{"type": "SSH", "name": "Json SSH", "username": "user", "password": "pass", "superPassword": "super"}""");
		JsonNode fromXml = this.xml("POST", "/credential-sets", """
			<credentialSet><type>SSH</type><name>Xml SSH</name><username>user</username><password>pass</password>\
			<superPassword>super</superPassword></credentialSet>""");
		Assertions.assertEquals("SSH", fromXml.get("type").asText());
		Assertions.assertEquals("user", fromXml.get("username").asText());
		Assertions.assertEquals(normalized(fromJson), normalized(fromXml));
	}

	@Test
	@DisplayName("List of IDs")
	void listRequest() throws Exception {
		long id1 = this.xml("POST", "/groups", """
			<group><name>Xml group 1</name><type>DYNAMIC</type><query>[Name] CONTAINS "a"</query></group>""").get("id").asLong();
		long id2 = this.json("POST", "/groups", """
			{"name": "Json group 2", "type": "DYNAMIC", "query": "[Name] CONTAINS \\"b\\""}""").get("id").asLong();
		JsonNode fromJson = this.json("POST", "/compliance/policies", """
			{"name": "Json policy", "targetGroups": [%d, %d]}""".formatted(id1, id2));
		JsonNode fromXml = this.xml("POST", "/compliance/policies", """
			<policy><name>Xml policy</name><targetGroups>%d</targetGroups><targetGroups>%d</targetGroups></policy>"""
			.formatted(id1, id2));
		Assertions.assertEquals(Set.of(id1, id2), groupIds(fromXml));
		Assertions.assertEquals(groupIds(fromJson), groupIds(fromXml));
	}
}
