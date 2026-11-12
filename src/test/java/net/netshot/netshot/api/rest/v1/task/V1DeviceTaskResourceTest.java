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

import java.util.ArrayList;
import java.util.List;

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
import net.netshot.netshot.task.Task;
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
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * The tasks of a device of the v1 API, over HTTP.
 */
@ApiTest
@V1Endpoints({
	"GET /devices/{id}/tasks"
})
@DisplayName("v1 device tasks")
@ResourceLock("DB")
public class V1DeviceTaskResourceTest {

	private V1Client reader;
	private Device router1;
	private Device router2;
	private Task failed;
	private Task succeeded;
	private Task other;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.reader = new V1Client(server, UiUser.LEVEL_READONLY);
		TestDevices.deleteAll();
		TestWiring.get().transactions().run(dsl -> dsl.execute("truncate table task cascade"));
		Domain domain = TestDevices.addDomain("Paris-" + System.nanoTime(), "Paris DC");
		this.router1 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1));
		this.router2 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 2));
		this.failed = TestTasks.add(TakeSnapshotTask.of(this.router1, "First", "alice", false, true, false), Task.Status.FAILURE);
		this.succeeded = TestTasks.add(TakeSnapshotTask.of(this.router1, "Second", "bob", false, false, true), Task.Status.SUCCESS);
		this.other = TestTasks.add(TakeSnapshotTask.of(this.router2, "Other", "bob", false, false, true), Task.Status.SUCCESS);
	}

	private List<Long> ids(Device device, String query) throws Exception {
		Answer answer = this.reader.get("/devices/%d/tasks%s".formatted(device.id(), query));
		Assertions.assertEquals(200, answer.status(), query + " -> " + answer);
		List<Long> ids = new ArrayList<>();
		answer.json().get("results").forEach(item -> ids.add(item.get("id").asLong()));
		return ids;
	}

	@Test
	@DisplayName("The tasks of a device are the task list filtered on it")
	void deviceTasks() throws Exception {
		Assertions.assertEquals(List.of(this.succeeded.id(), this.failed.id()), this.ids(this.router1, ""));
		Assertions.assertEquals(List.of(this.other.id()), this.ids(this.router2, ""));
		Assertions.assertEquals(List.of(this.failed.id()), this.ids(this.router1, "?status=FAILURE"));
		Assertions.assertEquals(List.of(this.failed.id(), this.succeeded.id()), this.ids(this.router1, "?sort=id"));
		Assertions.assertEquals(List.of(this.succeeded.id()), this.ids(this.router1, "?author=bob"));
		Assertions.assertEquals(List.of(), this.ids(this.router1, "?author=carol"));
		JsonNode page = this.reader.get("/devices/%d/tasks?limit=1&count=true".formatted(this.router1.id())).json();
		Assertions.assertEquals(2, page.get("count").asInt());
		Assertions.assertFalse(page.get("next").isNull());
		JsonNode first = this.reader.get("/devices/%d/tasks?fields=device,status".formatted(this.router1.id())).json().get("results").get(0);
		Assertions.assertEquals(this.router1.id(), first.get("device").get("id").asLong());
	}

	@Test
	@DisplayName("Unknown device, invalid filters")
	void errors() throws Exception {
		Assertions.assertEquals(404, this.reader.get("/devices/%d/tasks".formatted(TestServer.UNKNOWN_ID)).status());
		Assertions.assertEquals(400, this.reader.get("/devices/%d/tasks?status=DONE".formatted(this.router1.id())).status());
	}
}
