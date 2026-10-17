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
package net.netshot.netshot;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.hibernate.Session;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.quartz.Scheduler;
import org.quartz.impl.SchedulerRepository;

import jakarta.ws.rs.core.UriBuilder;
import net.netshot.netshot.api.rest.RestServer;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.engine.TaskManager;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunGroupDiagnosticsTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;

/**
 * The dataset of the web hook tests: the one of {@link ApiContractSeed}, plus a few tasks (one
 * for each task type not covered by the seed). The tasks are stored but never run.
 */
@TestInstance(Lifecycle.PER_CLASS)
public abstract class HookSeededTest extends WithDatabaseTest {

	/** The URL of the REST API of the test server (the seed goes through it). */
	protected final String apiUrl = TestNetwork.freeLocalApiUrl();

	protected final long startTime = System.currentTimeMillis();
	private TimeZone previousTimeZone;
	private Path binaryFolder;
	protected ApiContractSeed seed;
	/** Task IDs by case name (LinkedHashMap: stable order). */
	protected final Map<String, Long> taskIds = new LinkedHashMap<>();

	/**
	 * Gets the name of the schema of the test class.
	 *
	 * @return the name
	 */
	protected abstract String databaseName();

	@BeforeAll
	void bootAndSeed() throws Exception {
		this.previousTimeZone = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		Properties config = getFreshDatabaseConfig(this.databaseName());
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "INFO");
		config.setProperty("netshot.http.ssl.enabled", "false");
		URI uri = UriBuilder.fromUri(this.apiUrl).replacePath("/").build();
		config.setProperty("netshot.http.baseurl", uri.toString());
		this.binaryFolder = Files.createTempDirectory("netshot-hook-contract-");
		config.setProperty("netshot.snapshots.binary.path", this.binaryFolder.toString());
		Netshot.initConfig(config);
		Netshot.loadModuleConfigs();
		DeviceDriver.refreshDrivers();
		Database.update();
		Wiring.init();
		TaskManager.init();
		// Tasks must be stored but never run
		for (Scheduler scheduler : SchedulerRepository.getInstance().lookupAll()) {
			scheduler.standby();
			scheduler.clear();
		}
		RestServer.init(Wiring.get().binder());
		Thread.sleep(1000);
		this.seed = new ApiContractSeed(this.apiUrl, java.net.http.HttpClient.newHttpClient());
		this.seed.seed();
		this.addTasks();
	}

	@AfterAll
	void shutdown() throws Exception {
		TimeZone.setDefault(this.previousTimeZone);
		if (this.binaryFolder != null) {
			try (Stream<Path> files = Files.walk(this.binaryFolder)) {
				files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
			}
		}
		for (Scheduler scheduler : SchedulerRepository.getInstance().lookupAll()) {
			scheduler.clear();
			scheduler.start();
		}
	}

	/** Collects the seeded tasks, and adds the tasks which the seed does not provide plus a few variants. */
	private void addTasks() {
		String[] seeded = {
			"TakeSnapshotTask=task.snapshot", "TakeSnapshotTask-failed=task.failed",
			"CheckComplianceTask=task.compliance", "RunDiagnosticsTask=task.diagnostics",
			"RunDeviceScriptTask=task.script", "DiscoverDeviceTypeTask=task.discover",
			"ScanSubnetsTask=task.scan", "PurgeDatabaseTask=task.purge",
			"TakeGroupSnapshotTask=task.group", "CheckGroupComplianceTask=task.groupcompliance",
		};
		for (String entry : seeded) {
			String[] parts = entry.split("=");
			this.taskIds.put(parts[0], this.seed.id(parts[1]));
		}
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			Device router1 = session.get(Device.class, this.seed.id("device.router1"));
			Device router2 = session.get(Device.class, this.seed.id("device.router2"));
			DeviceGroup group = session.get(DeviceGroup.class, this.seed.id("group.static"));
			long created = ApiContractSeed.D2022 + 10000;

			CheckGroupSoftwareTask groupSoftware = new CheckGroupSoftwareTask(group, "Group software", "tester");
			this.taskData(session, groupSoftware, Task.Status.SUCCESS, created, "Software checked");
			this.taskIds.put("CheckGroupSoftwareTask", groupSoftware.getId());

			RunGroupDiagnosticsTask groupDiagnostics = new RunGroupDiagnosticsTask(group, "Group diagnostics", "tester", true);
			this.taskData(session, groupDiagnostics, Task.Status.FAILURE, created + 10000, "Diagnostics failed");
			this.taskIds.put("RunGroupDiagnosticsTask", groupDiagnostics.getId());

			RunDeviceGroupScriptTask groupScript = new RunDeviceGroupScriptTask(group, "function run(cli, device) {}",
				DeviceDriver.getDriverByName("CiscoIOS12"), "Group script", "tester");
			this.taskData(session, groupScript, Task.Status.SUCCESS, created + 20000, "Group script output");
			this.taskIds.put("RunDeviceGroupScriptTask", groupScript.getId());

			// Group task built from a device list instead of a group
			TakeGroupSnapshotTask listSnapshot = new TakeGroupSnapshotTask(List.of(router1, router2), "List snapshot",
				"tester", -1, false, false);
			this.taskData(session, listSnapshot, Task.Status.SUCCESS, created + 30000, "List snapshot done");
			this.taskIds.put("TakeGroupSnapshotTask-devicelist", listSnapshot.getId());

			// Discovery task which created a device
			DiscoverDeviceTypeTask discovered = new DiscoverDeviceTypeTask("10.1.1.1", session.get(Domain.class, this.seed.id("domain.paris")),
				"Discovered device", "tester");
			discovered.setDiscoveredDeviceType("CiscoIOS12");
			discovered.setDiscoverResultDevice(router1);
			this.taskData(session, discovered, Task.Status.SUCCESS, created + 40000, "Device created");
			this.taskIds.put("DiscoverDeviceTypeTask-result", discovered.getId());

			session.getTransaction().commit();
		}
	}

	private void taskData(Session session, Task task, Task.Status status, long created, String log) {
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
	protected static String taskType(String caseName) {
		int dash = caseName.indexOf('-');
		return dash < 0 ? caseName : caseName.substring(0, dash);
	}
}
