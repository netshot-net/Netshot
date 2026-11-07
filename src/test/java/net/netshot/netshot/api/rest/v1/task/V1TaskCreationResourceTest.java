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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

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
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunGroupDiagnosticsTask;
import net.netshot.netshot.task.type.ScanSubnetsTask;
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
import net.netshot.netshot.testing.api.V1Endpoints;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * The generic creation of tasks of the v1 API, over HTTP.
 */
@ApiTest
@V1Endpoints({
	"POST /tasks"
})
@DisplayName("v1 task creation")
@ResourceLock("DB")
public class V1TaskCreationResourceTest {

	private V1Client admin;
	private Domain domain;
	private Device router1;
	private Device router2;
	private StaticDeviceGroup core;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.admin = new V1Client(server, UiUser.LEVEL_ADMIN);
		TestDevices.deleteAll();
		TestWiring.get().transactions().run(dsl -> dsl.execute("truncate table task cascade"));
		this.domain = TestDevices.addDomain("Paris-" + System.nanoTime(), "Paris DC");
		this.router1 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(this.domain, null, 1));
		this.router2 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(this.domain, null, 2));
		this.core = TestDevices.addStaticGroup("Core-" + System.nanoTime(), this.router1, this.router2);
	}

	// Tasks which would do real work (scanning, purging) are scheduled in the future
	private static String later() {
		return "\"scheduleType\": \"AT\", \"scheduleReference\": \"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\"";
	}

	private Task accepted(Answer answer, String type) throws Exception {
		Assertions.assertEquals(202, answer.status(), answer.toString());
		JsonNode task = answer.json();
		Assertions.assertEquals("/api/v1/tasks/" + task.get("id").asLong(), answer.header("Location"));
		Assertions.assertEquals(type, task.get("type").asText());
		Task stored = TestTasks.find(task.get("id").asLong());
		Assertions.assertNotNull(stored);
		return stored;
	}

	@Test
	@DisplayName("A task on a device: the device is a reference (ID or object)")
	void deviceTask() throws Exception {
		Answer answer = this.admin.post("/tasks", """
			{"type": "TakeSnapshotTask", "device": %d, "parameters": {"dontRunDiagnostics": true}, "comments": "Generic", "priority": 7}"""
			.formatted(this.router1.id()));
		Task task = this.accepted(answer, "TakeSnapshotTask");
		Assertions.assertEquals(this.router1.id(), ((TakeSnapshotTask) task.spec()).deviceId());
		Assertions.assertTrue(((TakeSnapshotTask) task.spec()).dontRunDiagnostics());
		Assertions.assertFalse(((TakeSnapshotTask) task.spec()).dontCheckCompliance());
		Assertions.assertEquals(7, task.priority());
		Assertions.assertEquals("Generic", task.comments());
		Assertions.assertEquals(this.router1.id(), answer.json().get("device").get("id").asLong());
		Assertions.assertTrue(answer.json().get("parameters").get("dontRunDiagnostics").asBoolean());

		Task asObject = this.accepted(this.admin.post("/tasks", """
			{"type": "CheckComplianceTask", "device": {"id": %d, "name": "ignored"}}""".formatted(this.router2.id())), "CheckComplianceTask");
		Assertions.assertEquals(this.router2.id(), ((CheckComplianceTask) asObject.spec()).deviceId());
	}

	@Test
	@DisplayName("A task on a group, or on a list of devices")
	void groupTasks() throws Exception {
		Answer answer = this.admin.post("/tasks", """
			{"type": "TakeGroupSnapshotTask", "group": %d, "scheduleMode": "SEQUENTIAL", "stopOnFailure": true,
			"parameters": {"limitToOutofdateDeviceHours": 12, "dontCheckCompliance": true}}""".formatted(this.core.id()));
		Task task = this.accepted(answer, "TakeGroupSnapshotTask");
		TakeGroupSnapshotTask spec = (TakeGroupSnapshotTask) task.spec();
		Assertions.assertEquals(this.core.id(), spec.deviceGroupId());
		Assertions.assertEquals(12, spec.limitToOutofdateDeviceHours());
		Assertions.assertTrue(spec.dontCheckCompliance());
		Assertions.assertEquals(Task.ScheduleMode.SEQUENTIAL, task.scheduleMode());
		Assertions.assertTrue(task.stopOnFailure());
		Assertions.assertEquals(this.core.id(), answer.json().get("group").get("id").asLong());

		Task list = this.accepted(this.admin.post("/tasks", """
			{"type": "RunGroupDiagnosticsTask", "deviceList": [%d, {"id": %d}], "parameters": {"dontCheckCompliance": true}}"""
			.formatted(this.router2.id(), this.router1.id())), "RunGroupDiagnosticsTask");
		Assertions.assertTrue(((RunGroupDiagnosticsTask) list.spec()).dontCheckCompliance());
		Assertions.assertEquals(List.of(this.router2.id(), this.router1.id()), TestTasks.deviceListMembers(list.id()).stream().map(m -> m[1]).toList());

		this.accepted(this.admin.post("/tasks", "{\"type\": \"CheckGroupComplianceTask\", \"group\": " + this.core.id() + "}"),
			"CheckGroupComplianceTask");
		Assertions.assertInstanceOf(CheckGroupComplianceTask.class, TestTasks.all().get(TestTasks.all().size() - 1).spec());

		Task script = this.accepted(this.admin.post("/tasks", """
			{"type": "RunDeviceGroupScriptTask", "group": %d,
			"parameters": {"script": "function run(cli, device) { cli.command('show clock'); }", "deviceDriver": "CiscoIOS12", "runSnapshot": true}}"""
			.formatted(this.core.id())), "RunDeviceGroupScriptTask");
		Assertions.assertTrue(((RunDeviceGroupScriptTask) script.spec()).runSnapshot());
	}

	@Test
	@DisplayName("Global tasks: scanning subnets and purging the database")
	void globalTasks() throws Exception {
		Task scan = this.accepted(this.admin.post("/tasks", """
			{"type": "ScanSubnetsTask", %s, "parameters": {"domainId": %d, "subnets": ["10.0.0.0/30", "10.0.1.0/30"]}}"""
			.formatted(later(), this.domain.id())), "ScanSubnetsTask");
		Assertions.assertEquals(this.domain.id(), ((ScanSubnetsTask) scan.spec()).domainId());
		Assertions.assertEquals(2, ((ScanSubnetsTask) scan.spec()).subnets().size());
		Task purge = this.accepted(this.admin.post("/tasks", """
			{"type": "PurgeDatabaseTask", %s, "parameters": {"days": 30, "configDays": 60, "configKeepDays": 10, "removeOrphanFiles": true}}"""
			.formatted(later())), "PurgeDatabaseTask");
		PurgeDatabaseTask spec = (PurgeDatabaseTask) purge.spec();
		Assertions.assertEquals(30, spec.days());
		Assertions.assertEquals(60, spec.configDays());
		Assertions.assertTrue(spec.removeOrphanFiles());
		Assertions.assertEquals(400, this.admin.post("/tasks", "{\"type\": \"PurgeDatabaseTask\", \"parameters\": {\"days\": 1}}").status());
		Assertions.assertEquals(400, this.admin.post("/tasks", "{\"type\": \"ScanSubnetsTask\", \"parameters\": {\"domainId\": "
			+ this.domain.id() + ", \"subnets\": [\"nothing\"]}}").status());
	}

	@Test
	@DisplayName("Schedule: a date in the future, a date in the past")
	void schedule() throws Exception {
		Instant date = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
		Task task = this.accepted(this.admin.post("/tasks", "{\"type\": \"CheckComplianceTask\", \"device\": " + this.router1.id()
			+ ", \"scheduleType\": \"AT\", \"scheduleReference\": \"" + date + "\"}"), "CheckComplianceTask");
		Assertions.assertEquals(Task.ScheduleType.AT, task.scheduleType());
		Assertions.assertEquals(date, task.scheduleReference());
		Answer past = this.admin.post("/tasks", "{\"type\": \"CheckComplianceTask\", \"device\": " + this.router1.id()
			+ ", \"scheduleType\": \"AT\", \"scheduleReference\": \"" + Instant.now().minus(1, ChronoUnit.DAYS) + "\"}");
		Assertions.assertEquals(400, past.status(), past.toString());
		Assertions.assertEquals("NETSHOT_INVALID_TASK", past.json().get("code").asText());
	}

	@Test
	@DisplayName("Validation: type, target, parameters")
	void validation() throws Exception {
		Map<String, String> bodies = Map.ofEntries(
			Map.entry("no type", "{\"device\": " + this.router1.id() + "}"),
			Map.entry("unknown type", "{\"type\": \"NothingTask\", \"device\": " + this.router1.id() + "}"),
			Map.entry("not creatable", "{\"type\": \"DiscoverDeviceTypeTask\"}"),
			Map.entry("no device", "{\"type\": \"TakeSnapshotTask\"}"),
			Map.entry("unknown device", "{\"type\": \"TakeSnapshotTask\", \"device\": " + TestServer.UNKNOWN_ID + "}"),
			Map.entry("no group", "{\"type\": \"TakeGroupSnapshotTask\"}"),
			Map.entry("unknown group", "{\"type\": \"TakeGroupSnapshotTask\", \"group\": " + TestServer.UNKNOWN_ID + "}"),
			Map.entry("group and list", "{\"type\": \"TakeGroupSnapshotTask\", \"group\": " + this.core.id() + ", \"deviceList\": ["
				+ this.router1.id() + "]}"),
			Map.entry("unknown parameter", "{\"type\": \"TakeSnapshotTask\", \"device\": " + this.router1.id()
				+ ", \"parameters\": {\"automatic\": true}}"),
			Map.entry("wrong parameter type", "{\"type\": \"TakeSnapshotTask\", \"device\": " + this.router1.id()
				+ ", \"parameters\": {\"dontRunDiagnostics\": \"yes\"}}"),
			Map.entry("bad integer", "{\"type\": \"PurgeDatabaseTask\", \"parameters\": {\"days\": \"many\"}}"),
			Map.entry("bad priority", "{\"type\": \"CheckComplianceTask\", \"device\": " + this.router1.id() + ", \"priority\": 0}"),
			Map.entry("bad mode", "{\"type\": \"CheckComplianceTask\", \"device\": " + this.router1.id() + ", \"scheduleMode\": \"RANDOM\"}"),
			Map.entry("script without driver", "{\"type\": \"RunDeviceScriptTask\", \"device\": " + this.router1.id()
				+ ", \"parameters\": {\"script\": \"x\"}}"),
			Map.entry("unknown property", "{\"type\": \"CheckComplianceTask\", \"device\": " + this.router1.id() + ", \"colour\": 1}"));
		for (Map.Entry<String, String> body : bodies.entrySet()) {
			Answer answer = this.admin.post("/tasks", body.getValue());
			Assertions.assertEquals(400, answer.status(), body.getKey() + " -> " + answer);
		}
		Assertions.assertEquals(0, TestTasks.all().size());
	}

	@Test
	@DisplayName("Permissions: operator creates tasks, scripts, scans and purges need higher roles")
	void permissions(TestServer server) throws Exception {
		V1Client reader = new V1Client(server, UiUser.LEVEL_READONLY);
		V1Client operator = new V1Client(server, UiUser.LEVEL_OPERATOR);
		V1Client readWrite = new V1Client(server, UiUser.LEVEL_READWRITE);
		V1Client executor = new V1Client(server, UiUser.LEVEL_EXECUTEREADWRITE);
		String snapshot = "{\"type\": \"TakeSnapshotTask\", \"device\": " + this.router1.id() + "}";
		String script = "{\"type\": \"RunDeviceScriptTask\", \"device\": " + this.router1.id()
			+ ", \"parameters\": {\"script\": \"function run(cli, device) {}\", \"deviceDriver\": \"CiscoIOS12\"}}";
		String scan = "{\"type\": \"ScanSubnetsTask\", " + later() + ", \"parameters\": {\"domainId\": " + this.domain.id() + ", \"subnets\": [\"10.0.0.0/30\"]}}";
		String purge = "{\"type\": \"PurgeDatabaseTask\", " + later() + ", \"parameters\": {\"days\": 30}}";
		Assertions.assertEquals(403, reader.post("/tasks", snapshot).status());
		Assertions.assertEquals(202, operator.post("/tasks", snapshot).status());
		Assertions.assertEquals(403, operator.post("/tasks", script).status());
		Assertions.assertEquals(403, operator.post("/tasks", scan).status());
		Assertions.assertEquals(202, readWrite.post("/tasks", scan).status());
		Assertions.assertEquals(403, readWrite.post("/tasks", script).status());
		Assertions.assertEquals(202, executor.post("/tasks", script).status());
		Assertions.assertEquals(403, executor.post("/tasks", purge).status());
		Assertions.assertEquals(202, this.admin.post("/tasks", purge).status());
		Assertions.assertEquals(401, new V1Client(server, null).post("/tasks", snapshot).status());
	}
}
