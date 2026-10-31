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
package net.netshot.netshot.api.rest.v1.task;

import java.util.ArrayList;
import java.util.List;
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
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * The tasks of the v1 API, over HTTP.
 */
@ApiTest
@DisplayName("v1 tasks")
@ResourceLock("DB")
public class V1TaskResourceTest {

	private V1Client admin;
	private V1Client reader;
	private Device router1;
	private StaticDeviceGroup core;
	private Task groupTask;
	private Task childTask;
	private Task deviceTask;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.admin = new V1Client(server, UiUser.LEVEL_ADMIN);
		this.reader = new V1Client(server, UiUser.LEVEL_READONLY);
		TestDevices.deleteAll();
		TestWiring.get().transactions().run(dsl -> dsl.execute("truncate table task cascade"));
		Domain domain = TestDevices.addDomain("Paris", "Paris DC");
		this.router1 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1));
		Device router2 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 2));
		this.core = TestDevices.addStaticGroup("Core", this.router1, router2);
		this.groupTask = TestTasks.add(Task.of(new TakeGroupSnapshotTask(this.core.id(), List.of(), 0, false, false),
			"Nightly", "Core", "alice"), Task.Status.SUCCESS);
		this.childTask = TestTasks.add(TakeSnapshotTask.of(this.router1, "Child", "alice", false, true, false).toBuilder()
			.parentTaskId(this.groupTask.id()).childOrder(1).build(), Task.Status.FAILURE);
		this.deviceTask = TestTasks.add(TakeSnapshotTask.of(this.router1, "Manual", "bob", false, false, true), Task.Status.SUCCESS);
		TestTasks.setDebugLog(this.deviceTask.id(), "debug text");
	}

	private static List<Long> ids(JsonNode page) {
		List<Long> ids = new ArrayList<>();
		page.get("results").forEach(item -> ids.add(item.get("id").asLong()));
		return ids;
	}

	private static Set<String> keys(JsonNode object) {
		Set<String> keys = new TreeSet<>();
		object.fieldNames().forEachRemaining(keys::add);
		return keys;
	}

	private List<Long> list(String query) throws Exception {
		Answer answer = this.reader.get("/tasks" + query);
		Assertions.assertEquals(200, answer.status(), query + " -> " + answer);
		return ids(answer.json());
	}

	@Test
	@DisplayName("List: latest first, brief view, filters")
	void list() throws Exception {
		JsonNode page = this.reader.get("/tasks").json();
		Assertions.assertEquals(3, page.get("count").asInt());
		Assertions.assertEquals(List.of(this.deviceTask.id(), this.childTask.id(), this.groupTask.id()), ids(page));
		JsonNode first = page.get("results").get(0);
		Assertions.assertEquals(Set.of("id", "url", "display", "type", "status"), keys(first));
		Assertions.assertEquals("TakeSnapshotTask", first.get("type").asText());
		Assertions.assertTrue(first.get("display").asText().startsWith("Device snapshot - "), first.toString());

		Assertions.assertEquals(List.of(this.childTask.id()), this.list("?status=FAILURE"));
		Assertions.assertEquals(List.of(this.groupTask.id()), this.list("?type=TakeGroupSnapshotTask"));
		Assertions.assertEquals(List.of(this.deviceTask.id(), this.childTask.id()), this.list("?device=" + this.router1.id()));
		Assertions.assertEquals(List.of(this.groupTask.id()), this.list("?group=" + this.core.id()));
		Assertions.assertEquals(List.of(this.childTask.id()), this.list("?parentTask=" + this.groupTask.id()));
		Assertions.assertEquals(List.of(this.childTask.id(), this.groupTask.id()), this.list("?author=alice"));
		Assertions.assertEquals(List.of(this.deviceTask.id()), this.list("?q=manual"));
		Assertions.assertEquals(400, this.reader.get("/tasks?type=NothingTask").status());
		Assertions.assertEquals(400, this.reader.get("/tasks?status=DONE").status());
	}

	@Test
	@DisplayName("References: device, group and parent task, brief unless expanded")
	void references() throws Exception {
		JsonNode child = this.reader.get("/tasks?parentTask=%d&fields=device,group,parentTask".formatted(this.groupTask.id()))
			.json().get("results").get(0);
		Assertions.assertEquals(Set.of("id", "url", "display", "name", "mgmtAddress", "status"), keys(child.get("device")));
		Assertions.assertEquals("router00001", child.get("device").get("name").asText());
		Assertions.assertTrue(child.get("group").isNull());
		Assertions.assertEquals(Set.of("id", "url", "display", "type", "status"), keys(child.get("parentTask")));
		Assertions.assertEquals("TakeGroupSnapshotTask", child.get("parentTask").get("type").asText());

		JsonNode expanded = this.reader.get("/tasks/%d?expand=device&fields=device".formatted(this.childTask.id())).json();
		Assertions.assertEquals("Unknown IOS device", expanded.get("device").get("family").asText(), expanded.toString());

		JsonNode group = this.reader.get("/tasks/" + this.groupTask.id()).json();
		Assertions.assertEquals("Core", group.get("group").get("name").asText());
		Assertions.assertEquals(Set.of("id", "url", "display", "name", "folder", "type"), keys(group.get("group")));
	}

	@Test
	@DisplayName("Get: full view, parameters, ETag; full list view; debug log; cancel")
	void get() throws Exception {
		Answer answer = this.reader.get("/tasks/" + this.deviceTask.id());
		Assertions.assertEquals(200, answer.status(), answer.toString());
		Assertions.assertEquals("\"" + TestTasks.find(this.deviceTask.id()).version() + "\"", answer.header("ETag"));
		JsonNode task = answer.json();
		Assertions.assertEquals("SUCCESS", task.get("status").asText());
		Assertions.assertEquals("Device snapshot", task.get("description").asText());
		Assertions.assertEquals("bob", task.get("author").asText());
		Assertions.assertEquals("ASAP", task.get("scheduleType").asText());
		Assertions.assertFalse(task.get("parameters").get("dontRunDiagnostics").asBoolean());
		Assertions.assertTrue(task.get("parameters").get("dontCheckCompliance").asBoolean());
		Assertions.assertTrue(task.has("log"));

		JsonNode full = this.reader.get("/tasks?view=full&type=TakeGroupSnapshotTask").json().get("results").get(0);
		Assertions.assertEquals(0, full.get("parameters").get("limitToOutofdateDeviceHours").asInt());

		Assertions.assertEquals(404, this.reader.get("/tasks/" + TestServer.UNKNOWN_ID).status());
		Assertions.assertEquals(403, this.reader.get("/tasks/%d/debug-log".formatted(this.deviceTask.id()), "Accept", "text/plain").status());
		Answer log = this.admin.get("/tasks/%d/debug-log".formatted(this.deviceTask.id()), "Accept", "text/plain");
		Assertions.assertEquals(200, log.status(), log.toString());
		Assertions.assertEquals("debug text", log.body());

		Answer cancel = this.admin.post("/tasks/%d/cancel".formatted(this.deviceTask.id()), null);
		Assertions.assertEquals(409, cancel.status(), cancel.toString());
		Assertions.assertEquals("NETSHOT_TASK_NOT_CANCELLABLE", cancel.json().get("code").asText());
		Assertions.assertEquals(404, this.admin.post("/tasks/%d/cancel".formatted(TestServer.UNKNOWN_ID), null).status());
		Assertions.assertEquals(403, this.reader.post("/tasks/%d/cancel".formatted(this.deviceTask.id()), null).status());
	}
}
