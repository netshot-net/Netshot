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
package net.netshot.netshot.task;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;
import org.quartz.SchedulerException;

import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.Lookup;
import net.netshot.netshot.platform.query.SortOrder;
import net.netshot.netshot.platform.query.StatsQuery;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.GroupBasedTask;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.task.type.ScanSubnetsTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;

/**
 * Tests of the task use cases, against a real database, a recording task submitter and a fake task engine.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Task service")
class TaskServiceTest {

	private static final String AUTHOR = "tester";
	private static final String SCRIPT = "function run(cli, device) {}";

	private final List<Task> submittedTasks = new ArrayList<>();
	private final List<Long> cancelRequests = new ArrayList<>();
	private final List<Task> cancelledTasks = new ArrayList<>();
	private SchedulerException schedulerFailure;
	private boolean engineFailure;
	private TaskService service;
	private Transactions transactions;
	private long domainId;
	private long deviceId;
	private long otherDeviceId;
	private long groupId;
	private long snapshotId;
	private long failedId;

	@BeforeAll
	void initDb() throws Exception {
		this.transactions = TestWiring.get().core().transactions();
		TaskSubmitter submitter = task -> {
			if (this.schedulerFailure != null) {
				throw this.schedulerFailure;
			}
			this.submittedTasks.add(task);
			return task;
		};
		TaskEngine engine = new TaskEngine() {
			@Override
			public void requestCancel(long taskId) {
				if (TaskServiceTest.this.engineFailure) {
					throw new IllegalStateException("engine failure");
				}
				TaskServiceTest.this.cancelRequests.add(taskId);
			}

			@Override
			public void cancel(Task task, String reason) throws SchedulerException {
				if (TaskServiceTest.this.engineFailure) {
					throw new SchedulerException("engine failure");
				}
				TaskServiceTest.this.cancelledTasks.add(task);
			}

			@Override
			public int threadCount() {
				return 7;
			}
		};
		this.service = new TaskService(this.transactions, new TaskRepository(), submitter, engine, TestDrivers.registry(),
			TestWiring.get().core().repositories().device(), TestWiring.get().core().repositories().domain(), TestWiring.get().core().repositories().group());
		Domain domain = TestDevices.addDomain("Lab", "Test domain");
		this.domainId = domain.id();
		Device device = TestDevices.add(Device.create("CiscoIOS12", "10.30.0.1", domain, AUTHOR).toBuilder().name("router-1").build());
		this.otherDeviceId = TestDevices.add(Device.create("CiscoIOS12", "10.30.0.2", domain, AUTHOR).toBuilder().name("router-2").build()).id();
		this.groupId = TestDevices.addStaticGroup("Group").id();
		this.deviceId = device.id();
		this.snapshotId = this.persistTask(this.deviceTask(Task.Status.SUCCESS, new Date(1_600_000_000_000L), "with debug log"));
		TestTasks.setDebugLog(this.snapshotId, "debug text");
		this.failedId = this.persistTask(this.deviceTask(Task.Status.FAILURE, new Date(1_600_000_100_000L), "failed"));
	}

	@BeforeEach
	void reset() {
		this.submittedTasks.clear();
		this.cancelRequests.clear();
		this.cancelledTasks.clear();
		this.schedulerFailure = null;
		this.engineFailure = false;
	}

	private Task deviceTask(Task.Status status, Date date, String comments) {
		Device device = TestDevices.find(this.deviceId);
		return TakeSnapshotTask.of(device, comments, AUTHOR, false, false, false).toBuilder()
			.status(status)
			.changeDate(date.toInstant())
			.executionDate(date.toInstant())
			.build();
	}

	private long persistTask(Task task) {
		return TestTasks.add(task).id();
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private static CreationBuilder creation(String type) {
		return new CreationBuilder(type);
	}

	// ------------------------------------------------------------------
	// read
	// ------------------------------------------------------------------

	@Test
	@DisplayName("get() returns the task, and reports an unknown one")
	void get() {
		Task task = this.service.get(this.snapshotId);
		Assertions.assertInstanceOf(TakeSnapshotTask.class, task.spec());
		Assertions.assertEquals("with debug log", task.comments());
		NetshotException e = Assertions.assertThrows(NetshotException.class, () -> this.service.get(99_999_999L));
		Assertions.assertEquals(ErrorCode.NETSHOT_TASK_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("getDebugLog() returns the text, an empty text when there is none, and reports an unknown task")
	void debugLog() {
		Assertions.assertEquals("debug text", this.service.getDebugLog(this.snapshotId));
		Assertions.assertEquals("", this.service.getDebugLog(this.failedId));
		assertError(ErrorCode.NETSHOT_TASK_NOT_FOUND, () -> this.service.getDebugLog(99_999_999L));
	}

	private List<TaskSummary> summaries(Page page, List<SortOrder> sort, Criterion... criteria) {
		return this.service.listSummaries(new ListQuery(List.of(criteria), null, sort, page, false)).items();
	}

	private static Criterion of(String field, Lookup lookup, Object... values) {
		return new Criterion(field, lookup, List.of(values));
	}

	@Test
	@DisplayName("listSummaries() filters, orders and pages the summaries, on their date (execution date, else change date) too")
	void list() {
		List<TaskSummary> summaries = this.summaries(Page.ALL, List.of());
		Assertions.assertTrue(summaries.size() >= 2);
		Assertions.assertTrue(summaries.get(0).id() > summaries.get(1).id(), "most recent first");
		TaskSummary failed = summaries.stream().filter(s -> s.id() == this.failedId).findFirst().orElseThrow();
		Assertions.assertEquals("TakeSnapshotTask", failed.type());
		Assertions.assertEquals(this.deviceId, failed.deviceId());
		Assertions.assertNull(failed.deviceGroupId());

		List<TaskSummary> failures = this.summaries(Page.ALL, List.of(), of("status", Lookup.EXACT, "FAILURE"));
		Assertions.assertTrue(failures.stream().allMatch(s -> s.status() == Task.Status.FAILURE));
		Assertions.assertTrue(failures.stream().anyMatch(s -> s.id() == this.failedId));
		Assertions.assertTrue(this.summaries(Page.ALL, List.of(), of("type", Lookup.EXACT, "CheckComplianceTask")).stream()
			.noneMatch(s -> s.id() == this.failedId));

		List<TaskSummary> dated = this.summaries(Page.ALL, List.of(), of("date", Lookup.GTE, Instant.ofEpochMilli(1_600_000_050_000L)),
			of("date", Lookup.LTE, Instant.ofEpochMilli(1_600_000_150_000L)));
		Assertions.assertTrue(dated.stream().anyMatch(s -> s.id() == this.failedId));
		Assertions.assertTrue(dated.stream().noneMatch(s -> s.id() == this.snapshotId));

		Assertions.assertEquals(1, this.summaries(new Page(0, 1), List.of()).size());
		Assertions.assertTrue(this.summaries(Page.ALL, List.of(), of("parentTask", Lookup.EXACT, 99_999_999L)).isEmpty());
	}

	@Test
	@DisplayName("listSummaries() sorts on the activity of the status: the active tasks first")
	void listByActivity() {
		long scheduled = this.persistTask(this.deviceTask(Task.Status.SCHEDULED, new Date(), "scheduled"));
		List<TaskSummary> tasks = this.summaries(Page.ALL, List.of(SortOrder.asc("activity"), SortOrder.desc("date")),
			of("device", Lookup.EXACT, this.deviceId));
		List<Long> ids = tasks.stream().map(TaskSummary::id).toList();
		Assertions.assertTrue(ids.indexOf(scheduled) < ids.indexOf(this.snapshotId), "the scheduled task before the completed ones");
		List<Task.Status> active = List.of(Task.Status.RUNNING, Task.Status.WAITING, Task.Status.SCHEDULED, Task.Status.NEW);
		List<Integer> ranks = tasks.stream().map(t -> active.contains(t.status()) ? active.indexOf(t.status()) : active.size()).toList();
		Assertions.assertEquals(ranks.stream().sorted().toList(), ranks, "the tasks by activity");
		Assertions.assertTrue(this.summaries(Page.ALL, List.of(), of("device", Lookup.EXACT, this.otherDeviceId)).isEmpty());
	}

	@Test
	@DisplayName("getOverview() counts every status, and tells the thread count")
	void overview() {
		TaskOverview overview = this.service.getOverview(null);
		Assertions.assertEquals(Set.of(Task.Status.values()), overview.countByStatus().keySet());
		Assertions.assertTrue(overview.countByStatus().get(Task.Status.FAILURE) >= 1);
		Assertions.assertEquals(0L, overview.countByStatus().get(Task.Status.DELAYED));
		Assertions.assertEquals(7, overview.threadCount());
		Assertions.assertEquals(0L, this.service.getOverview(99_999_999L).countByStatus().get(Task.Status.FAILURE));
	}

	@Test
	@DisplayName("stats() counts the completed tasks of a range of dates (execution date, else change date)")
	void stats() {
		ListQuery range = new ListQuery(List.of(of("status", Lookup.EXACT, "SUCCESS", "FAILURE", "CANCELLED"),
			of("date", Lookup.GTE, Instant.ofEpochMilli(1_600_000_000_000L - 1)), of("date", Lookup.LT, Instant.ofEpochMilli(1_600_000_200_000L))),
			null, List.of(), Page.ALL, false);
		Map<Object, Long> byStatus = new HashMap<>();
		this.service.stats(range, new StatsQuery(List.of("status"), null, null, null, null, null)).items()
			.forEach(row -> byStatus.put(row.keys().get(0), row.count()));
		Assertions.assertEquals(Map.of("SUCCESS", 1L, "FAILURE", 1L), byStatus);
		Map<Object, Long> byType = new HashMap<>();
		this.service.stats(range, new StatsQuery(List.of("type"), null, null, null, null, null)).items()
			.forEach(row -> byType.put(row.keys().get(0), row.count()));
		Assertions.assertEquals(2L, byType.get("TakeSnapshotTask"));
	}

	// ------------------------------------------------------------------
	// cancel
	// ------------------------------------------------------------------

	@Test
	@DisplayName("cancel() reports an unknown task")
	void cancelUnknown() {
		assertError(ErrorCode.NETSHOT_TASK_NOT_FOUND, () -> this.service.cancel(99_999_999L, AUTHOR));
		Assertions.assertTrue(this.cancelledTasks.isEmpty());
		Assertions.assertTrue(this.cancelRequests.isEmpty());
	}

	@Test
	@DisplayName("cancel() cancels a scheduled task through the engine, and only a scheduled one")
	void cancel() {
		assertError(ErrorCode.NETSHOT_TASK_NOT_CANCELLABLE, () -> this.service.cancel(this.failedId, AUTHOR));
		Task scheduled = this.deviceTask(Task.Status.SCHEDULED, new Date(), "scheduled");
		long id = this.persistTask(scheduled);
		this.service.cancel(id, AUTHOR);
		Assertions.assertEquals(1, this.cancelledTasks.size());
		Assertions.assertEquals(id, this.cancelledTasks.get(0).id());
		Assertions.assertTrue(this.cancelRequests.isEmpty());
	}

	@Test
	@DisplayName("cancel() only requests the cancellation of a running task")
	void cancelRunning() {
		long runningDevice = this.persistTask(this.deviceTask(Task.Status.RUNNING, new Date(), "running"));
		this.service.cancel(runningDevice, AUTHOR);
		Assertions.assertEquals(List.of(runningDevice), this.cancelRequests);
		Assertions.assertTrue(this.cancelledTasks.isEmpty());

		Task group = GroupBasedTask.of(new TakeGroupSnapshotTask(null, List.of(this.deviceId), -1, false, false), null,
			"running group", AUTHOR);
		long runningGroup = this.persistTask(group.withStatus(Task.Status.RUNNING));
		this.service.cancel(runningGroup, AUTHOR);
		Assertions.assertEquals(List.of(runningDevice, runningGroup), this.cancelRequests);
	}

	@Test
	@DisplayName("cancel() reports a failing engine as a cancel error")
	void cancelFailure() {
		long id = this.persistTask(this.deviceTask(Task.Status.SCHEDULED, new Date(), "scheduled"));
		this.engineFailure = true;
		assertError(ErrorCode.NETSHOT_TASK_CANCEL_ERROR, () -> this.service.cancel(id, AUTHOR));
	}

	// ------------------------------------------------------------------
	// create
	// ------------------------------------------------------------------

	@Test
	@DisplayName("create() stores nothing and submits nothing for an invalid type")
	void createInvalidType() {
		assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(creation("NoSuchTask").build()));
		assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(creation(null).build()));
		Assertions.assertTrue(this.submittedTasks.isEmpty());
	}

	@Test
	@DisplayName("create() submits the device tasks, with their options, and the priority")
	void createDeviceTasks() {
		Task snapshot = this.service.create(creation("TakeSnapshotTask").device(this.deviceId).comments("c1")
			.dontRunDiagnostics(true).priority(7).build());
		TakeSnapshotTask take = Assertions.assertInstanceOf(TakeSnapshotTask.class, snapshot.spec());
		Assertions.assertEquals(this.deviceId, take.deviceId());
		Assertions.assertEquals("c1", snapshot.comments());
		Assertions.assertEquals(AUTHOR, snapshot.author());
		Assertions.assertTrue(take.dontRunDiagnostics());
		Assertions.assertEquals(7, snapshot.priority());
		Assertions.assertEquals(List.of(snapshot), this.submittedTasks);

		Assertions.assertInstanceOf(CheckComplianceTask.class,
			this.service.create(creation("CheckComplianceTask").device(this.deviceId).build()).spec());
		Assertions.assertInstanceOf(net.netshot.netshot.task.type.RunDiagnosticsTask.class,
			this.service.create(creation("RunDiagnosticsTask").device(this.deviceId).build()).spec());
		Assertions.assertEquals(3, this.submittedTasks.size());

		RunDeviceScriptTask script = Assertions.assertInstanceOf(RunDeviceScriptTask.class,
			this.service.create(creation("RunDeviceScriptTask").device(this.deviceId).driver("CiscoIOS12").script(SCRIPT)
				.runSnapshot(true).userInputs(Map.of()).build()).spec());
		Assertions.assertEquals(SCRIPT, script.script());
		Assertions.assertTrue(script.runSnapshot());
	}

	@Test
	@DisplayName("create() reports the unknown devices and the unknown groups")
	void createUnknownTargets() {
		assertError(ErrorCode.NETSHOT_INVALID_DEVICE, () -> this.service.create(creation("TakeSnapshotTask").device(99_999_999L).build()));
		assertError(ErrorCode.NETSHOT_INVALID_DEVICE, () -> this.service.create(creation("CheckComplianceTask").device(99_999_999L).build()));
		assertError(ErrorCode.NETSHOT_INVALID_GROUP, () -> this.service.create(creation("TakeGroupSnapshotTask").group(99_999_999L).build()));
		assertError(ErrorCode.NETSHOT_INVALID_DEVICE, () -> this.service.create(
			creation("CheckGroupComplianceTask").deviceList(List.of(this.deviceId, 99_999_999L)).build()));
		Assertions.assertTrue(this.submittedTasks.isEmpty());
	}

	@Test
	@DisplayName("create() requires exactly one of a group or a device list for the group tasks")
	void createGroupOrDeviceList() {
		for (String type : List.of("TakeGroupSnapshotTask", "CheckGroupComplianceTask", "CheckGroupSoftwareTask", "RunGroupDiagnosticsTask")) {
			assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(creation(type).build()));
			assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(
				creation(type).group(this.groupId).deviceList(List.of(this.deviceId)).build()));
			Task byGroup = this.service.create(creation(type).group(this.groupId).build());
			Assertions.assertEquals(type, byGroup.typeName());
			Task byList = this.service.create(creation(type).deviceList(List.of(this.deviceId, this.otherDeviceId)).build());
			Assertions.assertEquals(type, byList.typeName());
		}
		Assertions.assertEquals(8, this.submittedTasks.size());
		Task snapshot = this.service.create(
			creation("TakeGroupSnapshotTask").group(this.groupId).limitHours(12).scheduleMode(Task.ScheduleMode.SEQUENTIAL)
				.stopOnFailure(true).build());
		Assertions.assertEquals(12, ((TakeGroupSnapshotTask) snapshot.spec()).limitToOutofdateDeviceHours());
		Assertions.assertEquals(Task.ScheduleMode.SEQUENTIAL, snapshot.scheduleMode());
		Assertions.assertTrue(snapshot.stopOnFailure());
		Assertions.assertInstanceOf(RunDeviceGroupScriptTask.class, this.service.create(
			creation("RunDeviceGroupScriptTask").group(this.groupId).driver("CiscoIOS12").script(SCRIPT).build()).spec());
	}

	@Test
	@DisplayName("create() checks the driver and the script of the script tasks, in this order")
	void createScriptChecks() {
		assertError(ErrorCode.NETSHOT_INVALID_DEVICE, () -> this.service.create(
			creation("RunDeviceScriptTask").device(99_999_999L).driver("NoSuchDriver").script(SCRIPT).build()));
		assertError(ErrorCode.NETSHOT_INVALID_DEVICE, () -> this.service.create(
			creation("RunDeviceScriptTask").device(this.deviceId).driver("CiscoIOS12").script(null).build()));
		assertError(ErrorCode.NETSHOT_INVALID_DEVICE, () -> this.service.create(
			creation("RunDeviceGroupScriptTask").group(this.groupId).driver("NoSuchDriver").script(SCRIPT).build()));
		assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(
			creation("RunDeviceGroupScriptTask").driver("CiscoIOS12").script(SCRIPT).build()));
		Assertions.assertTrue(this.submittedTasks.isEmpty());
	}

	@Test
	@DisplayName("create() validates the subnets and the domain of a subnet scan")
	void createScan() {
		assertError(ErrorCode.NETSHOT_INVALID_SUBNET, () -> this.service.create(creation("ScanSubnetsTask").domain(this.domainId).subnets("nope").build()));
		assertError(ErrorCode.NETSHOT_INVALID_SUBNET, () -> this.service.create(creation("ScanSubnetsTask").domain(this.domainId).subnets("10.0.0.256").build()));
		assertError(ErrorCode.NETSHOT_SCAN_SUBNET_TOO_BIG, () -> this.service.create(creation("ScanSubnetsTask").domain(this.domainId).subnets("10.0.0.0/8").build()));
		assertError(ErrorCode.NETSHOT_INVALID_DOMAIN, () -> this.service.create(creation("ScanSubnetsTask").domain(0L).subnets("10.0.0.0/24").build()));
		assertError(ErrorCode.NETSHOT_INVALID_DOMAIN, () -> this.service.create(creation("ScanSubnetsTask").domain(99_999_999L).subnets("10.0.0.0/24").build()));
		Assertions.assertTrue(this.submittedTasks.isEmpty());
		Task scan = this.service.create(
			creation("ScanSubnetsTask").domain(this.domainId).subnets("10.0.0.0/24,10.0.1.5").build());
		Assertions.assertInstanceOf(ScanSubnetsTask.class, scan.spec());
		Assertions.assertTrue(scan.target().startsWith("{"), scan.target());
		Assertions.assertEquals(1, this.submittedTasks.size());
	}

	@Test
	@DisplayName("create() validates the retention of a database purge")
	void createPurge() {
		assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(creation("PurgeDatabaseTask").days(1).build()));
		assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(creation("PurgeDatabaseTask").days(30).configDays(3).build()));
		assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(creation("PurgeDatabaseTask").days(30).configDays(10).configSize(-1).build()));
		assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(creation("PurgeDatabaseTask").days(30).configDays(10).configKeepDays(-1).build()));
		assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(creation("PurgeDatabaseTask").days(30).configDays(10).configKeepDays(10).build()));
		assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(creation("PurgeDatabaseTask").days(30).moduleDays(2).build()));
		assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(
			creation("PurgeDatabaseTask").days(30).group(this.groupId).deviceList(List.of(this.deviceId)).build()));
		assertError(ErrorCode.NETSHOT_INVALID_GROUP, () -> this.service.create(creation("PurgeDatabaseTask").days(30).group(99_999_999L).build()));
		assertError(ErrorCode.NETSHOT_INVALID_DEVICE, () -> this.service.create(creation("PurgeDatabaseTask").days(30).deviceList(List.of(99_999_999L)).build()));
		Assertions.assertTrue(this.submittedTasks.isEmpty());
		PurgeDatabaseTask purge = (PurgeDatabaseTask) this.service.create(
			creation("PurgeDatabaseTask").days(30).configDays(-1).configSize(10).configKeepDays(5).removeOrphanFiles(true).build()).spec();
		Assertions.assertEquals(30, purge.days());
		Assertions.assertEquals(-1, purge.configDays());
		Assertions.assertEquals(0, purge.configSize(), "ignored without configuration purge");
		Assertions.assertEquals(0, purge.configKeepDays(), "ignored without configuration purge");
		Assertions.assertTrue(purge.removeOrphanFiles());
		Assertions.assertTrue(this.service.create(creation("PurgeDatabaseTask").days(30).group(this.groupId).build()) != null);
		Assertions.assertTrue(this.service.create(creation("PurgeDatabaseTask").days(30).deviceList(List.of(this.deviceId)).build()) != null);
	}

	@Test
	@DisplayName("create() checks the schedule and the priority")
	void createSchedule() {
		Date future = new Date(System.currentTimeMillis() + 3_600_000L);
		Date past = new Date(System.currentTimeMillis() - 3_600_000L);
		assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(
			creation("TakeSnapshotTask").device(this.deviceId).schedule(Task.ScheduleType.AT, past, 1).build()));
		assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(
			creation("TakeSnapshotTask").device(this.deviceId).schedule(Task.ScheduleType.DAILY, future, 0).build()));
		assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(creation("TakeSnapshotTask").device(this.deviceId).priority(0).build()));
		assertError(ErrorCode.NETSHOT_INVALID_TASK, () -> this.service.create(creation("TakeSnapshotTask").device(this.deviceId).priority(10).build()));
		Assertions.assertTrue(this.submittedTasks.isEmpty());
		Task daily = this.service.create(creation("TakeSnapshotTask").device(this.deviceId).schedule(Task.ScheduleType.DAILY, future, 2).build());
		Assertions.assertEquals(Task.ScheduleType.DAILY, daily.scheduleType());
		Assertions.assertEquals(2, daily.scheduleFactor());
		// Without schedule reference, the schedule is not checked at all
		Task asap = this.service.create(creation("TakeSnapshotTask").device(this.deviceId).schedule(Task.ScheduleType.AT, null, -1).build());
		Assertions.assertEquals(Task.ScheduleType.ASAP, asap.scheduleType());
	}

	@Test
	@DisplayName("create() reports a scheduler failure")
	void createSchedulerFailure() {
		this.schedulerFailure = new SchedulerException("no scheduler");
		assertError(ErrorCode.NETSHOT_SCHEDULE_ERROR, () -> this.service.create(creation("TakeSnapshotTask").device(this.deviceId).build()));
	}

	/** Builds task creation commands, with the defaults the REST request used to have. */
	private static final class CreationBuilder {
		private final String type;
		private Long device = 0L;
		private Long group = 0L;
		private List<Long> deviceList;
		private Task.ScheduleMode scheduleMode = Task.ScheduleMode.PARALLEL;
		private boolean stopOnFailure;
		private Long domain = 0L;
		private String subnets = "";
		private Date scheduleReference = new Date();
		private Task.ScheduleType scheduleType = Task.ScheduleType.ASAP;
		private int scheduleFactor = 1;
		private int priority = 5;
		private String comments = "";
		private int limitHours = -1;
		private int days = 90;
		private int configDays = -1;
		private int configSize;
		private int configKeepDays;
		private int moduleDays = -1;
		private boolean removeOrphanFiles;
		private String script = "";
		private String driver;
		private Map<String, String> userInputs;
		private boolean dontRunDiagnostics;
		private boolean runSnapshot;

		CreationBuilder(String type) {
			this.type = type;
		}

		CreationBuilder device(long value) {
			this.device = value;
			return this;
		}

		CreationBuilder group(long value) {
			this.group = value;
			return this;
		}

		CreationBuilder deviceList(List<Long> value) {
			this.deviceList = value;
			return this;
		}

		CreationBuilder scheduleMode(Task.ScheduleMode value) {
			this.scheduleMode = value;
			return this;
		}

		CreationBuilder stopOnFailure(boolean value) {
			this.stopOnFailure = value;
			return this;
		}

		CreationBuilder domain(long value) {
			this.domain = value;
			return this;
		}

		CreationBuilder subnets(String value) {
			this.subnets = value;
			return this;
		}

		CreationBuilder schedule(Task.ScheduleType scheduleKind, Date reference, int factor) {
			this.scheduleType = scheduleKind;
			this.scheduleReference = reference;
			this.scheduleFactor = factor;
			return this;
		}

		CreationBuilder priority(int value) {
			this.priority = value;
			return this;
		}

		CreationBuilder comments(String value) {
			this.comments = value;
			return this;
		}

		CreationBuilder limitHours(int value) {
			this.limitHours = value;
			return this;
		}

		CreationBuilder days(int value) {
			this.days = value;
			return this;
		}

		CreationBuilder configDays(int value) {
			this.configDays = value;
			return this;
		}

		CreationBuilder configSize(int value) {
			this.configSize = value;
			return this;
		}

		CreationBuilder configKeepDays(int value) {
			this.configKeepDays = value;
			return this;
		}

		CreationBuilder moduleDays(int value) {
			this.moduleDays = value;
			return this;
		}

		CreationBuilder removeOrphanFiles(boolean value) {
			this.removeOrphanFiles = value;
			return this;
		}

		CreationBuilder script(String value) {
			this.script = value;
			return this;
		}

		CreationBuilder driver(String value) {
			this.driver = value;
			return this;
		}

		CreationBuilder userInputs(Map<String, String> value) {
			this.userInputs = value;
			return this;
		}

		CreationBuilder dontRunDiagnostics(boolean value) {
			this.dontRunDiagnostics = value;
			return this;
		}

		CreationBuilder runSnapshot(boolean value) {
			this.runSnapshot = value;
			return this;
		}

		TaskCreation build() {
			return new TaskCreation(AUTHOR, this.type, this.device, this.group, this.deviceList, this.scheduleMode,
				this.stopOnFailure, this.domain, this.subnets, this.scheduleReference, this.scheduleType, this.scheduleFactor,
				this.priority, this.comments, false, this.limitHours, this.days, this.configDays, this.configSize,
				this.configKeepDays, this.moduleDays, this.removeOrphanFiles, this.script, this.driver, this.userInputs,
				this.dontRunDiagnostics, false, this.runSnapshot, false, false);
		}
	}
}
