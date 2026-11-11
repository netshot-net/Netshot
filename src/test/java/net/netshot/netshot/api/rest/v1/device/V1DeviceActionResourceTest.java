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
package net.netshot.netshot.api.rest.v1.device;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
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
import net.netshot.netshot.userscript.DeviceJsScript;
import net.netshot.netshot.userscript.ScriptCreation;
import net.netshot.netshot.userscript.ScriptRepository;
import net.netshot.netshot.userscript.ScriptService;

/**
 * The actions on a device of the v1 API (snapshot, compliance check, diagnostics, script run), over HTTP: each creates a task.
 */
@ApiTest
@V1Endpoints({
	"POST /devices/{id}/snapshot",
	"POST /devices/{id}/compliance-check",
	"POST /devices/{id}/diagnostics-run",
	"POST /devices/{id}/script-runs",
	"GET /tasks/{id}"
})
@DisplayName("v1 device actions")
@ResourceLock("DB")
class V1DeviceActionResourceTest {

	private static final String SCRIPT = "function run(cli, device) {\n  cli.command('show clock');\n}";

	private V1Client admin;
	private Device router1;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.admin = new V1Client(server, UiUser.LEVEL_ADMIN);
		TestDevices.deleteAll();
		TestWiring.get().transactions().run(dsl -> dsl.execute("truncate table task cascade"));
		Domain domain = TestDevices.addDomain("Paris-" + System.nanoTime(), "Paris DC");
		this.router1 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1));
	}

	private static String quote(String text) throws Exception {
		return new ObjectMapper().writeValueAsString(text);
	}

	private String path(String action) {
		return "/devices/" + this.router1.id() + "/" + action;
	}

	// Checks the answer is a 202 pointing at a task, which the task resource gives, and returns the stored task
	private Task accepted(Answer answer, String type) throws Exception {
		Assertions.assertEquals(202, answer.status(), answer.toString());
		JsonNode task = answer.json();
		long id = task.get("id").asLong();
		Assertions.assertEquals("/api/v1/tasks/" + id, answer.header("Location"));
		Assertions.assertEquals(type, task.get("type").asText());
		Assertions.assertEquals(this.router1.id(), task.get("device").get("id").asLong());
		Assertions.assertEquals("/api/v1/devices/" + this.router1.id(), task.get("device").get("url").asText());
		JsonNode fetched = this.admin.get("/tasks/" + id).json();
		Assertions.assertEquals(type, fetched.get("type").asText());
		Task stored = TestTasks.find(id);
		Assertions.assertNotNull(stored);
		return stored;
	}

	@Test
	@DisplayName("Snapshot: no body means defaults, options are kept in the task")
	void snapshot() throws Exception {
		Answer defaults = this.admin.post(this.path("snapshot"), null);
		Task task = this.accepted(defaults, "TakeSnapshotTask");
		Assertions.assertEquals(5, task.priority());
		Assertions.assertEquals(Task.ScheduleType.ASAP, task.scheduleType());
		TakeSnapshotTask spec = (TakeSnapshotTask) task.spec();
		Assertions.assertFalse(spec.dontRunDiagnostics());
		Assertions.assertFalse(spec.dontCheckCompliance());
		Assertions.assertFalse(spec.automatic());
		JsonNode parameters = defaults.json().get("parameters");
		Assertions.assertFalse(parameters.get("dontRunDiagnostics").asBoolean());

		Answer options = this.admin.post(this.path("snapshot"), """
			{"dontRunDiagnostics": true, "dontCheckCompliance": true, "comments": "Before upgrade", "priority": 2, "debugEnabled": true}""");
		Task custom = this.accepted(options, "TakeSnapshotTask");
		TakeSnapshotTask customSpec = (TakeSnapshotTask) custom.spec();
		Assertions.assertTrue(customSpec.dontRunDiagnostics());
		Assertions.assertTrue(customSpec.dontCheckCompliance());
		Assertions.assertEquals("Before upgrade", custom.comments());
		Assertions.assertEquals(2, custom.priority());
		Assertions.assertTrue(custom.debugEnabled());
		Assertions.assertEquals(Task.ScheduleType.ASAP, custom.scheduleType());
	}

	@Test
	@DisplayName("Snapshot: a schedule, and its validation")
	void schedule() throws Exception {
		Instant date = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
		Answer at = this.admin.post(this.path("snapshot"), "{\"scheduleType\": \"AT\", \"scheduleReference\": \"" + date + "\"}");
		Task task = this.accepted(at, "TakeSnapshotTask");
		Assertions.assertEquals(Task.ScheduleType.AT, task.scheduleType());
		Assertions.assertEquals(date, task.scheduleReference());
		Assertions.assertEquals(Task.Status.SCHEDULED, task.status());
		Answer daily = this.admin.post(this.path("snapshot"), "{\"scheduleType\": \"DAILY\", \"scheduleFactor\": 3, \"scheduleReference\": \""
			+ date + "\"}");
		Task repeating = this.accepted(daily, "TakeSnapshotTask");
		Assertions.assertEquals(Task.ScheduleType.DAILY, repeating.scheduleType());
		Assertions.assertEquals(3, repeating.scheduleFactor());

		Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
		Answer inThePast = this.admin.post(this.path("snapshot"), "{\"scheduleType\": \"AT\", \"scheduleReference\": \"" + past + "\"}");
		Assertions.assertEquals(400, inThePast.status(), inThePast.toString());
		Assertions.assertEquals("NETSHOT_INVALID_TASK", inThePast.json().get("code").asText());
		Assertions.assertEquals(400, this.admin.post(this.path("snapshot"), "{\"scheduleType\": \"AT\"}").status());
		Assertions.assertEquals(400, this.admin.post(this.path("snapshot"), "{\"scheduleType\": \"DAILY\", \"scheduleFactor\": 0}").status());
		Assertions.assertEquals(400, this.admin.post(this.path("snapshot"), "{\"scheduleType\": \"SOMETIMES\"}").status());
		Assertions.assertEquals(400, this.admin.post(this.path("snapshot"), "{\"priority\": 12}").status());
		Assertions.assertEquals(400, this.admin.post(this.path("snapshot"), "{\"colour\": \"red\"}").status());
		Assertions.assertEquals(2, TestTasks.all().size());
	}

	@Test
	@DisplayName("Compliance check and diagnostics run create their tasks")
	void complianceAndDiagnostics() throws Exception {
		Task compliance = this.accepted(this.admin.post(this.path("compliance-check"), "{\"comments\": \"Check\"}"), "CheckComplianceTask");
		Assertions.assertInstanceOf(CheckComplianceTask.class, compliance.spec());
		Assertions.assertEquals("Check", compliance.comments());
		this.accepted(this.admin.post(this.path("compliance-check"), null), "CheckComplianceTask");

		Task diagnostics = this.accepted(this.admin.post(this.path("diagnostics-run"), "{\"dontCheckCompliance\": true}"), "RunDiagnosticsTask");
		Assertions.assertTrue(((RunDiagnosticsTask) diagnostics.spec()).dontCheckCompliance());
		Task defaults = this.accepted(this.admin.post(this.path("diagnostics-run"), null), "RunDiagnosticsTask");
		Assertions.assertFalse(((RunDiagnosticsTask) defaults.spec()).dontCheckCompliance());
	}

	@Test
	@DisplayName("Script run: a script text with its driver, or a saved script")
	void scriptRun() throws Exception {
		Answer text = this.admin.post(this.path("script-runs"), """
			{"script": %s, "deviceDriver": "CiscoIOS12", "runSnapshot": true, "checkCompliance": true, "comments": "Run"}"""
			.formatted(quote(SCRIPT)));
		Task task = this.accepted(text, "RunDeviceScriptTask");
		RunDeviceScriptTask spec = (RunDeviceScriptTask) task.spec();
		Assertions.assertEquals(SCRIPT, spec.script());
		Assertions.assertEquals("CiscoIOS12", spec.deviceDriver());
		Assertions.assertTrue(spec.runSnapshot());
		Assertions.assertFalse(spec.runDiagnostics());
		Assertions.assertTrue(spec.checkCompliance());
		Assertions.assertEquals("CiscoIOS12", text.json().get("parameters").get("deviceDriver").asText());

		ScriptService scripts = new ScriptService(TestWiring.get().transactions(), new ScriptRepository(), TestDrivers.registry());
		DeviceJsScript saved = scripts.create(new ScriptCreation("Clock-" + System.nanoTime(), "CiscoIOS12", SCRIPT, "tester", "Folder"));
		Task fromSaved = this.accepted(this.admin.post(this.path("script-runs"), "{\"savedScript\": " + saved.id() + "}"), "RunDeviceScriptTask");
		Assertions.assertEquals(SCRIPT, ((RunDeviceScriptTask) fromSaved.spec()).script());
		Assertions.assertEquals("CiscoIOS12", ((RunDeviceScriptTask) fromSaved.spec()).deviceDriver());
	}

	@Test
	@DisplayName("Script run: the user inputs are checked against the declarations of the script")
	void scriptRunInputs() throws Exception {
		String script = quote("const Input = {\n  location: { label: \"Location\", regExp: /[A-Z]{2} - .+/ },\n  contact: { optional: true },\n};\n"
			+ "function run(cli, device) {\n  cli.command('show clock');\n}");
		String base = "{\"script\": " + script + ", \"deviceDriver\": \"CiscoIOS12\"%s}";
		Assertions.assertEquals(400, this.admin.post(this.path("script-runs"), base.formatted("")).status());
		Assertions.assertEquals(400, this.admin.post(this.path("script-runs"),
			base.formatted(", \"userInputValues\": {\"location\": \"nowhere\"}")).status());
		Answer ok = this.admin.post(this.path("script-runs"), base.formatted(", \"userInputValues\": {\"location\": \"FR - Paris\"}"));
		Task task = this.accepted(ok, "RunDeviceScriptTask");
		Assertions.assertEquals(Map.of("location", "FR - Paris"), ((RunDeviceScriptTask) task.spec()).userInputValues());
		Assertions.assertEquals("FR - Paris", ok.json().get("parameters").get("userInputValues").get("location").asText());
	}

	@Test
	@DisplayName("Script run: validation errors")
	void scriptRunValidation() throws Exception {
		String quoted = quote(SCRIPT);
		Assertions.assertEquals(400, this.admin.post(this.path("script-runs"), null).status());
		Assertions.assertEquals(400, this.admin.post(this.path("script-runs"), "{}").status());
		Assertions.assertEquals(400, this.admin.post(this.path("script-runs"), "{\"script\": " + quoted + "}").status());
		Assertions.assertEquals(400, this.admin.post(this.path("script-runs"), "{\"script\": " + quoted + ", \"deviceDriver\": \"Nothing\"}").status());
		Assertions.assertEquals(400, this.admin.post(this.path("script-runs"), "{\"script\": \"function run( {\", \"deviceDriver\": \"CiscoIOS12\"}")
			.status());
		Assertions.assertEquals(400, this.admin.post(this.path("script-runs"),
			"{\"savedScript\": 1, \"script\": " + quoted + ", \"deviceDriver\": \"CiscoIOS12\"}").status());
		Answer unknownSaved = this.admin.post(this.path("script-runs"), "{\"savedScript\": " + TestServer.UNKNOWN_ID + "}");
		Assertions.assertEquals(400, unknownSaved.status(), unknownSaved.toString());
		Assertions.assertEquals(0, TestTasks.all().size());
	}

	@Test
	@DisplayName("Unknown device: 404 for every action")
	void unknownDevice() throws Exception {
		for (String action : new String[] {"snapshot", "compliance-check", "diagnostics-run"}) {
			Answer answer = this.admin.post("/devices/" + TestServer.UNKNOWN_ID + "/" + action, null);
			Assertions.assertEquals(404, answer.status(), action + " " + answer);
			Assertions.assertEquals("NETSHOT_DEVICE_NOT_FOUND", answer.json().get("code").asText());
		}
		Assertions.assertEquals(404, this.admin.post("/devices/" + TestServer.UNKNOWN_ID + "/script-runs",
			"{\"script\": \"x\", \"deviceDriver\": \"CiscoIOS12\"}").status());
	}

	@Test
	@DisplayName("Permissions: the operator level runs the actions, scripts need execute-read-write")
	void permissions(TestServer server) throws Exception {
		V1Client reader = new V1Client(server, UiUser.LEVEL_READONLY);
		V1Client operator = new V1Client(server, UiUser.LEVEL_OPERATOR);
		V1Client readWrite = new V1Client(server, UiUser.LEVEL_READWRITE);
		V1Client executor = new V1Client(server, UiUser.LEVEL_EXECUTEREADWRITE);
		for (String action : new String[] {"snapshot", "compliance-check", "diagnostics-run"}) {
			Assertions.assertEquals(403, reader.post(this.path(action), null).status(), action);
			Assertions.assertEquals(202, operator.post(this.path(action), null).status(), action);
		}
		String script = "{\"script\": " + quote(SCRIPT) + ", \"deviceDriver\": \"CiscoIOS12\"}";
		Assertions.assertEquals(403, operator.post(this.path("script-runs"), script).status());
		Assertions.assertEquals(403, readWrite.post(this.path("script-runs"), script).status());
		Assertions.assertEquals(202, executor.post(this.path("script-runs"), script).status());
		Assertions.assertEquals(401, new V1Client(server, null).post(this.path("snapshot"), null).status());
	}
}
