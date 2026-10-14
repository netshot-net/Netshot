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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.hibernate.Session;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.quartz.Scheduler;
import org.quartz.impl.SchedulerRepository;

import com.sun.net.httpserver.HttpServer;

import jakarta.ws.rs.core.UriBuilder;
import net.netshot.netshot.ApiContractGolden.Format;
import net.netshot.netshot.api.rest.v0.RestService;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.hook.WebHook;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.boot.EntityModel;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.engine.TaskManager;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunGroupDiagnosticsTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;

/**
 * Golden file tests of the web hook payloads: the body posted by
 * {@link WebHook#execute(Object)} for each task type that can trigger a hook
 * (the only trigger is POST_TASK, and the payload is the task entity itself),
 * in JSON, XML and YAML. Golden files are stored under
 * {@code src/test/resources/hook-contract/} and must not change when the
 * implementation is refactored.
 *
 * <p>The dataset is the one of {@link ApiContractSeed}, plus a few tasks (one
 * for each task type not covered by the seed). Each task is loaded and
 * prepared the same way {@code TaskJob} does before running the hooks, then
 * handed to a real {@link WebHook} pointing at a local HTTP receiver, whose
 * request body is normalized like the REST API contract responses. Run with
 * {@code -Dnetshot.hook.contract.update=true} to (re)write the golden files.</p>
 */
@TestInstance(Lifecycle.PER_CLASS)
public class WebHookContractTest extends WithDatabaseTest {

	private static final String API_URL = "http://localhost:8890/api";

	/** System property, set to true to rewrite the golden files. */
	static final String UPDATE_PROPERTY = "netshot.hook.contract.update";

	/** Directory holding the golden files. */
	static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "hook-contract");

	private final long startTime = System.currentTimeMillis();
	private TimeZone previousTimeZone;
	private Path binaryFolder;
	private ApiContractSeed seed;
	private HttpServer receiver;
	private final AtomicReference<byte[]> receivedBody = new AtomicReference<>();
	private final AtomicReference<String> receivedType = new AtomicReference<>();
	/** Task IDs by case name (LinkedHashMap: stable order). */
	private final Map<String, Long> taskIds = new LinkedHashMap<>();

	@BeforeAll
	void bootAndSeed() throws Exception {
		this.previousTimeZone = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		Properties config = getFreshDatabaseConfig("webhookcontracttest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "INFO");
		config.setProperty("netshot.http.ssl.enabled", "false");
		URI uri = UriBuilder.fromUri(API_URL).replacePath("/").build();
		config.setProperty("netshot.http.baseurl", uri.toString());
		this.binaryFolder = Files.createTempDirectory("netshot-hook-contract-");
		config.setProperty("netshot.snapshots.binary.path", this.binaryFolder.toString());
		Netshot.initConfig(config);
		Netshot.loadModuleConfigs();
		DeviceDriver.refreshDrivers();
		Database.update();
		Database.init(EntityModel.build());
		TaskManager.init();
		// Tasks must be stored but never run
		for (Scheduler scheduler : SchedulerRepository.getInstance().lookupAll()) {
			scheduler.standby();
			scheduler.clear();
		}
		RestService.init();
		Thread.sleep(1000);
		this.seed = new ApiContractSeed(API_URL, HttpClient.newHttpClient());
		this.seed.seed();
		this.addTasks();

		this.receiver = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		this.receiver.createContext("/", exchange -> {
			this.receivedBody.set(exchange.getRequestBody().readAllBytes());
			this.receivedType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		this.receiver.start();
	}

	@AfterAll
	void shutdown() throws Exception {
		TimeZone.setDefault(this.previousTimeZone);
		if (this.receiver != null) {
			this.receiver.stop(0);
		}
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
	 * Posts a task through a real web hook, as {@code TaskJob} does.
	 * @return the canonical payload
	 */
	private String payload(long taskId, WebHook.Action action, Format format) throws Exception {
		WebHook hook = new WebHook() {
		};
		hook.setName("test");
		hook.setAction(action);
		hook.setUrl("http://127.0.0.1:" + this.receiver.getAddress().getPort() + "/hook");
		this.receivedBody.set(null);
		String result;
		try (Session session = Database.getSession()) {
			Task task = session.get(Task.class, taskId);
			task.prepare(session);
			result = hook.execute(task);
		}
		Assertions.assertEquals("HTTP response code 200", result);
		byte[] body = this.receivedBody.get();
		Assertions.assertNotNull(body, "The hook receiver did not get any request");
		String type = this.receivedType.get();
		String canonical = new ApiContractGolden(this.startTime).canonicalize(format, body, ApiContractGolden.Transform.NONE, false);
		return "# Content-Type: " + type + "\n" + canonical;
	}

	@TestFactory
	List<DynamicTest> hookPayloads() {
		boolean update = Boolean.parseBoolean(System.getProperty(UPDATE_PROPERTY, "false"));
		Map<WebHook.Action, Format> actions = new LinkedHashMap<>();
		actions.put(WebHook.Action.POST_JSON, Format.JSON);
		actions.put(WebHook.Action.POST_XML, Format.XML);
		actions.put(WebHook.Action.POST_YAML, Format.YAML);
		List<DynamicTest> tests = new ArrayList<>();
		for (Map.Entry<String, Long> task : this.taskIds.entrySet()) {
			for (Map.Entry<WebHook.Action, Format> action : actions.entrySet()) {
				String fileName = "POST_TASK-" + task.getKey() + "." + action.getValue().extension;
				tests.add(DynamicTest.dynamicTest(fileName, () -> {
					String actual = this.payload(task.getValue(), action.getKey(), action.getValue());
					if (update) {
						ApiContractGolden.writeGolden(GOLDEN_DIR, fileName, actual);
						return;
					}
					String expected = ApiContractGolden.readGolden(GOLDEN_DIR, fileName);
					Assertions.assertNotNull(expected, "No golden file " + fileName
						+ " (run with -D" + UPDATE_PROPERTY + "=true to create it)");
					if (!expected.equals(actual)) {
						Assertions.fail("Hook payload changed for " + fileName + ":\n"
							+ ApiContractGolden.diff(expected, actual, fileName));
					}
				}));
			}
		}
		return tests;
	}
}
