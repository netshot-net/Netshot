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
package net.netshot.netshot.api.hook;

import java.net.http.HttpClient;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Session;

import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunGroupDiagnosticsTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.contract.ApiContractSeed;

/**
 * The dataset of the web hook tests ({@code @ContractTest} classes, whose tasks are stored but never
 * run): the one of {@link ApiContractSeed}, plus a few tasks (one for each task type not covered by
 * the seed).
 */
final class HookTaskSeed {

	private HookTaskSeed() {
		// Static helpers only
	}

	/**
	 * Seeds the dataset through the REST API and the database.
	 *
	 * @param apiUrl the URL of the REST API of the test server
	 * @return the IDs of the tasks by case name (a task type, optionally followed by a variant after a dash), in a stable order
	 * @throws Exception if the seed fails
	 */
	static Map<String, Long> seed(String apiUrl) throws Exception {
		ApiContractSeed seed = new ApiContractSeed(apiUrl, HttpClient.newHttpClient());
		seed.seed();
		Map<String, Long> taskIds = new LinkedHashMap<>();
		addTasks(seed, taskIds);
		return taskIds;
	}

	/**
	 * Collects the seeded tasks, and adds the tasks which the seed does not provide plus a few variants.
	 *
	 * @param seed the seeded dataset
	 * @param taskIds the map to fill with the task IDs, by case name
	 */
	private static void addTasks(ApiContractSeed seed, Map<String, Long> taskIds) {
		String[] seeded = {
			"TakeSnapshotTask=task.snapshot", "TakeSnapshotTask-failed=task.failed",
			"CheckComplianceTask=task.compliance", "RunDiagnosticsTask=task.diagnostics",
			"RunDeviceScriptTask=task.script", "DiscoverDeviceTypeTask=task.discover",
			"ScanSubnetsTask=task.scan", "PurgeDatabaseTask=task.purge",
			"TakeGroupSnapshotTask=task.group", "CheckGroupComplianceTask=task.groupcompliance",
		};
		for (String entry : seeded) {
			String[] parts = entry.split("=");
			taskIds.put(parts[0], seed.id(parts[1]));
		}
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			Device router1 = session.get(Device.class, seed.id("device.router1"));
			Device router2 = session.get(Device.class, seed.id("device.router2"));
			DeviceGroup group = session.get(DeviceGroup.class, seed.id("group.static"));
			long created = ApiContractSeed.D2022 + 10000;

			CheckGroupSoftwareTask groupSoftware = new CheckGroupSoftwareTask(group, "Group software", "tester");
			taskData(session, groupSoftware, Task.Status.SUCCESS, created, "Software checked");
			taskIds.put("CheckGroupSoftwareTask", groupSoftware.getId());

			RunGroupDiagnosticsTask groupDiagnostics = new RunGroupDiagnosticsTask(group, "Group diagnostics", "tester", true);
			taskData(session, groupDiagnostics, Task.Status.FAILURE, created + 10000, "Diagnostics failed");
			taskIds.put("RunGroupDiagnosticsTask", groupDiagnostics.getId());

			RunDeviceGroupScriptTask groupScript = new RunDeviceGroupScriptTask(group, "function run(cli, device) {}",
				TestDrivers.registry().getDriverByName("CiscoIOS12"), "Group script", "tester");
			taskData(session, groupScript, Task.Status.SUCCESS, created + 20000, "Group script output");
			taskIds.put("RunDeviceGroupScriptTask", groupScript.getId());

			// Group task built from a device list instead of a group
			TakeGroupSnapshotTask listSnapshot = new TakeGroupSnapshotTask(List.of(router1, router2), "List snapshot",
				"tester", -1, false, false);
			taskData(session, listSnapshot, Task.Status.SUCCESS, created + 30000, "List snapshot done");
			taskIds.put("TakeGroupSnapshotTask-devicelist", listSnapshot.getId());

			// Discovery task which created a device
			DiscoverDeviceTypeTask discovered = new DiscoverDeviceTypeTask("10.1.1.1", session.get(Domain.class, seed.id("domain.paris")),
				"Discovered device", "tester");
			discovered.setDiscoveredDeviceType("CiscoIOS12");
			discovered.setDiscoverResultDevice(router1);
			taskData(session, discovered, Task.Status.SUCCESS, created + 40000, "Device created");
			taskIds.put("DiscoverDeviceTypeTask-result", discovered.getId());

			session.getTransaction().commit();
		}
	}

	private static void taskData(Session session, Task task, Task.Status status, long created, String log) {
		task.setStatus(status);
		task.setCreationDate(new Date(created));
		task.setChangeDate(new Date(created + 2000));
		task.setScheduleReference(new Date(created));
		task.setExecutionDate(new Date(created + 1000));
		task.setLogs(log);
		session.persist(task);
	}

	/**
	 * Gets the type of task of a case.
	 *
	 * @param caseName the name of the case (a task type, optionally followed by a variant after a dash)
	 * @return the simple class name of the type of task
	 */
	static String taskType(String caseName) {
		int dash = caseName.indexOf('-');
		return dash < 0 ? caseName : caseName.substring(0, dash);
	}
}
