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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import net.netshot.netshot.ApiContractGolden;
import net.netshot.netshot.DtoContract;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.TestDrivers;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskCreation;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.RunGroupDiagnosticsTask;
import net.netshot.netshot.task.type.ScanSubnetsTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * Checks that the task DTOs and their mapper are a faithful replacement of the {@code Task}
 * entities the REST API used to serialize: for every type of task, the representation of an
 * in-memory task is the same, in JSON and XML, as the one of the entity with the same view.
 * The creation request is read like it always was.
 */
class TaskDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final XmlMapper XML = XmlMapper.builder().build();

	@RegisterExtension
	private final DtoContract contract = new DtoContract(TaskDtoTest.class);

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestConfig.init(new java.util.Properties());
		TestDrivers.reload();
	}

	private static Domain domain() throws Exception {
		Domain domain = new Domain("Paris DC", "Description", new Network4Address("10.255.0.1"), null);
		domain.setId(3);
		return domain;
	}

	private static Device device(long id) throws Exception {
		Device device = new Device("CiscoIOS12", "10.1.1." + id, domain(), "seed");
		device.setId(id);
		device.setName("router-" + id);
		return device;
	}

	private static DeviceGroup group(long id) {
		StaticDeviceGroup group = new StaticDeviceGroup("Group " + id);
		group.setId(id);
		return group;
	}

	/** Gives every property shared by the tasks a distinctive value. */
	private static <T extends Task> T filled(T task, long id, Task.Status status) {
		task.setId(id);
		task.setAuthor("author-" + id);
		task.setChangeDate(new Date(1_600_000_000_000L + id));
		task.setCreationDate(new Date(1_500_000_000_000L + id));
		task.setExecutionDate(status == Task.Status.NEW ? null : new Date(1_550_000_000_000L + id));
		task.setComments("Comments of " + id);
		task.setDebugEnabled(id % 2 == 0);
		task.setStatus(status);
		task.setScheduleType(Task.ScheduleType.WEEKLY);
		task.setScheduleReference(new Date(1_700_000_000_000L));
		task.setScheduleFactor(3);
		task.setPriority(7);
		task.setRunnerId("runner-" + id);
		task.setParentTaskId(id + 100);
		task.setChildOrder((int) id);
		task.setScheduleMode(Task.ScheduleMode.SEQUENTIAL);
		task.setStopOnFailure(true);
		task.setCancelRequested(true);
		task.setDeviceDriver("CiscoIOS12");
		task.setScript("script of " + id);
		task.setUserInputValues(Map.of("input", "value"));
		task.setRunSnapshot(true);
		task.setRunDiagnostics(true);
		task.setCheckCompliance(true);
		task.setLog("line 1\nline 2 <&>");
		return task;
	}

	private List<Task> allTasks() throws Exception {
		List<Task> tasks = new ArrayList<>();
		tasks.add(filled(new TakeSnapshotTask(device(1), "snap", "tester", false, true, true), 1, Task.Status.SUCCESS));
		tasks.add(filled(new CheckComplianceTask(device(2), "check", "tester"), 2, Task.Status.FAILURE));
		tasks.add(filled(new RunDiagnosticsTask(device(3), "diag", "tester", true), 3, Task.Status.RUNNING));
		tasks.add(filled(new RunDeviceScriptTask(device(4), "script", TestDrivers.registry().getDriverByName("CiscoIOS12"), "script", "tester"),
			4, Task.Status.SCHEDULED));
		DiscoverDeviceTypeTask discover = new DiscoverDeviceTypeTask("10.9.9.9", domain(), "discover", "tester");
		discover.setDiscoveredDeviceType("CiscoIOS12");
		discover.setDiscoverResultDevice(device(5));
		discover.getAttributes().put("snapshotTaskId", 55L);
		tasks.add(filled(discover, 5, Task.Status.SUCCESS));
		tasks.add(filled(new DiscoverDeviceTypeTask("10.9.9.10", domain(), "discover without device", "tester"), 6, Task.Status.NEW));
		TakeGroupSnapshotTask groupSnapshot = new TakeGroupSnapshotTask(group(10), "group snap", "tester", 12, false, true);
		tasks.add(filled(groupSnapshot, 7, Task.Status.WAITING));
		tasks.add(filled(new CheckGroupComplianceTask(group(11), "group check", "tester"), 8, Task.Status.DELAYED));
		tasks.add(filled(new CheckGroupSoftwareTask(group(12), "group software", "tester"), 9, Task.Status.CANCELLED));
		tasks.add(filled(new RunGroupDiagnosticsTask(group(13), "group diag", "tester", false), 10, Task.Status.SUCCESS));
		RunDeviceGroupScriptTask groupScript = new RunDeviceGroupScriptTask();
		groupScript.setDeviceGroup(group(14));
		groupScript.setTarget("Group 14");
		tasks.add(filled(groupScript, 11, Task.Status.RUNNING));
		PurgeDatabaseTask purge = new PurgeDatabaseTask("purge", "tester", 30, 60, 10, 5, 20, group(15));
		purge.setRemoveOrphanFiles(true);
		tasks.add(filled(purge, 12, Task.Status.SCHEDULED));
		tasks.add(filled(new PurgeDatabaseTask("purge all", "tester", 90, -1, 0, 0, -1, (DeviceGroup) null), 13, Task.Status.NEW));
		tasks.add(filled(new PurgeDatabaseTask("purge devices", "tester", 90, -1, 0, 0, -1, List.of(device(6))), 14, Task.Status.NEW));
		tasks.add(filled(new ScanSubnetsTask(Set.of(new Network4Address("10.8.0.0", 24)), domain(), "scan", "{10.8.0.0/24}", "tester"),
			15, Task.Status.SUCCESS));
		// The tasks which target nothing
		tasks.add(filled(new TakeGroupSnapshotTask(), 16, Task.Status.NEW));
		tasks.add(filled(new CheckGroupComplianceTask(), 17, Task.Status.NEW));
		tasks.add(filled(new RunDeviceGroupScriptTask(), 18, Task.Status.NEW));
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
			this.assertRepresentation(dto, task.getClass().getSimpleName() + " " + task.getId());
			covered.add(task.getClass());
			// Make sure the check is meaningful
			String json = new String(json(dto));
			Assertions.assertTrue(json.contains("\"type\":\"" + task.getClass().getSimpleName() + "\""), json);
			Assertions.assertTrue(json.contains("\"log\":\"line 1\\nline 2 <&>\""), json);
			Assertions.assertTrue(new String(xml(dto)).contains("<" + task.getClass().getSimpleName() + " type=\""), new String(xml(dto)));
		}
		Assertions.assertEquals(Task.getTaskClasses(), covered, "every type of task is tested");
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
