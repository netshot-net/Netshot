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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.platform.lifecycle.Service;
import net.netshot.netshot.platform.lifecycle.ServiceManager;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.v1.V1Client;
import net.netshot.netshot.testing.api.v1.V1Client.Answer;
import net.netshot.netshot.testing.api.v1.V1Endpoints;

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
public class V1SystemResourceTest {

	private TestServer server;
	private V1Client admin;
	private V1Client anonymous;

	/** A service with a health and statistics. */
	private static final class TestService implements Service {
		private final String name;
		private final boolean healthy;

		TestService(String name, boolean healthy) {
			this.name = name;
			this.healthy = healthy;
		}

		@Override
		public String name() {
			return this.name;
		}

		@Override
		public void start() {
			// Nothing to start
		}

		@Override
		public void stop() {
			// Nothing to stop
		}

		@Override
		public boolean isHealthy() {
			return this.healthy;
		}

		@Override
		public Map<String, Object> stats() {
			return Map.of("received", 12, "listening", true);
		}
	}

	@BeforeEach
	void setUp(TestServer testServer) {
		this.server = testServer;
		this.admin = new V1Client(testServer, UiUser.LEVEL_ADMIN);
		this.anonymous = new V1Client(testServer, null);
	}

	@AfterEach
	void detach() {
		TestWiring.get().serviceHealthService().attach(new ServiceManager(List.of()));
	}

	private static void attach(Service... services) throws Exception {
		ServiceManager manager = new ServiceManager(List.of(services));
		manager.start();
		TestWiring.get().serviceHealthService().attach(manager);
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
		Assertions.assertEquals(Netshot.VERSION, info.get("version").asText());
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
	@DisplayName("Services: state, health and statistics, admin only")
	void services() throws Exception {
		attach(new TestService("Alpha service", true), new TestService("Beta service", false));
		Answer answer = this.admin.get("/system/services");
		Assertions.assertEquals(200, answer.status(), answer.toString());
		JsonNode page = answer.json();
		Assertions.assertEquals(2, page.get("count").asLong());
		JsonNode alpha = page.get("results").get(0);
		Assertions.assertEquals(Set.of("display", "name", "state", "healthy", "error", "stats"), names(alpha));
		Assertions.assertEquals("Alpha service", alpha.get("name").asText());
		Assertions.assertEquals("RUNNING", alpha.get("state").asText());
		Assertions.assertTrue(alpha.get("healthy").asBoolean());
		Assertions.assertTrue(alpha.get("error").isNull());
		Assertions.assertEquals(12, alpha.get("stats").get("received").asInt());
		Assertions.assertFalse(page.get("results").get(1).get("healthy").asBoolean());

		Assertions.assertEquals(403, new V1Client(this.server, UiUser.LEVEL_EXECUTEREADWRITE).get("/system/services").status());
		Assertions.assertEquals(401, this.anonymous.get("/system/services").status());
	}

	@Test
	@DisplayName("Health: 200 UP without authentication when all the services are healthy, nothing else in the answer")
	void healthUp() throws Exception {
		attach(new TestService("Alpha service", true));
		Answer answer = this.anonymous.get("/health");
		Assertions.assertEquals(200, answer.status(), answer.toString());
		Assertions.assertEquals(Set.of("status"), names(answer.json()));
		Assertions.assertEquals("UP", answer.json().get("status").asText());
		Assertions.assertFalse(answer.body().contains(Netshot.VERSION));
		// Credentials are not needed, and invalid ones don't matter
		Assertions.assertEquals(200, this.anonymous.get("/health", "X-Netshot-API-Token", "00000000000000000000000000000000").status());
		Assertions.assertEquals(200, this.admin.get("/health").status());
	}

	@Test
	@DisplayName("Health: 503 DOWN when a service is unhealthy or not started")
	void healthDown() throws Exception {
		attach(new TestService("Alpha service", true), new TestService("Beta service", false));
		Answer unhealthy = this.anonymous.get("/health");
		Assertions.assertEquals(503, unhealthy.status(), unhealthy.toString());
		Assertions.assertEquals("DOWN", unhealthy.json().get("status").asText());
		Assertions.assertEquals(Set.of("status"), names(unhealthy.json()));
		Assertions.assertFalse(unhealthy.body().contains("Beta"));

		TestWiring.get().serviceHealthService().attach(new ServiceManager(List.of(new TestService("Not started", true))));
		Assertions.assertEquals(503, this.anonymous.get("/health").status());

		TestWiring.get().serviceHealthService().attach(new ServiceManager(List.of()));
		Assertions.assertEquals(200, this.anonymous.get("/health").status());
	}
}
