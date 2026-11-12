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
package net.netshot.netshot.api.rest.v1.task;

import java.time.Instant;
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
import net.netshot.netshot.testing.api.v1.V1Client;
import net.netshot.netshot.testing.api.v1.V1Client.Answer;
import net.netshot.netshot.testing.api.v1.V1Endpoints;

/**
 * The statistics and status counts of the tasks in the v1 API, over HTTP.
 */
@ApiTest
@V1Endpoints({
	"GET /tasks/stats",
	"GET /tasks/status-counts"
})
@DisplayName("v1 task statistics")
@ResourceLock("DB")
public class V1TaskStatsResourceTest {

	private static final String DAY_START = "2030-01-01T00:00:00Z";
	private static final String DAY_END = "2030-01-02T00:00:00Z";

	private V1Client reader;
	private Task groupTask;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.reader = new V1Client(server, UiUser.LEVEL_READONLY);
		TestDevices.deleteAll();
		TestWiring.get().transactions().run(dsl -> dsl.execute("truncate table task cascade"));
		Domain domain = TestDevices.addDomain("Paris", "Paris DC");
		Device router = TestDevices.addFakeCiscoIos(domain, 1);
		StaticDeviceGroup core = TestDevices.addStaticGroup("Core", router);
		this.groupTask = TestTasks.add(Task.of(new TakeGroupSnapshotTask(core.id(), List.of(), 0, false, false), "Nightly", "Core", "alice"),
			Task.Status.RUNNING);
		this.executed(router, Task.Status.SUCCESS, "2030-01-01T06:00:00Z", null);
		this.executed(router, Task.Status.SUCCESS, "2030-01-01T07:00:00Z", null);
		this.executed(router, Task.Status.FAILURE, "2030-01-01T18:00:00Z", this.groupTask.id());
		TestTasks.add(TakeSnapshotTask.of(router, "Waiting", "bob", false, false, false), Task.Status.SCHEDULED);
	}

	private void executed(Device device, Task.Status status, String date, Long parentId) {
		Instant when = Instant.parse(date);
		Task.TaskBuilder builder = TakeSnapshotTask.of(device, "Done", "bob", false, false, false).toBuilder()
			.creationDate(when).changeDate(when).scheduleReference(when).executionDate(when);
		if (parentId != null) {
			builder.parentTaskId(parentId).childOrder(1);
		}
		TestTasks.add(builder.build(), status);
	}

	private JsonNode get(String path) throws Exception {
		Answer answer = this.reader.get(path);
		Assertions.assertEquals(200, answer.status(), path + " -> " + answer);
		return answer.json();
	}

	private void assertBadRequest(String path) throws Exception {
		Answer answer = this.reader.get(path);
		Assertions.assertEquals(400, answer.status(), path + " -> " + answer);
	}

	private static Set<String> keys(JsonNode object) {
		Set<String> keys = new TreeSet<>();
		object.fieldNames().forEachRemaining(keys::add);
		return keys;
	}

	private static List<String> rows(JsonNode stats, String... properties) {
		List<String> rows = new ArrayList<>();
		for (JsonNode row : stats.get("results")) {
			StringBuilder text = new StringBuilder();
			for (String property : properties) {
				text.append(row.get(property).asText()).append(':');
			}
			rows.add(text.append(row.get("count").asLong()).toString());
		}
		return rows;
	}

	@Test
	@DisplayName("Stats: counts by status and type, by time bucket of execution, with the filters of the task list")
	void stats() throws Exception {
		JsonNode byStatus = this.get("/tasks/stats?groupBy=status");
		Assertions.assertEquals(5, byStatus.get("count").asLong());
		Assertions.assertEquals(Set.of("SUCCESS:2", "FAILURE:1", "RUNNING:1", "SCHEDULED:1"), Set.copyOf(rows(byStatus, "status")));
		Assertions.assertEquals(Set.of("TakeSnapshotTask:4", "TakeGroupSnapshotTask:1"),
			Set.copyOf(rows(this.get("/tasks/stats?groupBy=type"), "type")));
		JsonNode byGroup = this.get("/tasks/stats?groupBy=group&type=TakeGroupSnapshotTask");
		Assertions.assertEquals(1, byGroup.get("results").size());
		Assertions.assertEquals("Core", byGroup.get("results").get(0).get("group").get("display").asText());
		Assertions.assertEquals(1, byGroup.get("results").get(0).get("count").asLong());

		// The executed tasks by day and by hour, on the day of the fixtures
		JsonNode byDay = this.get("/tasks/stats?interval=day&from=" + DAY_START + "&to=" + DAY_END + "&groupBy=status");
		Assertions.assertEquals("executionDate", byDay.get("on").asText());
		Assertions.assertEquals(Set.of(DAY_START.replace("Z", ".000Z") + ":SUCCESS:2", DAY_START.replace("Z", ".000Z") + ":FAILURE:1"),
			Set.copyOf(rows(byDay, "start", "status")));
		JsonNode byHour = this.get("/tasks/stats?interval=hour&from=" + DAY_START + "&to=" + DAY_END + "&status=FAILURE");
		Assertions.assertEquals(24, byHour.get("results").size());
		Assertions.assertEquals(1, byHour.get("results").get(18).get("count").asLong());
		Assertions.assertEquals(0, byHour.get("results").get(6).get("count").asLong());
		JsonNode children = this.get("/tasks/stats?parentTask=" + this.groupTask.id() + "&groupBy=status");
		Assertions.assertEquals(List.of("FAILURE:1"), rows(children, "status"));
	}

	@Test
	@DisplayName("Stats: invalid parameters are refused")
	void statsErrors() throws Exception {
		this.assertBadRequest("/tasks/stats?groupBy=comments");
		this.assertBadRequest("/tasks/stats?interval=day&on=author");
		this.assertBadRequest("/tasks/stats?interval=day&from=yesterday");
		this.assertBadRequest("/tasks/stats?interval=day&from=" + DAY_END + "&to=" + DAY_START);
		this.assertBadRequest("/tasks/stats?after=" + DAY_START);
		this.assertBadRequest("/tasks/stats?status=DONE");
	}

	@Test
	@DisplayName("Status counts: every status, with the number of threads, optionally for the children of a task")
	void statusCounts() throws Exception {
		JsonNode counts = this.get("/tasks/status-counts");
		Assertions.assertEquals(Set.of("countByStatus", "threadCount"), keys(counts));
		Assertions.assertTrue(counts.get("threadCount").asInt() >= 0);
		JsonNode byStatus = counts.get("countByStatus");
		for (Task.Status status : Task.Status.values()) {
			Assertions.assertTrue(byStatus.has(status.name()), status.name());
		}
		Assertions.assertEquals(2, byStatus.get("SUCCESS").asInt());
		Assertions.assertEquals(1, byStatus.get("FAILURE").asInt());
		Assertions.assertEquals(1, byStatus.get("SCHEDULED").asInt());
		Assertions.assertEquals(1, byStatus.get("RUNNING").asInt());
		Assertions.assertEquals(0, byStatus.get("CANCELLED").asInt());

		byStatus = this.get("/tasks/status-counts?parentTask=" + this.groupTask.id()).get("countByStatus");
		Assertions.assertEquals(1, byStatus.get("FAILURE").asInt());
		Assertions.assertEquals(0, byStatus.get("SUCCESS").asInt());
		Assertions.assertEquals(0, byStatus.get("RUNNING").asInt());

		this.assertBadRequest("/tasks/status-counts?parentTask=abc");
		this.assertBadRequest("/tasks/status-counts?parentTask=0");
		this.assertBadRequest("/tasks/status-counts?status=SUCCESS");
	}
}
