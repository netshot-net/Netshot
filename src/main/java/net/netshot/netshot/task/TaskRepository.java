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

import static net.netshot.netshot.platform.database.schema.Tables.DEBUG_LOG;
import static net.netshot.netshot.platform.database.schema.Tables.DISCOVER_DEVICE_TYPE_TASK_CREDENTIAL_SETS;
import static net.netshot.netshot.platform.database.schema.Tables.SCAN_SUBNETS_TASK_SUBNETS;
import static net.netshot.netshot.platform.database.schema.Tables.TASK;
import static net.netshot.netshot.platform.database.schema.Tables.TASK_DEVICE_LIST_MEMBERS;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.QueryMapping;
import net.netshot.netshot.platform.database.QueryMapping.Option;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.SortOrder;
import net.netshot.netshot.platform.database.schema.tables.records.TaskRecord;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.NetworkAddress.AddressUsage;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;
import net.netshot.netshot.task.type.DeviceBasedTask;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.DomainBasedTask;
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

/**
 * The queries on the tasks. The {@link DSLContext} is always the first parameter:
 * the caller owns the transaction.
 *
 * <p>The type of a task is stored as the name of its specification class ({@code dtype}).
 * The parameters which have no column of their own are stored as JSON attributes, with the
 * results of the runs.</p>
 */
public class TaskRepository {

	/** The names of the types of task. */
	public static final Set<String> TYPE_NAMES;

	static {
		Set<String> names = new HashSet<>();
		for (Class<?> specClass : TaskSpec.class.getPermittedSubclasses()) {
			names.add(specClass.getSimpleName());
		}
		TYPE_NAMES = Set.copyOf(names);
	}

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final TypeReference<Map<String, Object>> ATTRIBUTES_TYPE = new TypeReference<>() {
	};

	private static final TypeReference<Map<String, String>> USER_INPUTS_TYPE = new TypeReference<>() {
	};

	/** The IDs of the devices of the device list, in order. */
	private static final Field<List<Long>> DEVICE_LIST = DSL.multiset(
			DSL.select(TASK_DEVICE_LIST_MEMBERS.DEVICE_ID).from(TASK_DEVICE_LIST_MEMBERS)
				.where(TASK_DEVICE_LIST_MEMBERS.TASK_ID.eq(TASK.ID)).orderBy(TASK_DEVICE_LIST_MEMBERS.LIST_POSITION))
		.convertFrom(r -> (List<Long>) new ArrayList<>(r.map(t -> t.value1())))
		.as("device_list");

	/** The IDs of the credential sets (discovery). */
	private static final Field<Set<Long>> CREDENTIAL_SETS = DSL.multiset(
			DSL.select(DISCOVER_DEVICE_TYPE_TASK_CREDENTIAL_SETS.CREDENTIAL_SETS).from(DISCOVER_DEVICE_TYPE_TASK_CREDENTIAL_SETS)
				.where(DISCOVER_DEVICE_TYPE_TASK_CREDENTIAL_SETS.DISCOVER_DEVICE_TYPE_TASK.eq(TASK.ID)))
		.convertFrom(r -> (Set<Long>) new HashSet<>(r.map(t -> t.value1())))
		.as("credential_sets");

	/** The subnets (scan). */
	private static final Field<Set<Network4Address>> SUBNETS = DSL.multiset(
			DSL.select(SCAN_SUBNETS_TASK_SUBNETS.IPV4ADDRESS, SCAN_SUBNETS_TASK_SUBNETS.IPV4MASK, SCAN_SUBNETS_TASK_SUBNETS.IPV4USAGE)
				.from(SCAN_SUBNETS_TASK_SUBNETS).where(SCAN_SUBNETS_TASK_SUBNETS.SCAN_SUBNETS_TASK.eq(TASK.ID)))
		.convertFrom(r -> (Set<Network4Address>) new HashSet<>(r.map(t -> toSubnet(t.value1(), t.value2(), t.value3()))))
		.as("subnets");

	private static final List<SelectField<?>> FIELDS;

	static {
		List<SelectField<?>> fields = new ArrayList<>(List.of(TASK.fields()));
		fields.add(DEVICE_LIST);
		fields.add(CREDENTIAL_SETS);
		fields.add(SUBNETS);
		FIELDS = List.copyOf(fields);
	}

	private static Network4Address toSubnet(java.net.InetAddress address, Integer mask, String usage) {
		try {
			Network4Address subnet = new Network4Address((Inet4Address) address, mask == null ? 32 : mask);
			if (usage != null) {
				subnet.setAddressUsage(AddressUsage.valueOf(usage));
			}
			return subnet;
		}
		catch (UnknownHostException e) {
			throw new IllegalStateException("Invalid stored subnet", e);
		}
	}

	/**
	 * Gets the current date, as stored in the change dates (truncated to the second).
	 *
	 * @return the current date
	 */
	static Instant changeDateNow() {
		return Instant.now().truncatedTo(ChronoUnit.SECONDS);
	}

	private static Map<String, Object> readAttributes(JSONB json) {
		if (json == null) {
			return new HashMap<>();
		}
		try {
			Map<String, Object> attributes = JSON.readValue(json.data(), ATTRIBUTES_TYPE);
			return attributes == null ? new HashMap<>() : attributes;
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid stored task attributes", e);
		}
	}

	private static Map<String, String> readUserInputs(JSONB json) {
		if (json == null) {
			return null;
		}
		try {
			return JSON.readValue(json.data(), USER_INPUTS_TYPE);
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid stored task user inputs", e);
		}
	}

	private static JSONB toJson(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return JSONB.valueOf(JSON.writeValueAsString(value));
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Unable to serialize the task data", e);
		}
	}

	private static int intAttribute(Map<String, Object> attributes, String key, int defaultValue) {
		return attributes.get(key) instanceof Number number ? number.intValue() : defaultValue;
	}

	private static long longAttribute(Map<String, Object> attributes, String key, long defaultValue) {
		return attributes.get(key) instanceof Number number ? number.longValue() : defaultValue;
	}

	private static boolean booleanAttribute(Map<String, Object> attributes, String key, boolean defaultValue) {
		return attributes.get(key) instanceof Boolean bool ? bool : defaultValue;
	}

	private static String stringAttribute(Map<String, Object> attributes, String key) {
		return attributes.get(key) instanceof String string ? string : null;
	}

	private static List<String> stringListAttribute(Map<String, Object> attributes, String key) {
		List<String> values = new ArrayList<>();
		if (attributes.get(key) instanceof List<?> list) {
			for (Object item : list) {
				if (item instanceof String value) {
					values.add(value);
				}
			}
		}
		return values;
	}

	private static TaskSpec toSpec(Record r) {
		TaskRecord t = r.into(TASK);
		Map<String, Object> a = readAttributes(t.getAttributes());
		Long groupId = t.getDeviceGroup();
		List<Long> deviceIds = r.get(DEVICE_LIST);
		return switch (t.getDtype()) {
			case "TakeSnapshotTask" -> new TakeSnapshotTask(t.getDevice(), booleanAttribute(a, "automatic", false),
				t.getDontRunDiagnostics(), t.getDontCheckCompliance());
			case "RunDiagnosticsTask" -> new RunDiagnosticsTask(t.getDevice(), t.getDontCheckCompliance());
			case "CheckComplianceTask" -> new CheckComplianceTask(t.getDevice());
			case "RunDeviceScriptTask" -> new RunDeviceScriptTask(t.getDevice(), t.getScript(), t.getDeviceDriver(),
				readUserInputs(t.getUserInputValues()), t.getRunSnapshot(), t.getRunDiagnostics(), t.getCheckCompliance());
			case "DiscoverDeviceTypeTask" -> new DiscoverDeviceTypeTask(t.getDeviceAddress(), t.getDomain(), r.get(CREDENTIAL_SETS),
				stringAttribute(a, "deviceComments"), stringListAttribute(a, "deviceTags"),
				stringAttribute(a, DiscoverDeviceTypeTask.DISCOVERED_DEVICE_TYPE), longAttribute(a, DiscoverDeviceTypeTask.SNAPSHOT_TASK_ID, 0),
				t.getDiscoverResultDevice());
			case "ScanSubnetsTask" -> new ScanSubnetsTask(t.getDomain(), r.get(SUBNETS));
			case "PurgeDatabaseTask" -> new PurgeDatabaseTask(groupId, deviceIds, intAttribute(a, "days", 0),
				intAttribute(a, "configDays", -1), intAttribute(a, "configSize", 0), intAttribute(a, "configKeepDays", 0),
				intAttribute(a, "moduleDays", -1), booleanAttribute(a, "removeOrphanFiles", false));
			case "TakeGroupSnapshotTask" -> new TakeGroupSnapshotTask(groupId, deviceIds, intAttribute(a, "limitToOutofdateDeviceHours", -1),
				t.getDontRunDiagnostics(), t.getDontCheckCompliance());
			case "CheckGroupComplianceTask" -> new CheckGroupComplianceTask(groupId, deviceIds);
			case "CheckGroupSoftwareTask" -> new CheckGroupSoftwareTask(groupId, deviceIds);
			case "RunGroupDiagnosticsTask" -> new RunGroupDiagnosticsTask(groupId, deviceIds, t.getDontCheckCompliance());
			case "RunDeviceGroupScriptTask" -> new RunDeviceGroupScriptTask(groupId, deviceIds, t.getScript(), t.getDeviceDriver(),
				readUserInputs(t.getUserInputValues()), t.getRunSnapshot(), t.getRunDiagnostics(), t.getCheckCompliance());
			default -> throw new IllegalStateException("Unknown task type " + t.getDtype());
		};
	}

	private static Task toTask(Record r) {
		TaskRecord t = r.into(TASK);
		return new Task(t.getId(), t.getVersion(), t.getAuthor(), t.getComments(), t.getCreationDate(), t.getChangeDate(),
			t.getExecutionDate(), t.getScheduleReference(),
			t.getScheduleType() == null ? null : Task.ScheduleType.valueOf(t.getScheduleType()),
			t.getScheduleFactor() == null ? 0 : t.getScheduleFactor(), t.getPriority(),
			t.getStatus() == null ? null : Task.Status.valueOf(t.getStatus()), t.getTarget(), t.getRunnerId(), t.getDebugEnabled(),
			t.getParentTaskId(), t.getChildOrder(), Task.ScheduleMode.valueOf(t.getScheduleMode()), t.getStopOnFailure(),
			t.getCancelRequested(), t.getLog() == null ? "" : t.getLog(), toSpec(r));
	}

	/**
	 * Lists the tasks matching a condition, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param condition the condition on {@code TASK}
	 * @return the tasks
	 */
	public List<Task> findWhere(DSLContext dsl, Condition condition) {
		return dsl.select(FIELDS).from(TASK).where(condition).orderBy(TASK.ID).fetch(TaskRepository::toTask);
	}

	/**
	 * Gets a task.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @return the task, or null if there is no such task
	 */
	public Task find(DSLContext dsl, long id) {
		List<Task> tasks = this.findWhere(dsl, TASK.ID.eq(id));
		return tasks.isEmpty() ? null : tasks.get(0);
	}

	/**
	 * Lists the tasks in some statuses.
	 *
	 * @param dsl the jOOQ context
	 * @param statuses the statuses
	 * @return the tasks
	 */
	public List<Task> findByStatus(DSLContext dsl, Task.Status... statuses) {
		return this.findWhere(dsl, statusIn(statuses));
	}

	/**
	 * Gets the condition "the status of the task is one of these".
	 *
	 * @param statuses the statuses
	 * @return the condition
	 */
	public static Condition statusIn(Task.Status... statuses) {
		List<String> names = new ArrayList<>();
		for (Task.Status status : statuses) {
			names.add(status.name());
		}
		return TASK.STATUS.in(names);
	}

	/**
	 * Lists the child tasks of a task, in order.
	 *
	 * @param dsl the jOOQ context
	 * @param parentId the ID of the parent task
	 * @return the child tasks
	 */
	public List<Task> findChildren(DSLContext dsl, long parentId) {
		return dsl.select(FIELDS).from(TASK).where(TASK.PARENT_TASK_ID.eq(parentId)).orderBy(TASK.CHILD_ORDER, TASK.ID)
			.fetch(TaskRepository::toTask);
	}

	/**
	 * Gets the status of a task.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @return the status, or null if there is no such task
	 */
	public Task.Status findStatus(DSLContext dsl, long id) {
		String status = dsl.select(TASK.STATUS).from(TASK).where(TASK.ID.eq(id)).fetchOne(TASK.STATUS);
		return status == null ? null : Task.Status.valueOf(status);
	}

	/**
	 * Gets the statuses of tasks.
	 *
	 * @param dsl the jOOQ context
	 * @param ids the IDs
	 * @return the status of each existing task, by ID
	 */
	public Map<Long, Task.Status> findStatuses(DSLContext dsl, Collection<Long> ids) {
		Map<Long, Task.Status> statuses = new HashMap<>();
		dsl.select(TASK.ID, TASK.STATUS).from(TASK).where(TASK.ID.in(ids))
			.forEach(r -> statuses.put(r.value1(), Task.Status.valueOf(r.value2())));
		return statuses;
	}

	/**
	 * Tells whether the cancellation of a task was requested.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @return true if the cancellation was requested
	 */
	public boolean isCancelRequested(DSLContext dsl, long id) {
		return Boolean.TRUE.equals(dsl.select(TASK.CANCEL_REQUESTED).from(TASK).where(TASK.ID.eq(id)).fetchOne(TASK.CANCEL_REQUESTED));
	}

	/**
	 * Gets the debug log of a task.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID of the task
	 * @return the debug log, or null if there is none
	 */
	public String findDebugLog(DSLContext dsl, long id) {
		return dsl.select(DEBUG_LOG.TEXT).from(TASK).join(DEBUG_LOG).on(DEBUG_LOG.ID.eq(TASK.DEBUG_LOG))
			.where(TASK.ID.eq(id)).fetchOne(DEBUG_LOG.TEXT);
	}

	// The attributes (JSON) of a new task, depending on its type.
	private static Map<String, Object> attributesOf(TaskSpec spec) {
		Map<String, Object> attributes = new LinkedHashMap<>();
		switch (spec) {
			case TakeSnapshotTask s -> attributes.put("automatic", s.automatic());
			case TakeGroupSnapshotTask s -> attributes.put("limitToOutofdateDeviceHours", s.limitToOutofdateDeviceHours());
			case PurgeDatabaseTask s -> {
				attributes.put("days", s.days());
				attributes.put("configDays", s.configDays());
				attributes.put("configSize", s.configSize());
				attributes.put("configKeepDays", s.configKeepDays());
				attributes.put("moduleDays", s.moduleDays());
				attributes.put("removeOrphanFiles", s.removeOrphanFiles());
			}
			case DiscoverDeviceTypeTask s -> {
				if (s.deviceComments() != null) {
					attributes.put("deviceComments", s.deviceComments());
				}
				if (!s.deviceTags().isEmpty()) {
					attributes.put("deviceTags", s.deviceTags());
				}
				if (s.discoveredDeviceType() != null) {
					attributes.put(DiscoverDeviceTypeTask.DISCOVERED_DEVICE_TYPE, s.discoveredDeviceType());
				}
				if (s.snapshotTaskId() != 0) {
					attributes.put(DiscoverDeviceTypeTask.SNAPSHOT_TASK_ID, s.snapshotTaskId());
				}
			}
			case RunDiagnosticsTask _ -> {
			}
			case CheckComplianceTask _ -> {
			}
			case RunDeviceScriptTask _ -> {
			}
			case ScanSubnetsTask _ -> {
			}
			case CheckGroupComplianceTask _ -> {
			}
			case CheckGroupSoftwareTask _ -> {
			}
			case RunGroupDiagnosticsTask _ -> {
			}
			case RunDeviceGroupScriptTask _ -> {
			}
		}
		return attributes;
	}

	/**
	 * Adds a task (with its device list, credential sets or subnets). The change date is now,
	 * unless the task has one.
	 *
	 * @param dsl the jOOQ context
	 * @param task the task (its ID is ignored)
	 * @return the stored task, with its ID
	 */
	public Task add(DSLContext dsl, Task task) {
		TaskSpec spec = task.spec();
		Instant changeDate = task.changeDate() == null ? changeDateNow() : task.changeDate();
		TaskRecord t = dsl.newRecord(TASK);
		t.setDtype(spec.typeName());
		t.setVersion(0);
		t.setAuthor(task.author());
		t.setComments(task.comments());
		t.setCreationDate(task.creationDate());
		t.setChangeDate(changeDate);
		t.setExecutionDate(task.executionDate());
		t.setScheduleReference(task.scheduleReference());
		t.setScheduleType(task.scheduleType() == null ? null : task.scheduleType().name());
		t.setScheduleFactor(task.scheduleFactor());
		t.setPriority(task.priority());
		t.setStatus(task.status() == null ? null : task.status().name());
		t.setTarget(task.target());
		t.setRunnerId(task.runnerId());
		t.setDebugEnabled(task.debugEnabled());
		t.setParentTaskId(task.parentTaskId());
		t.setChildOrder(task.childOrder());
		t.setScheduleMode(task.scheduleMode().name());
		t.setStopOnFailure(task.stopOnFailure());
		t.setCancelRequested(task.cancelRequested());
		t.setLog(task.log());
		t.setAttributes(toJson(attributesOf(spec)));
		t.setRunSnapshot(false);
		t.setRunDiagnostics(false);
		t.setCheckCompliance(false);
		t.setDontRunDiagnostics(false);
		t.setDontCheckCompliance(false);
		if (spec instanceof DeviceBasedTask s) {
			t.setDevice(s.deviceId());
		}
		if (spec instanceof GroupBasedTask s) {
			t.setDeviceGroup(s.deviceGroupId());
		}
		if (spec instanceof DomainBasedTask s) {
			t.setDomain(s.domainId());
		}
		switch (spec) {
			case TakeSnapshotTask s -> {
				t.setDontRunDiagnostics(s.dontRunDiagnostics());
				t.setDontCheckCompliance(s.dontCheckCompliance());
			}
			case RunDiagnosticsTask s -> t.setDontCheckCompliance(s.dontCheckCompliance());
			case TakeGroupSnapshotTask s -> {
				t.setDontRunDiagnostics(s.dontRunDiagnostics());
				t.setDontCheckCompliance(s.dontCheckCompliance());
			}
			case RunGroupDiagnosticsTask s -> t.setDontCheckCompliance(s.dontCheckCompliance());
			case RunDeviceScriptTask s -> {
				t.setScript(s.script());
				t.setDeviceDriver(s.deviceDriver());
				t.setUserInputValues(toJson(s.userInputValues()));
				t.setRunSnapshot(s.runSnapshot());
				t.setRunDiagnostics(s.runDiagnostics());
				t.setCheckCompliance(s.checkCompliance());
			}
			case RunDeviceGroupScriptTask s -> {
				t.setScript(s.script());
				t.setDeviceDriver(s.deviceDriver());
				t.setUserInputValues(toJson(s.userInputValues()));
				t.setRunSnapshot(s.runSnapshot());
				t.setRunDiagnostics(s.runDiagnostics());
				t.setCheckCompliance(s.checkCompliance());
			}
			case DiscoverDeviceTypeTask s -> {
				t.setDeviceAddress(s.deviceAddress());
				t.setDiscoverResultDevice(s.discoverResultDeviceId());
			}
			case CheckComplianceTask _ -> {
			}
			case ScanSubnetsTask _ -> {
			}
			case PurgeDatabaseTask _ -> {
			}
			case CheckGroupComplianceTask _ -> {
			}
			case CheckGroupSoftwareTask _ -> {
			}
		}
		t.store();
		long id = t.getId();
		if (spec instanceof GroupBasedTask s) {
			int position = 0;
			for (Long deviceId : s.deviceIds()) {
				dsl.insertInto(TASK_DEVICE_LIST_MEMBERS)
					.set(TASK_DEVICE_LIST_MEMBERS.TASK_ID, id)
					.set(TASK_DEVICE_LIST_MEMBERS.DEVICE_ID, deviceId)
					.set(TASK_DEVICE_LIST_MEMBERS.LIST_POSITION, position++)
					.execute();
			}
		}
		if (spec instanceof DiscoverDeviceTypeTask s) {
			for (Long credentialSetId : s.credentialSetIds()) {
				dsl.insertInto(DISCOVER_DEVICE_TYPE_TASK_CREDENTIAL_SETS)
					.set(DISCOVER_DEVICE_TYPE_TASK_CREDENTIAL_SETS.DISCOVER_DEVICE_TYPE_TASK, id)
					.set(DISCOVER_DEVICE_TYPE_TASK_CREDENTIAL_SETS.CREDENTIAL_SETS, credentialSetId)
					.execute();
			}
		}
		if (spec instanceof ScanSubnetsTask s) {
			for (Network4Address subnet : s.subnets()) {
				dsl.insertInto(SCAN_SUBNETS_TASK_SUBNETS)
					.set(SCAN_SUBNETS_TASK_SUBNETS.SCAN_SUBNETS_TASK, id)
					.set(SCAN_SUBNETS_TASK_SUBNETS.IPV4ADDRESS, subnet.getAddress())
					.set(SCAN_SUBNETS_TASK_SUBNETS.IPV4MASK, subnet.getPrefixLength())
					.set(SCAN_SUBNETS_TASK_SUBNETS.IPV4USAGE, subnet.getAddressUsage() == null ? null : subnet.getAddressUsage().name())
					.execute();
			}
		}
		return task.toBuilder().id(id).version(0).changeDate(changeDate).build();
	}

	/**
	 * Changes the status of a task, if it is in one of the expected statuses.
	 * The change date and the version are not changed (bulk status change).
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @param status the new status
	 * @param expected the expected current statuses (none for any)
	 * @return true if the status was changed
	 */
	public boolean setStatus(DSLContext dsl, long id, Task.Status status, Task.Status... expected) {
		return dsl.update(TASK).set(TASK.STATUS, status.name())
			.where(TASK.ID.eq(id).and(expected.length == 0 ? DSL.noCondition() : statusIn(expected)))
			.execute() == 1;
	}

	/**
	 * Changes the status of tasks which are in one of the expected statuses (bulk change).
	 *
	 * @param dsl the jOOQ context
	 * @param condition which tasks
	 * @param status the new status
	 * @param expected the expected current statuses
	 * @return the number of changed tasks
	 */
	public int setStatus(DSLContext dsl, Condition condition, Task.Status status, Task.Status... expected) {
		return dsl.update(TASK).set(TASK.STATUS, status.name()).where(condition.and(statusIn(expected))).execute();
	}

	/**
	 * Changes the status of a task, changing its change date.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @param status the new status
	 */
	public void changeStatus(DSLContext dsl, long id, Task.Status status) {
		this.touch(dsl).set(TASK.STATUS, status.name()).where(TASK.ID.eq(id)).execute();
	}

	/**
	 * Assigns a task to a runner: it is then waiting to be run.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @param runnerId the ID of the runner
	 */
	public void assignRunner(DSLContext dsl, long id, String runnerId) {
		this.touch(dsl).set(TASK.RUNNER_ID, runnerId).set(TASK.STATUS, Task.Status.WAITING.name()).where(TASK.ID.eq(id)).execute();
	}

	/**
	 * Claims a task to run it: the task must be waiting or scheduled (and assigned to the runner, if given).
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @param runnerId the ID of the runner (null in single mode)
	 * @return true if the task was claimed (it is then RUNNING)
	 */
	public boolean claim(DSLContext dsl, long id, String runnerId) {
		return dsl.update(TASK)
			.set(TASK.STATUS, Task.Status.RUNNING.name())
			.set(TASK.VERSION, TASK.VERSION.plus(1))
			.where(TASK.ID.eq(id).and(statusIn(Task.Status.WAITING, Task.Status.SCHEDULED))
				.and(runnerId == null ? DSL.noCondition() : TASK.RUNNER_ID.eq(runnerId)))
			.execute() == 1;
	}

	/**
	 * Marks a claimed task as started.
	 *
	 * @param dsl the jOOQ context
	 * @param run the run
	 */
	public void start(DSLContext dsl, TaskRun run) {
		this.touch(dsl)
			.set(TASK.STATUS, Task.Status.RUNNING.name())
			.set(TASK.LOG, "")
			.set(TASK.EXECUTION_DATE, run.getExecutionDate())
			.where(TASK.ID.eq(run.getTask().id()))
			.execute();
	}

	/**
	 * Saves the result of a run.
	 *
	 * @param dsl the jOOQ context
	 * @param run the run
	 */
	public void saveRun(DSLContext dsl, TaskRun run) {
		long id = run.getTask().id();
		Long debugLogId = null;
		if (run.getDebugLog() != null) {
			debugLogId = dsl.insertInto(DEBUG_LOG).set(DEBUG_LOG.TEXT, run.getDebugLog()).returning(DEBUG_LOG.ID).fetchOne(DEBUG_LOG.ID);
		}
		Map<String, Object> attributes = readAttributes(dsl.select(TASK.ATTRIBUTES).from(TASK).where(TASK.ID.eq(id)).fetchOne(TASK.ATTRIBUTES));
		attributes.putAll(run.getResults());
		var update = this.touch(dsl)
			.set(TASK.STATUS, run.getStatus().name())
			.set(TASK.LOG, run.getLog())
			.set(TASK.EXECUTION_DATE, run.getExecutionDate())
			.set(TASK.ATTRIBUTES, toJson(attributes));
		if (debugLogId != null) {
			update = update.set(TASK.DEBUG_LOG, debugLogId);
		}
		if (run.getResultDeviceId() != null) {
			update = update.set(TASK.DISCOVER_RESULT_DEVICE, run.getResultDeviceId());
		}
		update.where(TASK.ID.eq(id)).execute();
	}

	/**
	 * Marks a task as failed, with a log.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @param log the log (replacing the former one), or null to keep the log
	 */
	public void fail(DSLContext dsl, long id, String log) {
		var update = this.touch(dsl).set(TASK.STATUS, Task.Status.FAILURE.name());
		if (log != null) {
			update = update.set(TASK.LOG, log).set(TASK.EXECUTION_DATE, Instant.now());
		}
		update.where(TASK.ID.eq(id)).execute();
	}

	/**
	 * Marks a task which is still running (or waiting for its device) as failed, appending the reason to its log.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @param reason the reason, appended to the log
	 * @return true if the task was still running (and is now failed)
	 */
	public boolean failRunning(DSLContext dsl, long id, String reason) {
		return this.touch(dsl)
			.set(TASK.STATUS, Task.Status.FAILURE.name())
			.set(TASK.LOG, DSL.coalesce(TASK.LOG, "").concat(DSL.val(TaskRun.formatLine(org.slf4j.event.Level.ERROR, reason))))
			.where(TASK.ID.eq(id), TaskRepository.statusIn(Task.Status.RUNNING, Task.Status.WAITING))
			.execute() > 0;
	}

	/**
	 * Cancels a task, logging the reason.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @param reason the reason, appended to the log
	 */
	public void cancel(DSLContext dsl, long id, String reason) {
		this.touch(dsl)
			.set(TASK.STATUS, Task.Status.CANCELLED.name())
			.set(TASK.LOG, DSL.coalesce(TASK.LOG, "").concat(DSL.val(TaskRun.formatLine(org.slf4j.event.Level.WARN, reason))))
			.where(TASK.ID.eq(id))
			.execute();
	}

	/**
	 * Flags a task so that it stops (while it is running).
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 */
	public void requestCancel(DSLContext dsl, long id) {
		dsl.update(TASK).set(TASK.CANCEL_REQUESTED, true).where(TASK.ID.eq(id)).execute();
	}

	// An update of a task changing its change date and version.
	private org.jooq.UpdateSetMoreStep<TaskRecord> touch(DSLContext dsl) {
		return dsl.update(TASK).set(TASK.CHANGE_DATE, changeDateNow()).set(TASK.VERSION, TASK.VERSION.plus(1));
	}

	/**
	 * Deletes the tasks finished before a date.
	 *
	 * @param dsl the jOOQ context
	 * @param before the date
	 * @param deviceCondition null for all tasks, or the condition on the target device ({@code TASK.DEVICE})
	 *        of the device-based tasks to delete
	 * @return the number of deleted tasks
	 */
	public int deleteFinished(DSLContext dsl, Instant before, Condition deviceCondition) {
		Condition condition = statusIn(Task.Status.CANCELLED, Task.Status.FAILURE, Task.Status.SUCCESS)
			.and(TASK.EXECUTION_DATE.lt(before));
		if (deviceCondition != null) {
			condition = condition.and(TASK.DEVICE.isNotNull()).and(deviceCondition);
		}
		return dsl.deleteFrom(TASK).where(condition).execute();
	}

	// ------------------------------------------------------------------
	// Summaries and counts
	// ------------------------------------------------------------------

	private static final List<SelectField<?>> SUMMARY_FIELDS = List.of(TASK.ID, TASK.VERSION, TASK.DTYPE, TASK.STATUS, TASK.AUTHOR, TASK.TARGET,
		TASK.COMMENTS, TASK.CREATION_DATE, TASK.CHANGE_DATE, TASK.EXECUTION_DATE, TASK.SCHEDULE_REFERENCE, TASK.SCHEDULE_TYPE,
		TASK.SCHEDULE_FACTOR, TASK.PRIORITY, TASK.RUNNER_ID, TASK.DEBUG_ENABLED, TASK.DEVICE, TASK.DEVICE_GROUP,
		TASK.PARENT_TASK_ID, TASK.CHILD_ORDER, TASK.SCHEDULE_MODE, TASK.STOP_ON_FAILURE);

	private static TaskSummary toSummary(Record r) {
		return new TaskSummary(r.get(TASK.ID), r.get(TASK.VERSION), r.get(TASK.DTYPE),
			r.get(TASK.STATUS) == null ? null : Task.Status.valueOf(r.get(TASK.STATUS)),
			r.get(TASK.AUTHOR), r.get(TASK.TARGET), r.get(TASK.COMMENTS), r.get(TASK.CREATION_DATE), r.get(TASK.CHANGE_DATE),
			r.get(TASK.EXECUTION_DATE), r.get(TASK.SCHEDULE_REFERENCE),
			r.get(TASK.SCHEDULE_TYPE) == null ? null : Task.ScheduleType.valueOf(r.get(TASK.SCHEDULE_TYPE)),
			r.get(TASK.SCHEDULE_FACTOR) == null ? 0 : r.get(TASK.SCHEDULE_FACTOR), r.get(TASK.PRIORITY), r.get(TASK.RUNNER_ID),
			r.get(TASK.DEBUG_ENABLED), r.get(TASK.DEVICE), r.get(TASK.DEVICE_GROUP), r.get(TASK.PARENT_TASK_ID),
			r.get(TASK.CHILD_ORDER), Task.ScheduleMode.valueOf(r.get(TASK.SCHEDULE_MODE)), r.get(TASK.STOP_ON_FAILURE));
	}

	// The condition "the date of the task (execution date, or change date if it never ran) is in the range".
	private static Condition dateRange(Instant from, Instant to, boolean executionToIncluded) {
		Condition executed = TASK.EXECUTION_DATE.isNotNull();
		Condition notExecuted = TASK.EXECUTION_DATE.isNull();
		if (from != null) {
			executed = executed.and(TASK.EXECUTION_DATE.ge(from));
			notExecuted = notExecuted.and(TASK.CHANGE_DATE.ge(from));
		}
		if (to != null) {
			executed = executed.and(executionToIncluded ? TASK.EXECUTION_DATE.le(to) : TASK.EXECUTION_DATE.lt(to));
			notExecuted = notExecuted.and(TASK.CHANGE_DATE.lt(to));
		}
		return executed.or(notExecuted);
	}

	/** The fields the task lists can be filtered and sorted on. */
	public static final QueryMapping LIST = QueryMapping.builder(TASK.ID)
		.enumeration("type", TASK.DTYPE, TYPE_NAMES, Option.SORTABLE)
		.enumeration("status", TASK.STATUS, Task.Status.class, Option.SORTABLE)
		.string("author", TASK.AUTHOR, Option.SORTABLE, Option.SEARCHABLE)
		.string("target", TASK.TARGET, Option.SORTABLE, Option.SEARCHABLE)
		.string("comments", TASK.COMMENTS, Option.SEARCHABLE)
		.integer("priority", TASK.PRIORITY, Option.SORTABLE)
		.enumeration("scheduleType", TASK.SCHEDULE_TYPE, Task.ScheduleType.class, Option.SORTABLE)
		.date("scheduleReference", TASK.SCHEDULE_REFERENCE, Option.SORTABLE)
		.date("creationDate", TASK.CREATION_DATE, Option.SORTABLE)
		.date("changeDate", TASK.CHANGE_DATE, Option.SORTABLE)
		.date("executionDate", TASK.EXECUTION_DATE, Option.SORTABLE)
		.integer("device", TASK.DEVICE)
		.integer("group", TASK.DEVICE_GROUP)
		.integer("parentTask", TASK.PARENT_TASK_ID)
		.integer("childOrder", TASK.CHILD_ORDER, Option.SORTABLE)
		.string("runnerId", TASK.RUNNER_ID)
		.bool("debugEnabled", TASK.DEBUG_ENABLED)
		.defaultSort(SortOrder.desc("id"))
		.build();

	/**
	 * Lists the summaries of the tasks matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query
	 * @return the summaries of the window of the query
	 */
	public List<TaskSummary> findSummaries(DSLContext dsl, ListQuery query) {
		return query.page().apply(dsl.select(SUMMARY_FIELDS).from(TASK).where(LIST.condition(query)).orderBy(LIST.orderBy(query)))
			.fetch(TaskRepository::toSummary);
	}

	/**
	 * Lists the tasks matching a query (see {@link #find} for what is loaded).
	 *
	 * @param dsl the jOOQ context
	 * @param query the query
	 * @return the tasks of the window of the query
	 */
	public List<Task> findAll(DSLContext dsl, ListQuery query) {
		return query.page().apply(dsl.select(FIELDS).from(TASK).where(LIST.condition(query)).orderBy(LIST.orderBy(query)))
			.fetch(TaskRepository::toTask);
	}

	/**
	 * Counts the tasks matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query (its window is ignored)
	 * @return the number of tasks
	 */
	public long count(DSLContext dsl, ListQuery query) {
		return dsl.fetchCount(TASK, LIST.condition(query));
	}

	/**
	 * Lists the summaries of the tasks: by descending ID, or by order for the children of a task.
	 *
	 * @param dsl the jOOQ context
	 * @param filter the filter
	 * @param page the window on the result
	 * @return the summaries
	 */
	public List<TaskSummary> findSummaries(DSLContext dsl, TaskFilter filter, Page page) {
		Condition condition = DSL.noCondition();
		if (filter.statuses() != null && !filter.statuses().isEmpty()) {
			condition = condition.and(statusIn(filter.statuses().toArray(new Task.Status[0])));
		}
		if (filter.types() != null && !filter.types().isEmpty()) {
			// The unknown type names are ignored
			condition = condition.and(TASK.DTYPE.in(filter.types().stream().filter(TYPE_NAMES::contains).toList()));
		}
		if (filter.after() != null) {
			condition = condition.and(dateRange(Instant.ofEpochMilli(filter.after()), null, true));
		}
		if (filter.before() != null) {
			condition = condition.and(dateRange(null, Instant.ofEpochMilli(filter.before()), true));
		}
		if (filter.parentTaskId() != null) {
			condition = condition.and(TASK.PARENT_TASK_ID.eq(filter.parentTaskId()));
		}
		return page.apply(dsl.select(SUMMARY_FIELDS).from(TASK).where(condition)
				.orderBy(filter.parentTaskId() != null ? TASK.CHILD_ORDER.asc() : TASK.ID.desc()))
			.fetch(TaskRepository::toSummary);
	}

	/**
	 * Lists the summaries of the tasks of a device: the active ones first, then the latest ones.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 * @param page the window on the result
	 * @return the summaries
	 */
	public List<TaskSummary> findSummariesByDevice(DSLContext dsl, long deviceId, Page page) {
		Field<Integer> statusOrder = DSL.when(TASK.STATUS.eq(Task.Status.RUNNING.name()), 1)
			.when(TASK.STATUS.eq(Task.Status.WAITING.name()), 2)
			.when(TASK.STATUS.eq(Task.Status.SCHEDULED.name()), 3)
			.when(TASK.STATUS.eq(Task.Status.NEW.name()), 4)
			.otherwise(10);
		return page.apply(dsl.select(SUMMARY_FIELDS).from(TASK).where(TASK.DEVICE.eq(deviceId))
				.orderBy(statusOrder.asc(), DSL.coalesce(TASK.EXECUTION_DATE, TASK.CHANGE_DATE).desc().nullsFirst(), TASK.ID.desc()))
			.fetch(TaskRepository::toSummary);
	}

	/**
	 * Counts the tasks by status.
	 *
	 * @param dsl the jOOQ context
	 * @param parentTaskId the ID of the parent task, to count its children only (or null for all tasks)
	 * @return the counts
	 */
	public List<TaskStatusCount> countByStatus(DSLContext dsl, Long parentTaskId) {
		return dsl.select(TASK.STATUS, DSL.count(TASK.ID)).from(TASK)
			.where(parentTaskId == null ? DSL.noCondition() : TASK.PARENT_TASK_ID.eq(parentTaskId))
			.groupBy(TASK.STATUS)
			.fetch(r -> new TaskStatusCount(Task.Status.valueOf(r.value1()), r.value2()));
	}

	/**
	 * Counts the tasks in some statuses by status, over a date range.
	 *
	 * @param dsl the jOOQ context
	 * @param statuses the statuses
	 * @param from the start of the range (included)
	 * @param to the end of the range (excluded)
	 * @return the counts
	 */
	public List<TaskStatusCount> countByStatus(DSLContext dsl, Collection<Task.Status> statuses, Instant from, Instant to) {
		return dsl.select(TASK.STATUS, DSL.count(TASK.ID)).from(TASK)
			.where(statusIn(statuses.toArray(new Task.Status[0])).and(dateRange(from, to, false)))
			.groupBy(TASK.STATUS)
			.fetch(r -> new TaskStatusCount(Task.Status.valueOf(r.value1()), r.value2()));
	}

	/**
	 * Counts the tasks of a type in some statuses, over a date range.
	 *
	 * @param dsl the jOOQ context
	 * @param typeName the type name
	 * @param statuses the statuses
	 * @param from the start of the range (included)
	 * @param to the end of the range (excluded)
	 * @return the count
	 */
	public long countByType(DSLContext dsl, String typeName, Collection<Task.Status> statuses, Instant from, Instant to) {
		Integer count = dsl.selectCount().from(TASK)
			.where(TASK.DTYPE.eq(typeName).and(statusIn(statuses.toArray(new Task.Status[0]))).and(dateRange(from, to, false)))
			.fetchOne(0, Integer.class);
		return count == null ? 0L : count;
	}
}
