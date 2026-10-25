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
package net.netshot.netshot.api.rest.v0.task;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskCreation;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.GroupBasedTask;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.RunGroupDiagnosticsTask;
import net.netshot.netshot.task.type.ScanSubnetsTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.task.type.TaskSpec;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.contract.ApiContractGolden;
import net.netshot.netshot.testing.contract.DtoContract;

/**
 * Checks that the task DTOs and their mapper are a faithful replacement of the {@code Task}
 * entities the REST API used to serialize: for every type of task, the representation of an
 * in-memory task is the same, in JSON and XML, as the one of the entity with the same view.
 * The creation request is read like it always was.
 */
@ScriptEngineTest
class TaskDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final XmlMapper XML = XmlMapper.builder().build();

	@RegisterExtension
	private final DtoContract contract = new DtoContract(TaskDtoTest.class);

	private static Domain domain() throws Exception {
		return Domain.builder().id(3).name("Paris DC").description("Description").server4Address(new Network4Address("10.255.0.1")).build();
	}

	private static Device device(long id) throws Exception {
		return Device.create("CiscoIOS12", "10.1.1." + id, domain(), "seed").toBuilder().id(id).name("router-" + id).build();
	}

	private static DeviceGroup group(long id) {
		return StaticDeviceGroup.builder().id(id).name("Group " + id).folder("").build();
	}

	/**
	 * Gives every property shared by the tasks a distinctive value.
	 * @param task the task to fill
	 * @param id the ID to give
	 * @param status the status to give
	 * @return the filled task
	 */
	private static Task filled(Task task, long id, Task.Status status) {
		return task.toBuilder()
			.id(id)
			.author("author-" + id)
			.changeDate(Instant.ofEpochMilli(1_600_000_000_000L + id))
			.creationDate(Instant.ofEpochMilli(1_500_000_000_000L + id))
			.executionDate(status == Task.Status.NEW ? null : Instant.ofEpochMilli(1_550_000_000_000L + id))
			.comments("Comments of " + id)
			.debugEnabled(id % 2 == 0)
			.status(status)
			.scheduleType(Task.ScheduleType.WEEKLY)
			.scheduleReference(Instant.ofEpochMilli(1_700_000_000_000L))
			.scheduleFactor(3)
			.priority(7)
			.runnerId("runner-" + id)
			.parentTaskId(id + 100)
			.childOrder((int) id)
			.scheduleMode(Task.ScheduleMode.SEQUENTIAL)
			.stopOnFailure(true)
			.cancelRequested(true)
			.log("line 1\nline 2 <&>")
			.build();
	}

	// The script parameters given to the script tasks
	private static RunDeviceScriptTask scriptSpec(long deviceId, long id) {
		return new RunDeviceScriptTask(deviceId, "script of " + id, "CiscoIOS12", Map.of("input", "value"), true, true, true);
	}

	private List<Task> allTasks() throws Exception {
		List<Task> tasks = new ArrayList<>();
		tasks.add(filled(TakeSnapshotTask.of(device(1), "snap", "tester", false, true, true), 1, Task.Status.SUCCESS));
		tasks.add(filled(CheckComplianceTask.of(device(2), "check", "tester"), 2, Task.Status.FAILURE));
		tasks.add(filled(RunDiagnosticsTask.of(device(3), "diag", "tester", true), 3, Task.Status.RUNNING));
		tasks.add(filled(RunDeviceScriptTask.of(device(4), scriptSpec(4, 4), "script", "tester"), 4, Task.Status.SCHEDULED));
		tasks.add(filled(Task.of(DiscoverDeviceTypeTask.builder().deviceAddress("10.9.9.9").domainId(3L)
			.discoveredDeviceType("CiscoIOS12").discoverResultDeviceId(5L).snapshotTaskId(55L).build(), "discover", "10.9.9.9", "tester"),
			5, Task.Status.SUCCESS));
		tasks.add(filled(DiscoverDeviceTypeTask.of("10.9.9.10", 3L, Set.of(), "discover without device", "tester"), 6, Task.Status.NEW));
		tasks.add(filled(GroupBasedTask.of(new TakeGroupSnapshotTask(10L, null, 12, false, true), group(10).name(), "group snap", "tester"),
			7, Task.Status.WAITING));
		tasks.add(filled(GroupBasedTask.of(new CheckGroupComplianceTask(11L, null), group(11).name(), "group check", "tester"),
			8, Task.Status.DELAYED));
		tasks.add(filled(GroupBasedTask.of(new CheckGroupSoftwareTask(12L, null), group(12).name(), "group software", "tester"),
			9, Task.Status.CANCELLED));
		tasks.add(filled(GroupBasedTask.of(new RunGroupDiagnosticsTask(13L, null, false), group(13).name(), "group diag", "tester"),
			10, Task.Status.SUCCESS));
		RunDeviceScriptTask script = scriptSpec(0, 11);
		tasks.add(filled(Task.of(new RunDeviceGroupScriptTask(14L, null, script.script(), script.deviceDriver(), script.userInputValues(),
			true, true, true), "", "Group 14", ""), 11, Task.Status.RUNNING));
		tasks.add(filled(Task.of(PurgeDatabaseTask.builder().deviceGroupId(15L).days(30).configDays(60).configSize(10).configKeepDays(5)
			.moduleDays(20).removeOrphanFiles(true).build(), "purge", "Global", "tester"), 12, Task.Status.SCHEDULED));
		tasks.add(filled(Task.of(PurgeDatabaseTask.builder().days(90).build(), "purge all", "Global", "tester"), 13, Task.Status.NEW));
		tasks.add(filled(Task.of(PurgeDatabaseTask.builder().deviceIds(List.of(6L)).days(90).build(), "purge devices", "Global", "tester"),
			14, Task.Status.NEW));
		tasks.add(filled(ScanSubnetsTask.of(Set.of(new Network4Address("10.8.0.0", 24)), 3L, "scan", "{10.8.0.0/24}", "tester"),
			15, Task.Status.SUCCESS));
		// The tasks which target nothing
		tasks.add(filled(Task.of(new TakeGroupSnapshotTask(null, null, -1, false, false), "", "None", ""), 16, Task.Status.NEW));
		tasks.add(filled(Task.of(new CheckGroupComplianceTask(null, null), "", "None", ""), 17, Task.Status.NEW));
		RunDeviceScriptTask script18 = scriptSpec(0, 18);
		tasks.add(filled(Task.of(new RunDeviceGroupScriptTask(null, null, script18.script(), script18.deviceDriver(),
			script18.userInputValues(), true, true, true), "", "None", ""), 18, Task.Status.NEW));
		return tasks;
	}

	private static byte[] json(Object value) throws Exception {
		return JSON.writer().writeValueAsBytes(value);
	}

	private static byte[] xml(Object value) throws Exception {
		return XML.writer().writeValueAsBytes(value);
	}

	private void assertRepresentation(TaskDto dto, String message) throws Exception {
		this.contract.check(message, ApiContractGolden.Format.JSON, json(dto));
		this.contract.check(message, ApiContractGolden.Format.XML, xml(dto));
	}

	@Test
	@DisplayName("Every type of task has the same JSON and XML representation as the entity")
	void everyTypeRepresentation() throws Exception {
		Set<Class<?>> covered = new java.util.HashSet<>();
		for (Task task : this.allTasks()) {
			TaskDto dto = TaskMapper.toDto(TestDrivers.registry(), task);
			this.assertRepresentation(dto, task.typeName() + " " + task.id());
			covered.add(task.spec().getClass());
			// Make sure the check is meaningful
			String json = new String(json(dto));
			Assertions.assertTrue(json.contains("\"type\":\"" + task.typeName() + "\""), json);
			Assertions.assertTrue(json.contains("\"log\":\"line 1\\nline 2 <&>\""), json);
			Assertions.assertTrue(new String(xml(dto)).contains("<" + task.typeName() + " type=\""), new String(xml(dto)));
		}
		Assertions.assertEquals(Set.of(TaskSpec.class.getPermittedSubclasses()), covered, "every type of task is tested");
	}

	@Test
	@DisplayName("A list of tasks keeps the type of each one")
	void listRepresentation() throws Exception {
		List<Task> tasks = this.allTasks();
		List<TaskDto> dtos = tasks.stream().map(task -> TaskMapper.toDto(TestDrivers.registry(), task)).toList();
		this.contract.check("listRepresentation", ApiContractGolden.Format.JSON, JSON.writer()
			.forType(new TypeReference<List<TaskDto>>() { }).writeValueAsBytes(dtos));
	}

	@Test
	@DisplayName("The device and group IDs are the ones of the target, 0 for none")
	void targets() throws Exception {
		Assertions.assertEquals(1L, ((TaskDto.TakeSnapshot) TaskMapper.toDto(TestDrivers.registry(), this.allTasks().get(0))).deviceId());
		Assertions.assertEquals(0L, ((TaskDto.RunDeviceGroupScript) TaskMapper.toDto(TestDrivers.registry(), this.allTasks().get(17))).deviceGroupId());
		Assertions.assertEquals(0L, ((TaskDto.TakeGroupSnapshot) TaskMapper.toDto(TestDrivers.registry(), this.allTasks().get(15))).deviceGroupId());
		Assertions.assertEquals(15L, ((TaskDto.PurgeDatabase) TaskMapper.toDto(TestDrivers.registry(), this.allTasks().get(11))).deviceGroupId());
	}

	// ------------------------------------------------------------------
	// Request
	// ------------------------------------------------------------------

	@Test
	@DisplayName("An absent property of the request has its default, an explicit null does not")
	void requestDefaults() throws Exception {
		TaskRequest absent = JSON.readerFor(TaskRequest.class).readValue("{}");
		Assertions.assertEquals("", absent.getType());
		Assertions.assertEquals(0L, absent.getGroup());
		Assertions.assertEquals(0L, absent.getDevice());
		Assertions.assertEquals("", absent.getSubnets());
		Assertions.assertNotNull(absent.getScheduleReference());
		Assertions.assertEquals(Task.ScheduleType.ASAP, absent.getScheduleType());
		Assertions.assertEquals(1, absent.getScheduleFactor());
		Assertions.assertEquals(5, absent.getSchedulePriority());
		Assertions.assertEquals(-1, absent.getLimitToOutofdateDeviceHours());
		Assertions.assertEquals(90, absent.getDaysToPurge());
		Assertions.assertEquals(-1, absent.getConfigDaysToPurge());
		Assertions.assertEquals(-1, absent.getModuleDaysToPurge());
		Assertions.assertEquals("", absent.getScript());
		Assertions.assertEquals(Task.ScheduleMode.PARALLEL, absent.getScheduleMode());
		Assertions.assertNull(absent.getDriver());

		TaskRequest nulls = JSON.readerFor(TaskRequest.class)
			.readValue("{\"type\": null, \"group\": null, \"scheduleReference\": null, \"script\": null, \"schedulePriority\": null}");
		Assertions.assertNull(nulls.getType());
		Assertions.assertNull(nulls.getGroup());
		Assertions.assertNull(nulls.getScheduleReference());
		Assertions.assertNull(nulls.getScript());
		Assertions.assertEquals(0, nulls.getSchedulePriority());
	}

	@Test
	@DisplayName("The request is mapped to the command, component by component")
	void requestToCommand() throws Exception {
		TaskRequest request = JSON.readerFor(TaskRequest.class).readValue("""
			{"type": "PurgeDatabaseTask", "device": 1, "group": 2, "deviceList": [3, 4], "scheduleMode": "SEQUENTIAL",
			 "stopOnFailure": true, "domain": 5, "subnets": "10.0.0.0/24", "scheduleReference": 1700000000000,
			 "scheduleType": "DAILY", "scheduleFactor": 2, "schedulePriority": 6, "comments": "c", "debugEnabled": true,
			 "limitToOutofdateDeviceHours": 12, "daysToPurge": 30, "configDaysToPurge": 60, "configSizeToPurge": 10,
			 "configKeepDays": 5, "moduleDaysToPurge": 20, "removeOrphanFiles": true, "script": "s", "driver": "d",
			 "userInputs": {"a": "b"}, "dontRunDiagnostics": true, "dontCheckCompliance": true, "runSnapshot": true,
			 "runDiagnostics": true, "checkCompliance": true}""");
		TaskCreation expected = new TaskCreation("me", "PurgeDatabaseTask", 1L, 2L, List.of(3L, 4L), Task.ScheduleMode.SEQUENTIAL,
			true, 5L, "10.0.0.0/24", new Date(1_700_000_000_000L), Task.ScheduleType.DAILY, 2, 6, "c", true, 12, 30, 60, 10, 5, 20,
			true, "s", "d", Map.of("a", "b"), true, true, true, true, true);
		Assertions.assertEquals(expected, TaskMapper.toCreation(request, "me"));
	}
}
