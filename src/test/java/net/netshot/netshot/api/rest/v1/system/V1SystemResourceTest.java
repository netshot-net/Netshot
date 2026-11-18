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
package net.netshot.netshot.api.rest.v1.system;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.v1.V1Client;
import net.netshot.netshot.testing.api.v1.V1Client.Answer;
import net.netshot.netshot.testing.api.v1.V1Endpoints;
import net.netshot.netshot.platform.runtime.BuildInfo;

@ApiTest
@V1Endpoints({
	"GET /system/info",
	"GET /system/cluster/members",
	"GET /system/cluster/master",
	"GET /system/services",
	"GET /health"
})
@DisplayName("v1 system")
@ResourceLock("DB")
class V1SystemResourceTest {

	private TestServer server;
	private V1Client admin;
	private V1Client anonymous;

	@BeforeEach
	void setUp(TestServer testServer) {
		this.server = testServer;
		this.admin = new V1Client(testServer, UiUser.LEVEL_ADMIN);
		this.anonymous = new V1Client(testServer, null);
	}

	private static Set<String> names(JsonNode object) {
		Set<String> names = new TreeSet<>();
		object.fieldNames().forEachRemaining(names::add);
		return names;
	}

	@Test
	@DisplayName("Server information")
	void info() throws Exception {
		Answer answer = this.admin.get("/system/info");
		Assertions.assertEquals(200, answer.status(), answer.toString());
		JsonNode info = answer.json();
		Assertions.assertEquals(Set.of("version", "serverTime", "sessionMaxIdleTime"), names(info));
		Assertions.assertEquals(BuildInfo.VERSION, info.get("version").asText());
		Assertions.assertTrue(info.get("serverTime").asText().endsWith("Z"));
		Assertions.assertTrue(info.get("sessionMaxIdleTime").asInt() > 0);
		Assertions.assertEquals(200, new V1Client(this.server, UiUser.LEVEL_READONLY).get("/system/info").status());
		Assertions.assertEquals(401, this.anonymous.get("/system/info").status());
		Assertions.assertEquals(1, this.admin.get("/system/info?fields=version").json().size());
	}

	@Test
	@DisplayName("Cluster members: an empty list without clustering, admin only")
	void clusterMembers() throws Exception {
		Answer answer = this.admin.get("/system/cluster/members");
		Assertions.assertEquals(200, answer.status(), answer.toString());
		Assertions.assertEquals(0, answer.json().get("count").asLong());
		Assertions.assertEquals(0, answer.json().get("results").size());
		Assertions.assertEquals(403, new V1Client(this.server, UiUser.LEVEL_EXECUTEREADWRITE).get("/system/cluster/members").status());
		Assertions.assertEquals(401, this.anonymous.get("/system/cluster/members").status());
	}

	@Test
	@DisplayName("Cluster mastership: reachable without authentication, 200 without clustering")
	void clusterMaster() throws Exception {
		Answer answer = this.anonymous.get("/system/cluster/master");
		Assertions.assertEquals(200, answer.status(), answer.toString());
		JsonNode master = answer.json();
		Assertions.assertEquals(Set.of("clusterEnabled", "master", "currentMasterId"), names(master));
		Assertions.assertFalse(master.get("clusterEnabled").asBoolean());
		Assertions.assertTrue(master.get("master").asBoolean());
		Assertions.assertTrue(master.get("currentMasterId").isNull());
	}

	@Test
	@DisplayName("Services: the state, health and statistics of the services of the server, admin only")
	void services() throws Exception {
		Answer answer = this.admin.get("/system/services");
		Assertions.assertEquals(200, answer.status(), answer.toString());
		JsonNode page = answer.json();
		Assertions.assertEquals(TestWiring.get().services().status().size(), page.get("count").asLong());
		// The tests start the database access, the task manager and the REST server only
		JsonNode database = page.get("results").get(0);
		Assertions.assertEquals(Set.of("display", "name", "state", "healthy", "error", "stats"), names(database));
		Assertions.assertEquals("database", database.get("name").asText());
		Assertions.assertEquals("Database", database.get("display").asText());
		Assertions.assertEquals("RUNNING", database.get("state").asText());
		Assertions.assertTrue(database.get("healthy").asBoolean());
		Assertions.assertTrue(database.get("error").isNull());
		Assertions.assertTrue(database.get("stats").isObject());
		JsonNode syslog = null;
		for (JsonNode service : page.get("results")) {
			if ("syslog-server".equals(service.get("name").asText())) {
				syslog = service;
			}
		}
		Assertions.assertNotNull(syslog);
		Assertions.assertEquals("NEW", syslog.get("state").asText());
		Assertions.assertFalse(syslog.get("healthy").asBoolean());

		Assertions.assertEquals(403, new V1Client(this.server, UiUser.LEVEL_EXECUTEREADWRITE).get("/system/services").status());
		Assertions.assertEquals(401, this.anonymous.get("/system/services").status());
	}

	@Test
	@DisplayName("Health: 503 DOWN without authentication when a service is not started, nothing else in the answer")
	void healthDown() throws Exception {
		// Some services of the server are not started by the tests
		Answer answer = this.anonymous.get("/health");
		Assertions.assertEquals(503, answer.status(), answer.toString());
		Assertions.assertEquals(Set.of("status"), names(answer.json()));
		Assertions.assertEquals("DOWN", answer.json().get("status").asText());
		Assertions.assertFalse(answer.body().contains(BuildInfo.VERSION));
		Assertions.assertFalse(answer.body().contains("Syslog"));
		// Credentials are not needed, and invalid ones don't matter
		Assertions.assertEquals(503, this.anonymous.get("/health", "X-Netshot-API-Token", "00000000000000000000000000000000").status());
		Assertions.assertEquals(503, this.admin.get("/health").status());
	}
}
