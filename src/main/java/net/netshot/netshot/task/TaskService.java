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
package net.netshot.netshot.task;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.domain.DomainRepository;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.task.Task.ScheduleType;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;
import net.netshot.netshot.task.type.ChildOrchestratingTask;
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
import net.netshot.netshot.userscript.DeviceJsScript;

/**
 * The task use cases: list, read, cancel, create, and the statistics.
 *
 * <p>Running the tasks is the job of the task engine ({@link TaskHandlers}), which this service
 * only reaches through the {@link TaskSubmitter} and {@link TaskEngine} ports.</p>
 */
@Slf4j
public class TaskService {

	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/** The number of items returned by the lists when the caller gives no limit. */
	public static final int DEFAULT_LIMIT = 100;

	// The final (completed) task statuses, i.e. the ones shown on the tasks time-based statistics.
	private static final Set<Task.Status> FINAL_STATUSES =
		Set.of(Task.Status.SUCCESS, Task.Status.FAILURE, Task.Status.CANCELLED);

	private static final String SUBNET_SEPARATORS = "(\r\n|\n|;| |,)";
	private static final Pattern SUBNET_PATTERN = Pattern.compile("^(?<ip>[0-9\\.]+)(/(?<mask>[0-9]+))?$");

	private final Transactions transactions;
	private final TaskRepository repository;
	private final TaskSubmitter taskSubmitter;
	private final TaskEngine taskEngine;
	private final DriverRegistry driverRegistry;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the task queries
	 * @param taskSubmitter how to hand a new task over to the task engine
	 * @param taskEngine how to cancel tasks and to know the size of the task engine
	 * @param driverRegistry the loaded device drivers
	 */
	public TaskService(Transactions transactions, TaskRepository repository, TaskSubmitter taskSubmitter, TaskEngine taskEngine,
		DriverRegistry driverRegistry) {
		this.transactions = transactions;
		this.repository = repository;
		this.taskSubmitter = taskSubmitter;
		this.taskEngine = taskEngine;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Lists the tasks in their summary form.
	 *
	 * @param filter the criteria
	 * @param page the window on the result; the limit defaults to {@link #DEFAULT_LIMIT}
	 * @return the task summaries
	 * @throws NetshotException if the tasks can't be read
	 */
	public List<TaskSummary> list(TaskFilter filter, Page page) {
		Page effective = page.limit() == null ? new Page(page.offset(), DEFAULT_LIMIT) : page;
		try {
			return this.transactions.read(dsl -> this.repository.findSummaries(dsl, filter, effective));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the tasks.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the tasks");
		}
	}

	/**
	 * Lists the tasks of a device in their summary form.
	 *
	 * <p>As the former endpoint did, any failure, including an invalid window, is reported as
	 * a database access error.</p>
	 *
	 * @param deviceId the device ID
	 * @param offset the number of tasks to skip, or null
	 * @param limit the maximum number of tasks to return, or null for {@link #DEFAULT_LIMIT}
	 * @return the task summaries
	 * @throws NetshotException if the tasks can't be read
	 */
	public List<TaskSummary> listByDevice(long deviceId, Integer offset, Integer limit) {
		try {
			Page page = new Page(offset, limit == null ? DEFAULT_LIMIT : limit);
			return this.transactions.read(dsl -> this.repository.findSummariesByDevice(dsl, deviceId, page));
		}
		catch (Exception e) {
			log.error("Unable to fetch the tasks.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the tasks");
		}
	}

	/**
	 * Finds a task (see {@link TaskRepository#find} for what is loaded).
	 *
	 * @param id the task ID
	 * @return the detached task, or null if there is no such task
	 * @throws NetshotException if the task can't be read
	 */
	public Task find(long id) {
		try {
			return this.transactions.read(dsl -> this.repository.find(dsl, id));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the task {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the task");
		}
	}

	/**
	 * Maps a task to another representation, in a read transaction, giving the mapper access
	 * to the devices of the task (loaded on demand, in the same transaction).
	 * The mapper must not keep the devices.
	 *
	 * @param <R> the type of the result
	 * @param id the task ID
	 * @param mapper the function mapping the task
	 * @return the result of the mapper
	 * @throws NetshotException if the task doesn't exist or can't be read
	 */
	public <R> R mapTask(long id, TaskMapping<R> mapper) {
		try {
			return this.transactions.read(dsl -> {
				Task task = this.repository.find(dsl, id);
				if (task == null) {
					log.error("Unable to find the task {}.", id);
					throw new NetshotException(ErrorCode.NETSHOT_TASK_NOT_FOUND, "Task not found");
				}
				return mapper.map(task, deviceId -> deviceId == null ? null : new DeviceRepository().find(dsl, deviceId));
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the task {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to get the task");
		}
	}

	/**
	 * Maps a task, with access to its devices.
	 *
	 * @param <R> the type of the result
	 */
	@FunctionalInterface
	public interface TaskMapping<R> {
		/**
		 * Maps a task.
		 *
		 * @param task the task
		 * @param devices gets a device by ID (null for a null ID or a missing device)
		 * @return the result
		 */
		R map(Task task, Function<Long, Device> devices);
	}

	/**
	 * Gets the debug log of a task.
	 *
	 * @param id the task ID
	 * @return the text of the debug log, empty if the task has none
	 * @throws NetshotException if the task doesn't exist or can't be read
	 */
	public String getDebugLog(long id) {
		try {
			return this.transactions.read(dsl -> {
				Task task = this.repository.find(dsl, id);
				if (task == null) {
					log.error("Unable to find the task {}.", id);
					throw new NetshotException(ErrorCode.NETSHOT_TASK_NOT_FOUND, "Task not found");
				}
				String debugLog = this.repository.findDebugLog(dsl, id);
				return debugLog == null ? "" : debugLog;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the task {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to get the task");
		}
	}

	/**
	 * Edits a task. The only change is the cancellation of the task.
	 *
	 * @param id the task ID
	 * @param cancel true to cancel the task
	 * @param author the user cancelling the task
	 * @return the task, as it is after the change (detached)
	 * @throws NetshotException if the task doesn't exist, can't be cancelled or can't be read
	 */
	public Task update(long id, boolean cancel, String author) {
		Task task;
		try {
			task = this.transactions.write(dsl -> this.repository.find(dsl, id));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the task {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the task.");
		}
		if (task == null) {
			log.error("Unable to find the task {}.", id);
			throw new NetshotException(ErrorCode.NETSHOT_TASK_NOT_FOUND, "Unable to find the task.");
		}
		if (cancel) {
			this.cancel(task, author);
			try {
				task = this.transactions.read(dsl -> this.repository.find(dsl, id));
			}
			catch (DataAccessException e) {
				log.error("Unable to fetch the task {}.", id, e);
				throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the task.");
			}
		}
		return task;
	}

	private void cancel(Task task, String author) {
		long id = task.id();
		// A group-based task that orchestrates per-device children (either schedule mode)
		// spends its whole run in RUNNING, so the normal SCHEDULED-only cancel path doesn't
		// apply -- it's cancelled by flagging cancelRequested, polled by its own
		// orchestration loop, rather than by pulling it out of the Quartz scheduler.
		boolean runningOrchestrator = task.status() == Task.Status.RUNNING
			&& task.spec() instanceof ChildOrchestratingTask;
		// Same for a running device task, which may be waiting for its turn on the device
		// (the request is only honored while waiting, not once the device session started)
		boolean runningDeviceTask = task.status() == Task.Status.RUNNING
			&& task.deviceLockPolicy() != Task.DeviceLockPolicy.NONE;
		if (task.status() != Task.Status.SCHEDULED && !runningOrchestrator && !runningDeviceTask) {
			log.error("User is trying to cancel task {} not in SCHEDULED state (or RUNNING group task).", id);
			throw new NetshotException(ErrorCode.NETSHOT_TASK_NOT_CANCELLABLE, "The task isn't in 'SCHEDULED' state.");
		}
		try {
			if (runningOrchestrator) {
				this.taskEngine.requestCancel(id);
				AAA_LOG.info("{} has been requested to cancel (running group task).", task);
			}
			else if (runningDeviceTask) {
				this.taskEngine.requestCancel(id);
				AAA_LOG.info("{} has been requested to cancel (running device task, if still waiting for the device).", task);
			}
			else {
				String message = "Task manually cancelled by user %s.".formatted(author);
				this.taskEngine.cancel(task, message);
				AAA_LOG.info("{} has been manually cancelled.", task);
			}
		}
		catch (Exception e) {
			log.error("Unable to cancel the task {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_TASK_CANCEL_ERROR, "Cannot cancel the task.");
		}
	}

	/**
	 * Gets global info about the tasks: the number of tasks of every status.
	 *
	 * @param parentTaskId the ID of a parent task whose children are summarized, or null for all the tasks
	 * @return the overview
	 * @throws NetshotException if the tasks can't be read
	 */
	public TaskOverview getOverview(Long parentTaskId) {
		try {
			List<TaskStatusCount> counts = this.transactions.read(dsl -> this.repository.countByStatus(dsl, parentTaskId));
			Map<Task.Status, Long> countByStatus = new HashMap<>();
			for (Task.Status status : Task.Status.values()) {
				countByStatus.put(status, 0L);
			}
			for (TaskStatusCount count : counts) {
				countByStatus.put(count.status(), count.count());
			}
			return new TaskOverview(countByStatus, this.taskEngine.threadCount());
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the task counts", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the task counts");
		}
	}

	/**
	 * Gets time-based statistics (histogram) about the completed tasks over a date range.
	 *
	 * @param after the start of the range in milliseconds since 1970, or null for the epoch
	 * @param before the end of the range in milliseconds since 1970, or null for now
	 * @param buckets the number of time buckets to split the range into (at most 366), or null for 48
	 * @return the statistics
	 * @throws NetshotException if the tasks can't be read
	 */
	public TaskStats getStats(Long after, Long before, Integer buckets) {
		long from = after == null ? 0L : after;
		long to = before == null ? System.currentTimeMillis() : before;
		int binCount = buckets == null ? 48 : Math.max(1, Math.min(366, buckets));
		long span = Math.max(1L, to - from);
		double step = ((double) span) / binCount;
		try {
			return this.transactions.read(dsl -> {
				Map<Task.Status, Long> statusCounts = new HashMap<>();
				for (Task.Status status : FINAL_STATUSES) {
					statusCounts.put(status, 0L);
				}
				List<TaskStats.Bin> bins = new ArrayList<>();
				for (int i = 0; i < binCount; i++) {
					Instant bucketStart = Instant.ofEpochMilli(from + Math.round(i * step));
					Instant bucketEnd = Instant.ofEpochMilli(i == binCount - 1 ? to : from + Math.round((i + 1) * step));
					Map<Task.Status, Long> countByStatus = new HashMap<>();
					for (Task.Status status : FINAL_STATUSES) {
						countByStatus.put(status, 0L);
					}
					for (TaskStatusCount count : this.repository.countByStatus(dsl, FINAL_STATUSES, bucketStart, bucketEnd)) {
						countByStatus.put(count.status(), count.count());
						statusCounts.merge(count.status(), count.count(), Long::sum);
					}
					bins.add(new TaskStats.Bin(Date.from(bucketStart), Date.from(bucketEnd), countByStatus));
				}
				Instant rangeStart = Instant.ofEpochMilli(from);
				Instant rangeEnd = Instant.ofEpochMilli(to);
				Map<String, Long> typeCounts = new HashMap<>();
				for (String typeName : TaskRepository.TYPE_NAMES) {
					typeCounts.put(typeName, this.repository.countByType(dsl, typeName, FINAL_STATUSES, rangeStart, rangeEnd));
				}
				return new TaskStats(bins, statusCounts, typeCounts);
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the task stats", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the task stats");
		}
	}

	// ------------------------------------------------------------------
	// Creation
	// ------------------------------------------------------------------

	/**
	 * Creates a task and schedules it for execution.
	 *
	 * @param creation the task to create
	 * @return the created task
	 * @throws NetshotException if the command is invalid, a target doesn't exist,
	 *         or the task can't be stored or scheduled
	 */
	public Task create(TaskCreation creation) {
		log.debug("Adding a task.");
		Task task = this.build(creation);
		if (creation.scheduleReference() != null) {
			task = task.toBuilder()
				.debugEnabled(creation.debugEnabled())
				.scheduleReference(creation.scheduleReference().toInstant())
				.scheduleType(creation.scheduleType())
				.scheduleFactor(creation.scheduleFactor())
				.build();
			if (task.scheduleType() == ScheduleType.AT) {
				Calendar inOneMinute = Calendar.getInstance();
				inOneMinute.add(Calendar.MINUTE, 1);
				if (creation.scheduleReference().before(inOneMinute.getTime())) {
					log.error("The schedule for the task occurs in less than one minute ({} vs {}).",
						creation.scheduleReference(), inOneMinute.getTime());
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_TASK, "The schedule occurs in the past.");
				}
			}
			else if (task.scheduleFactor() < 1) {
				log.error("The repeating factor {} is invalid.", task.scheduleFactor());
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_TASK, "The repeating factor is invalid.");
			}
		}
		if (creation.schedulePriority() < 1 || creation.schedulePriority() > 9) {
			log.error("Invalid priority {} for the task", creation.schedulePriority());
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_TASK, "Invalid priority value.");
		}
		task = task.toBuilder().priority(creation.schedulePriority()).build();
		try {
			task = this.taskSubmitter.submit(task);
		}
		catch (DataAccessException e) {
			log.error("Unable to add the task.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the task to the database.");
		}
		catch (SchedulerException e) {
			log.error("Unable to schedule the task.", e);
			throw new NetshotException(ErrorCode.NETSHOT_SCHEDULE_ERROR, "Unable to schedule the task.");
		}
		AAA_LOG.info("The task {} has been created.", task);
		return task;
	}

	// Builds the task of the requested type, from its specific components.
	private Task build(TaskCreation c) {
		String type = c.type();
		if ("TakeSnapshotTask".equals(type)) {
			Device device = this.loadDevice(c.device());
			return TakeSnapshotTask.of(device, c.comments(), c.author(), false, c.dontRunDiagnostics(), c.dontCheckCompliance());
		}
		else if ("RunDeviceScriptTask".equals(type)) {
			DeviceDriver driver = this.validateScript(c);
			Device device = this.loadDevice(c.device());
			this.validateUserInputs(c);
			return RunDeviceScriptTask.of(device, new RunDeviceScriptTask(null, c.script(), driver.getName(), c.userInputs(),
				c.runSnapshot(), c.runDiagnostics(), c.checkCompliance()), c.comments(), c.author());
		}
		else if ("RunDeviceGroupScriptTask".equals(type)) {
			DeviceDriver driver = this.validateScript(c);
			this.validateUserInputs(c);
			return this.buildGroupTask(c, (groupId, deviceIds) -> new RunDeviceGroupScriptTask(groupId, deviceIds, c.script(),
				driver.getName(), c.userInputs(), c.runSnapshot(), c.runDiagnostics(), c.checkCompliance()), true);
		}
		else if ("CheckComplianceTask".equals(type)) {
			Device device = this.loadDevice(c.device());
			return CheckComplianceTask.of(device, c.comments(), c.author());
		}
		else if ("TakeGroupSnapshotTask".equals(type)) {
			return this.buildGroupTask(c, (groupId, deviceIds) -> new TakeGroupSnapshotTask(groupId, deviceIds,
				c.limitToOutofdateDeviceHours(), c.dontRunDiagnostics(), c.dontCheckCompliance()), true);
		}
		else if ("CheckGroupComplianceTask".equals(type)) {
			return this.buildGroupTask(c, CheckGroupComplianceTask::new, false);
		}
		else if ("CheckGroupSoftwareTask".equals(type)) {
			return this.buildGroupTask(c, CheckGroupSoftwareTask::new, false);
		}
		else if ("RunGroupDiagnosticsTask".equals(type)) {
			return this.buildGroupTask(c, (groupId, deviceIds) -> new RunGroupDiagnosticsTask(groupId, deviceIds,
				c.dontCheckCompliance()), true);
		}
		else if ("ScanSubnetsTask".equals(type)) {
			return this.buildScanSubnetsTask(c);
		}
		else if ("PurgeDatabaseTask".equals(type)) {
			return this.buildPurgeDatabaseTask(c);
		}
		else if ("RunDiagnosticsTask".equals(type)) {
			Device device = this.loadDevice(c.device());
			return RunDiagnosticsTask.of(device, c.comments(), c.author(), c.dontCheckCompliance());
		}
		log.error("User posted an invalid task type '{}'.", type);
		throw new NetshotException(ErrorCode.NETSHOT_INVALID_TASK, "Invalid task type.");
	}

	// Builds the specification of a task targeting either a group or a device list.
	private interface GroupTaskFactory<S extends TaskSpec & GroupBasedTask> {
		S build(Long groupId, List<Long> deviceIds);
	}

	// Builds a task targeting either a group or a device list (with the schedule mode of the children, if any).
	private <S extends TaskSpec & GroupBasedTask> Task buildGroupTask(TaskCreation c, GroupTaskFactory<S> factory, boolean withChildren) {
		validateGroupOrDeviceList(c);
		Task task = this.inTransaction(dsl -> {
			if (hasGroup(c)) {
				DeviceGroup group = this.loadGroup(dsl, c.group());
				return GroupBasedTask.of(factory.build(group.id(), List.of()), group.name(), c.comments(), c.author());
			}
			return GroupBasedTask.of(factory.build(null, this.resolveDeviceList(dsl, c)), null, c.comments(), c.author());
		});
		if (withChildren) {
			task = task.toBuilder().scheduleMode(c.scheduleMode()).stopOnFailure(c.stopOnFailure()).build();
		}
		return task;
	}

	private Task buildScanSubnetsTask(TaskCreation c) {
		log.trace("Adding a ScanSubnetsTask");
		Set<Network4Address> subnets = new HashSet<>();
		String[] rsSubnets = c.subnets().split(SUBNET_SEPARATORS);
		for (String rsSubnet : rsSubnets) {
			Matcher matcher = SUBNET_PATTERN.matcher(rsSubnet);
			if (!matcher.find()) {
				log.warn("User posted an invalid subnet '{}'.", rsSubnet);
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_SUBNET, String.format("Invalid subnet '%s'.", rsSubnet));
			}
			Network4Address subnet;
			try {
				int mask = 32;
				if (matcher.group("mask") != null) {
					mask = Integer.parseInt(matcher.group("mask"));
				}
				subnet = new Network4Address(matcher.group("ip"), mask);
				subnets.add(subnet);
			}
			catch (Exception e) {
				log.warn("User posted an invalid subnet '{}'.", rsSubnet, e);
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_SUBNET, String.format("Invalid subnet '%s'.", rsSubnet));
			}
			if (subnet.getPrefixLength() < 22 || subnet.getPrefixLength() > 32) {
				log.warn("User posted an invalid prefix length {}.", subnet.getPrefix());
				throw new NetshotException(ErrorCode.NETSHOT_SCAN_SUBNET_TOO_BIG, String.format("Invalid prefix length for '%s'.", rsSubnet));
			}
		}
		if (subnets.isEmpty()) {
			log.warn("User posted an invalid subnet list '{}'.", c.subnets());
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_SUBNET, String.format("Invalid subnet list '%s'.", c.subnets()));
		}
		if (c.domain() == 0) {
			log.error("Domain {} is invalid (0).", c.domain());
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DOMAIN, "Invalid domain");
		}
		Domain domain;
		try {
			domain = this.transactions.write(dsl -> {
				Domain found = new DomainRepository().find(dsl, c.domain());
				if (found == null) {
					log.error("No such domain of ID {}.", c.domain());
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_DOMAIN, "Invalid domain");
				}
				return found;
			});
		}
		catch (Exception e) {
			log.error("Unable to load the domain {}.", c.domain());
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DOMAIN, "Invalid domain");
		}
		StringBuilder target = new StringBuilder();
		target.append("{");
		for (Network4Address subnet : subnets) {
			if (target.length() > 1) {
				target.append(", ");
			}
			target.append(subnet.getPrefix());
		}
		target.append("}");
		return ScanSubnetsTask.of(subnets, domain.id(), c.comments(), target.toString(), c.author());
	}

	private Task buildPurgeDatabaseTask(TaskCreation c) {
		log.trace("Adding a PurgeDatabaseTask");
		if (c.daysToPurge() < 2) {
			log.error("Invalid number of days {} for the PurgeDatabaseTask task.", c.daysToPurge());
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_TASK, "Invalid number of days.");
		}
		int configDays = c.configDaysToPurge();
		int configSize = c.configSizeToPurge();
		int configKeepDays = c.configKeepDays();
		if (configDays == -1) {
			configSize = 0;
			configKeepDays = 0;
		}
		else if (configDays <= 3) {
			log.error("The number of days of configurations to purge must be greater than 3.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_TASK, "The number of days of configurations to purge must be greater than 3.");
		}
		else {
			if (configSize < 0) {
				log.error("The configuration size limit can't be negative.");
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_TASK, "The limit on the configuration size can't be negative.");
			}
			if (configKeepDays < 0) {
				log.error("The interval of days between configurations to keep can't be negative.");
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_TASK, "The number of days of configurations to purge can't be negative.");
			}
			if (configDays <= configKeepDays) {
				log.error("The number of days of configurations to purge must be greater than the number of days between two successive configurations to keep.");
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_TASK,
					"The number of days of configurations to purge must be greater than the number of days between two successive configurations to keep.");
			}
		}
		int moduleDays = c.moduleDaysToPurge();
		if (moduleDays != -1 && moduleDays < 3) {
			log.error("The number of days of removed modules to purge must be greater than 3.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_TASK, "The number of days of removed modules to purge must be greater than 3.");
		}
		if (hasGroup(c) && hasDeviceList(c)) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_TASK, "Only one of 'group' or 'deviceList' may be provided.");
		}
		final int finalConfigSize = configSize;
		final int finalConfigKeepDays = configKeepDays;
		PurgeDatabaseTask.PurgeDatabaseTaskBuilder spec = PurgeDatabaseTask.builder()
			.days(c.daysToPurge())
			.configDays(configDays)
			.configSize(finalConfigSize)
			.configKeepDays(finalConfigKeepDays)
			.moduleDays(moduleDays)
			.removeOrphanFiles(c.removeOrphanFiles());
		if (hasGroup(c)) {
			spec.deviceGroupId(this.inTransaction(dsl -> this.loadGroup(dsl, c.group()).id()));
		}
		else if (hasDeviceList(c)) {
			// No translation of the database errors here, as in the former code
			spec.deviceIds(this.transactions.write(dsl -> this.resolveDeviceList(dsl, c)));
		}
		Task task = Task.of(spec.build(), c.comments(), "Global", c.author());
		return task;
	}

	// Checks the driver and the script of a script task, and gets the driver.
	private DeviceDriver validateScript(TaskCreation c) {
		DeviceDriver driver = this.driverRegistry.getDriverByName(c.driver());
		if (driver == null) {
			log.error("Unknown device driver {}.", c.type());
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DEVICE, "Unknown device driver.");
		}
		if (c.script() == null) {
			log.error("The script can't be empty.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DEVICE, "The script can't be empty.");
		}
		return driver;
	}

	// Checks the user inputs against the declarations of the script.
	private void validateUserInputs(TaskCreation c) {
		try {
			DeviceJsScript jsScript = DeviceJsScript.of("__toValidate", c.driver(), c.script(), c.author());
			jsScript.validateUserInputs(this.driverRegistry, c.userInputs());
		}
		catch (IllegalArgumentException e) {
			log.warn("Invalid script.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_SCRIPT, e.getMessage());
		}
	}

	// Loads the target device of a device-based task.
	private Device loadDevice(Long deviceId) {
		return this.inTransaction(dsl -> {
			Device device = new DeviceRepository().find(dsl, deviceId);
			if (device == null) {
				log.error("Unable to find the device {}.", deviceId);
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_DEVICE, "Unable to find the device.");
			}
			return device;
		});
	}

	private DeviceGroup loadGroup(DSLContext dsl, Long groupId) {
		DeviceGroup group = new GroupRepository().find(dsl, groupId);
		if (group == null) {
			log.error("Unable to find the group {}.", groupId);
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_GROUP, "Unable to find the group.");
		}
		return group;
	}

	// Resolves the ordered list of devices of a one-time device list task.
	private List<Long> resolveDeviceList(DSLContext dsl, TaskCreation c) {
		List<Long> devices = new ArrayList<>();
		for (Long deviceId : c.deviceList()) {
			Device device = new DeviceRepository().find(dsl, deviceId);
			if (device == null) {
				log.error("Unable to find the device {}.", deviceId);
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_DEVICE, String.format("Unable to find device %d.", deviceId));
			}
			devices.add(device.id());
		}
		return devices;
	}

	// Runs some work in a read-write transaction, reporting a database failure as such.
	private <T> T inTransaction(Function<DSLContext, T> work) {
		try {
			return this.transactions.write(work);
		}
		catch (DataAccessException e) {
			log.error("Error while retrieving the target of the task.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Database error.");
		}
	}

	private static boolean hasGroup(TaskCreation c) {
		return c.group() != null && c.group() != 0L;
	}

	private static boolean hasDeviceList(TaskCreation c) {
		return c.deviceList() != null && !c.deviceList().isEmpty();
	}

	// Validates that exactly one of a device group or a one-time device list was given.
	private static void validateGroupOrDeviceList(TaskCreation c) {
		if (hasGroup(c) == hasDeviceList(c)) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_TASK, "Exactly one of 'group' or 'deviceList' must be provided.");
		}
	}
}
