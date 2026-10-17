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

import org.hibernate.Session;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import org.quartz.JobKey;
import org.quartz.Trigger;
import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Converter;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.engine.TaskContext;
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
 * A task.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
@Table(indexes = {
	@Index(name = "changeDateIndex", columnList = "changeDate"),
	@Index(name = "creationDateIndex", columnList = "creationDate"),
	@Index(name = "statusIndex", columnList = "status"),
	@Index(name = "executionDateIndex", columnList = "executionDate")
})
@Slf4j
public abstract class Task implements Cloneable {

	/**
	 * The Enum ScheduleType.
	 */
	public enum ScheduleType {
		ASAP,
		AT,
		DAILY,
		MONTHLY,
		WEEKLY,
		HOURLY,
	}

	/**
	 * How a group-based task schedules its per-device child tasks.
	 */
	public enum ScheduleMode {
		/**
		 * Schedule all child tasks at once, let the scheduler run as many as possible in
		 * parallel -- the parent stays RUNNING until every child has finished.
		 */
		PARALLEL,
		/** Schedule child tasks one device at a time, waiting for each to finish before starting the next. */
		SEQUENTIAL,
	}

	/**
	 * How a task accesses its target device, with respect to the other tasks targeting the same
	 * device (enforced by {@link net.netshot.netshot.task.engine.TaskJob} using {@link net.netshot.netshot.task.engine.TaskLockManager}, cluster-wide).
	 *
	 * <p>Invariant: a task holding exclusive access to a device must never wait for another task
	 * (this would allow deadlocks).
	 */
	public enum DeviceLockPolicy {
		/** No exclusive access to the device is needed. */
		NONE,
		/**
		 * Exclusive access to the device; the task is cancelled if a task of the same type is
		 * already running or waiting on the device (tasks of this type are interchangeable),
		 * otherwise waits for the device to be free.
		 */
		CANCEL_IF_SAME_TYPE,
		/** Exclusive access to the device; always waits for the device to be free. */
		WAIT,
	}

	/**
	 * The Enum Status.
	 *
	 * <p>Persisted via {@link StatusConverter} using the explicit {@link #getValue()} below,
	 * rather than JPA's default (the enum's declaration order) -- so this list can be
	 * reordered or extended (e.g. {@link #DELAYED}, added after the rest) without silently
	 * reinterpreting already-persisted status values. When adding a new constant, give it the
	 * next unused value; never reuse or renumber an existing one.
	 */
	public enum Status {

		/** The task was cancelled. */
		CANCELLED(0),

		/** The task failed. */
		FAILURE(1),

		/** The task is new. */
		NEW(2),

		/** The task is running. */
		RUNNING(3),

		/** The task is scheduled. */
		SCHEDULED(4),

		/** The task is a success. */
		SUCCESS(5),

		/** The task is waiting. */
		WAITING(6),

		/**
		 * The task is a not-yet-promoted child of a group-based task -- pre-created (with its
		 * device/config already set) so the parent's total child count is known upfront, but
		 * deliberately excluded from every "pick this up and run it" query
		 * ({@code scheduleNewTasks}, {@code rescheduleAll}, {@code reassignOrphanTasks}) until
		 * the parent's own orchestration logic promotes it.
		 */
		DELAYED(7);

		private final int value;

		Status(int value) {
			this.value = value;
		}

		/**
		 * The fixed value this status is persisted as (see {@link StatusConverter}).
		 * @return the persisted value
		 */
		public int getValue() {
			return this.value;
		}

		/**
		 * Finds the status for the given persisted value.
		 * @param value the persisted value
		 * @return the matching status
		 * @throws IllegalArgumentException if no status has this value
		 */
		public static Status fromValue(int value) {
			for (Status status : Status.values()) {
				if (status.value == value) {
					return status;
				}
			}
			throw new IllegalArgumentException("Unknown Task.Status value " + value);
		}

		/**
		 * Converts {@link Status} to/from its explicit, permanently-fixed persisted value
		 * (see {@link #getValue()}), instead of JPA's default ordinal-by-declaration-order
		 * mapping.
		 */
		@Converter(autoApply = true)
		public static class StatusConverter implements AttributeConverter<Status, Integer> {
			@Override
			public Integer convertToDatabaseColumn(Status status) {
				return status == null ? null : status.getValue();
			}

			@Override
			public Status convertToEntityAttribute(Integer value) {
				return value == null ? null : Status.fromValue(value);
			}
		}
	}

	/** The Constant TASK_CLASSES. */
	private static final Set<Class<? extends Task>> TASK_CLASSES;

	static {
		TASK_CLASSES = new HashSet<Class<? extends Task>>();
		try {
			for (Class<?> clazz : Database.listClassesInPackage("net.netshot.netshot.task.type")) {
				if (Task.class.isAssignableFrom(clazz)) {
					@SuppressWarnings("unchecked") Class<? extends Task> taskClass = (Class<? extends Task>) clazz;
					TASK_CLASSES.add(taskClass);
				}
			}
		}
		catch (Exception e) {
			log.error("Error while scanning the task classes.", e);
		}
	}

	/**
	 * Gets the task classes.
	 *
	 * @return the task classes
	 */
	public static final Set<Class<? extends Task>> getTaskClasses() {
		return TASK_CLASSES;
	}


	/**
	 * Pure decision logic for the sequential scheduling mode of group-based tasks
	 * (TakeGroupSnapshotTask, RunDeviceGroupScriptTask, RunGroupDiagnosticsTask), extracted
	 * out of the child orchestration loop so it can be unit tested without Quartz/DB/real child
	 * task execution.
	 */
	public static final class SequentialScheduling {

		/**
		 * What the sequential loop should do once the current child task (if any) has
		 * reached a terminal state.
		 */
		public enum NextAction {
			/** Schedule the next device in the list. */
			CONTINUE,
			/** Stop scheduling further devices (a cancellation was honored, or a failure occurred with stop-on-failure). */
			STOP,
		}

		/**
		 * Decides whether the sequential loop should continue to the next device or stop.
		 *
		 * @param lastChildFailed whether the child task just completed with a FAILURE status (false if none has run yet)
		 * @param stopOnFailure whether the task is configured to stop scheduling further children on failure
		 * @param cancelRequested whether cancellation of the parent task has been requested
		 * @return CONTINUE or STOP
		 */
		public static NextAction decideNextAction(boolean lastChildFailed, boolean stopOnFailure, boolean cancelRequested) {
			if (cancelRequested) {
				return NextAction.STOP;
			}
			if (lastChildFailed && stopOnFailure) {
				return NextAction.STOP;
			}
			return NextAction.CONTINUE;
		}

		/**
		 * Decides the parent task's final status once the sequential loop has ended.
		 *
		 * <p>In don't-stop-on-failure mode, the loop deliberately keeps scheduling children
		 * past a failure, so a failed child is not by itself a reason to fail the parent --
		 * the parent only fails if it couldn't do its job of scheduling the children (a
		 * scheduling error), or reports FAILURE when stop-on-failure is enabled and a child
		 * failure actually caused the loop to stop early.
		 *
		 * @param anyChildFailed whether at least one child task failed
		 * @param stopOnFailure whether the task is configured to stop scheduling further children on failure
		 * @param cancelHonored whether the loop stopped early because cancellation was requested
		 * @param schedulingError whether an error occurred while scheduling/persisting a child (not a child task failure)
		 * @return the final Task.Status (a scheduling error, or a failure with stop-on-failure, takes priority
		 *         over CANCELLED, which takes priority over SUCCESS)
		 */
		public static Task.Status decideFinalStatus(boolean anyChildFailed, boolean stopOnFailure,
				boolean cancelHonored, boolean schedulingError) {
			if (schedulingError) {
				return Task.Status.FAILURE;
			}
			if (anyChildFailed && stopOnFailure) {
				return Task.Status.FAILURE;
			}
			if (cancelHonored) {
				return Task.Status.CANCELLED;
			}
			return Task.Status.SUCCESS;
		}

		private SequentialScheduling() {
		}
	}

	/** The author. */
	@Getter
	@Setter
	protected String author = "";

	/** The change date. */
	@Getter
	@Setter
	protected Date changeDate;

	/** The comments. */
	@Getter
	@Setter
	protected String comments = "";

	/** The creation date. */
	@Getter
	@Setter
	protected Date creationDate = new Date();

	/** Debug enabled. */
	@Getter
	protected boolean debugEnabled = false;

	/** The debug log. */
	@Getter(onMethod = @__({
		@OneToOne(orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL),
		@OnDelete(action = OnDeleteAction.SET_NULL)
	}))
	@Setter
	protected DebugLog debugLog;

	/** The execution date. */
	@Getter
	@Setter
	protected Date executionDate;

	/** The id. */
	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)
	}))
	@Setter
	protected long id;

	/** The log. */
	protected StringBuffer logs = new StringBuffer();

	/** Full debug logs. */
	protected StringBuffer fullLogs = null;

	/** Task context. */
	public TaskContext logger = new TaskContext() {
		@Override
		public void log(Level level, String message, Object... params) {
			String noNullMessage = message.replace('\0', '\u2400');
			if (fullLogs != null) {
				fullLogs
					.append(Instant.now())
					.append(" [").append(level).append("] ")
					.append(MessageFormatter.arrayFormat(noNullMessage, params).getMessage())
					.append("\n");
			}
			if (level.toInt() <= Level.TRACE.toInt()) {
				// Don't log traces to base logs
				return;
			}
			logs
				.append(Instant.now())
				.append(" [").append(level).append("] ")
				.append(MessageFormatter.arrayFormat(noNullMessage, params).getMessage())
				.append("\n");
		}

		@Override
		public boolean isTracing() {
			return debugEnabled;
		}

		@Override
		public String getIdentifier() {
			return "%s_%d".formatted(Task.this.getClass().getSimpleName(), Task.this.getId());
		}
	};

	/** The schedule reference. */
	@Getter
	@Setter
	protected Date scheduleReference = new Date();

	/** The schedule type. */
	@Getter
	@Setter
	protected ScheduleType scheduleType = ScheduleType.ASAP;

	/** The factor (to multiply type). */
	@Getter
	@Setter
	protected int scheduleFactor = 1;

	/** The status. */
	@Getter
	@Setter
	protected Status status = Status.NEW;

	/** The target. */
	@Getter(onMethod = @__({
		@Column(length = 10000)
	}))
	@Setter
	protected String target = "None";

	/** DB version field. */
	@Getter(onMethod = @__({
		@Version
	}))
	@Setter
	private int version;

	/** Runner ID (clustering mode). */
	@Getter
	@Setter
	private String runnerId;

	/** Task priority. */
	@Getter
	@Setter
	private int priority = Trigger.DEFAULT_PRIORITY;

	/**
	 * The target device, shared by every task implementing {@link net.netshot.netshot.task.type.DeviceBasedTask}.
	 */
	@Getter(onMethod = @__({
		@ManyToOne(fetch = FetchType.LAZY),
		@OnDelete(action = OnDeleteAction.SET_NULL)
	}))
	@Setter
	protected Device device;

	/**
	 * The target device group, shared by every task implementing {@link net.netshot.netshot.task.type.GroupBasedTask}.
	 */
	@Getter(onMethod = @__({
		@ManyToOne(fetch = FetchType.LAZY),
		@OnDelete(action = OnDeleteAction.SET_NULL)
	}))
	@Setter
	protected DeviceGroup deviceGroup;

	/**
	 * The domain, shared by every task implementing {@link net.netshot.netshot.task.type.DomainBasedTask}.
	 */
	@Getter(onMethod = @__({
		@ManyToOne(fetch = FetchType.LAZY),
		@OnDelete(action = OnDeleteAction.CASCADE)
	}))
	@Setter
	protected Domain domain;

	/** Compatible device driver (script tasks). */
	@Getter
	@Setter
	protected String deviceDriver;

	/** The JS script to execute (script tasks). */
	@Getter(onMethod = @__({
		@Column(length = 10000000)
	}))
	@Setter
	protected String script;

	/** Variable values for the script (script tasks). */
	@Getter(onMethod = @__({
		@JdbcTypeCode(SqlTypes.JSON)
	}))
	@Setter
	protected Map<String, String> userInputValues;

	/** Automatically run a snapshot after successful script execution (script tasks). */
	@Getter
	@Setter
	protected boolean runSnapshot;

	/** Automatically run diagnostics after successful script execution (script tasks). */
	@Getter
	@Setter
	protected boolean runDiagnostics;

	/** Automatically check compliance after successful script execution (script tasks). */
	@Getter
	@Setter
	protected boolean checkCompliance;

	/** Do not automatically start a run diagnostics task (snapshot tasks). */
	@Getter
	@Setter
	protected boolean dontRunDiagnostics;

	/** Do not automatically start a check compliance task (snapshot/diagnostics tasks). */
	@Getter
	@Setter
	protected boolean dontCheckCompliance;

	/**
	 * Free-form, subclass-specific scalar attributes that don't warrant their own column
	 * (e.g. purge day counts, discovery result details, snapshot flags). Not part of any
	 * cross-task query/filter today, so a shared JSON blob avoids one sparse column per
	 * one-off field on the now-unified task table.
	 */
	@Getter(onMethod = @__({
		@JdbcTypeCode(SqlTypes.JSON)
	}))
	@Setter
	protected Map<String, Object> attributes = new HashMap<>();

	/**
	 * Ordered members of this task's one-time device list (as opposed to a real
	 * {@link net.netshot.netshot.inventory.group.DeviceGroup}), consistent with how
	 * {@link net.netshot.netshot.inventory.group.DeviceGroup#cachedMemberships} owns its
	 * {@code DeviceGroupMembership} rows: a real, cascade-managed relationship rather
	 * than a hand-rolled query.
	 */
	@Getter(onMethod = @__({
		@OneToMany(mappedBy = "key.task", cascade = CascadeType.ALL, orphanRemoval = true),
		@OrderBy("position asc"),
		@OnDelete(action = OnDeleteAction.CASCADE)
	}))
	@Setter
	protected List<TaskDeviceListMember> deviceListMembers = new ArrayList<>();

	/**
	 * Gets the ordered device list, derived from {@link #deviceListMembers}.
	 * @return the ordered list of devices, empty if this task has no device list
	 */
	@Transient
	public List<Device> getDeviceList() {
		List<Device> devices = new ArrayList<>(this.deviceListMembers.size());
		for (TaskDeviceListMember member : this.deviceListMembers) {
			devices.add(member.getDevice());
		}
		return devices;
	}

	/**
	 * Replaces the one-time device list with the given ordered devices, rebuilding
	 * {@link #deviceListMembers} (cascade-persisted/removed along with this task).
	 * @param devices the ordered devices, or null to clear the list
	 */
	public void setDeviceList(List<Device> devices) {
		this.deviceListMembers.clear();
		if (devices != null) {
			int position = 0;
			for (Device listDevice : devices) {
				this.deviceListMembers.add(new TaskDeviceListMember(this, listDevice, position));
				position++;
			}
		}
	}

	/**
	 * Resolves the in-memory devices targeted by a task that implements both
	 * {@link net.netshot.netshot.task.type.GroupBasedTask} and
	 * {@link net.netshot.netshot.task.type.DeviceListBasedTask}: either the cached
	 * devices of its {@link #deviceGroup}, or its one-time device list, whichever is set
	 * (the two are mutually exclusive, enforced at the REST layer). Assumes prepare(),
	 * or the caller, has already initialized whichever source applies.
	 *
	 * @return the ordered list of target devices, empty if neither source is set
	 */
	@Transient
	public List<Device> getTargetDevices() {
		if (this.deviceGroup != null) {
			return new ArrayList<>(this.deviceGroup.getCachedDevices());
		}
		return this.getDeviceList();
	}

	/**
	 * The ID of the parent task, when this task was spawned as a per-device child of a
	 * group-based task (either scheduling mode). Kept as a plain scalar column rather
	 * than a JPA relation to the parent Task: the parent is only ever needed by its ID
	 * here, and a lazy {@code @ManyToOne} would let Hibernate hand back an uninitialized
	 * proxy -- instead of the concrete subclass -- for the parent's own row whenever a
	 * sibling child task is hydrated first within the same polymorphic
	 * "select t from Task t" query, breaking Jackson's SIMPLE_NAME type discriminator
	 * on that row.
	 */
	@Getter(onMethod = @__({
		@Column(name = "parent_task_id")
	}))
	@Setter
	protected Long parentTaskId;

	/** Position of this task among its siblings, when it has a parent task. */
	@Getter
	@Setter
	protected int childOrder;

	/** Parallel vs sequential scheduling of child tasks (group-based tasks only). */
	@Getter
	@Setter
	protected ScheduleMode scheduleMode = ScheduleMode.PARALLEL;

	/** Whether to stop scheduling further child tasks after one fails (sequential mode only). */
	@Getter
	@Setter
	protected boolean stopOnFailure;

	/**
	 * Set by a cancellation request on a task that is already RUNNING (sequential group
	 * tasks spend their whole execution in RUNNING, so the normal SCHEDULED-only cancel
	 * path doesn't apply) -- polled by the sequential scheduling loop between child tasks.
	 */
	@Getter
	@Setter
	protected boolean cancelRequested;

	/**
	 * Checks whether cancellation of this (running, sequential-mode) task has been
	 * requested, re-reading the flag directly from the database rather than relying on
	 * this possibly-stale in-memory instance.
	 *
	 * @return true if cancellation was requested
	 */
	@Transient
	public boolean isCancelRequestedFresh() {
		Session session = Database.getSession(true);
		try {
			Boolean flag = session
				.createQuery("select t.cancelRequested from Task t where t.id = :id", Boolean.class)
				.setParameter("id", this.id)
				.uniqueResult();
			return Boolean.TRUE.equals(flag);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets a subclass-specific attribute stored in the shared {@link #attributes} map.
	 * @param key the attribute key
	 * @param defaultValue the value to return when the attribute isn't set
	 * @return the attribute value, or defaultValue
	 */
	protected int getIntAttribute(String key, int defaultValue) {
		Object value = this.attributes.get(key);
		if (value instanceof Number number) {
			return number.intValue();
		}
		return defaultValue;
	}

	/**
	 * Gets a subclass-specific long attribute stored in the shared {@link #attributes} map.
	 * @param key the attribute key
	 * @param defaultValue the value to return when the attribute isn't set
	 * @return the attribute value, or defaultValue
	 */
	protected long getLongAttribute(String key, long defaultValue) {
		Object value = this.attributes.get(key);
		if (value instanceof Number number) {
			return number.longValue();
		}
		return defaultValue;
	}

	/**
	 * Gets a subclass-specific boolean attribute stored in the shared {@link #attributes} map.
	 * @param key the attribute key
	 * @param defaultValue the value to return when the attribute isn't set
	 * @return the attribute value, or defaultValue
	 */
	protected boolean getBooleanAttribute(String key, boolean defaultValue) {
		Object value = this.attributes.get(key);
		if (value instanceof Boolean bool) {
			return bool;
		}
		return defaultValue;
	}

	/**
	 * Gets a subclass-specific string attribute stored in the shared {@link #attributes} map.
	 * @param key the attribute key
	 * @param defaultValue the value to return when the attribute isn't set
	 * @return the attribute value, or defaultValue
	 */
	protected String getStringAttribute(String key, String defaultValue) {
		Object value = this.attributes.get(key);
		if (value instanceof String string) {
			return string;
		}
		return defaultValue;
	}

	/**
	 * Sets a subclass-specific attribute in the shared {@link #attributes} map.
	 * @param key the attribute key
	 * @param value the value to set
	 */
	protected void setAttribute(String key, Object value) {
		this.attributes.put(key, value);
	}

	/**
	 * Instantiates a new task.
	 */
	protected Task() {
	}

	/**
	 * Instantiates a new task.
	 *
	 * @param comments the comments
	 * @param target the target
	 * @param author the author
	 * @param debugEnabled whether to enable debugging
	 */
	public Task(String comments, String target, String author, boolean debugEnabled) {
		this.comments = comments;
		this.target = target;
		this.author = author;
		this.debugEnabled = debugEnabled;
		if (this.debugEnabled) {
			this.fullLogs = new StringBuffer();
		}
	}

	/**
	 * Instantiates a new task.
	 *
	 * @param comments the comments
	 * @param target the target
	 * @param author the author
	 */
	public Task(String comments, String target, String author) {
		this.comments = comments;
		this.target = target;
		this.author = author;
	}

	/**
	 * Generate the identity of the task. Used by Quartz.
	 * Two tasks with the same identity won't be executed concurrently.
	 *
	 * @return the identity of the task
	 */
	@Transient
	public abstract JobKey getIdentity();


	/*(non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		Task task = (Task) super.clone();
		// super.clone() is a shallow copy, so at this point task.deviceListMembers is the
		// exact same (possibly Hibernate-managed) collection instance as this.deviceListMembers.
		// A DeviceListBasedTask subclass's clone() typically calls setDeviceList(this.getDeviceList())
		// right after, which would then clear/repopulate that shared collection in place --
		// corrupting this task's own device list and confusing Hibernate (which tracks a
		// managed collection by its owning entity) once the clone gets persisted. Give the
		// clone its own empty list so subclasses safely rebuild it on a fresh, independent list.
		task.deviceListMembers = new ArrayList<>();
		task.setScheduleReference(this.scheduleReference);
		task.setScheduleType(this.scheduleType);
		task.setScheduleFactor(this.scheduleFactor);
		task.setPriority(this.priority);
		task.setId(0);
		// Clear debug logs
		task.setDebugEnabled(this.isDebugEnabled());
		// Clear logs
		task.setLogs(new StringBuffer());
		// Reset status
		task.setStatus(Status.NEW);
		// Clear execution date
		task.setExecutionDate(null);
		// A repeated/cloned task is a fresh top-level run, not a child of the original's parent
		task.setParentTaskId(null);
		task.setChildOrder(0);
		task.setCancelRequested(false);
		return task;
	}

	/*(non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof Task)) {
			return false;
		}
		Task other = (Task) obj;
		if (id != other.id) {
			return false;
		}
		return true;
	}

	/**
	 * Gets the next execution date.
	 *
	 * @return the next execution date
	 */
	@Transient
	public Date getNextExecutionDate() {

		int factor = this.scheduleFactor;
		if (factor <= 0) {
			factor = 1;
		}
		int unit = 0;

		switch (this.scheduleType) {
			case AT:
				return this.scheduleReference;
			case HOURLY:
				unit = Calendar.HOUR;
				break;
			case DAILY:
				unit = Calendar.DAY_OF_MONTH;
				break;
			case WEEKLY:
				unit = Calendar.WEEK_OF_YEAR;
				break;
			case MONTHLY:
				unit = Calendar.MONTH;
				break;
			case ASAP:
			default:
				return null;
		}

		if (unit > 0) {
			Calendar inOneMinute = Calendar.getInstance();
			inOneMinute.add(Calendar.MINUTE, 1);
			Calendar targetCalendar = Calendar.getInstance();
			targetCalendar.setTime(this.scheduleReference);
			if (targetCalendar.get(Calendar.YEAR) < inOneMinute.get(Calendar.YEAR)) {
				targetCalendar.set(Calendar.YEAR, inOneMinute.get(Calendar.YEAR) - 1);
			}
			for (int i = 0; i < 100000; i++) {
				if (targetCalendar.after(inOneMinute)) {
					return targetCalendar.getTime();
				}
				targetCalendar.add(unit, factor);
			}
			return targetCalendar.getTime();
		}

		return null;
	}

	/**
	 * Gets the task description.
	 *
	 * @return the task description
	 */
	@Transient
	public abstract String getTaskDescription();

	/*(non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (id ^ (id >>> 32));
		return result;
	}

	/**
	 * Checks if is repeating.
	 *
	 * @return true, if is repeating
	 */
	@Transient
	public boolean isRepeating() {
		switch (this.scheduleType) {
			case ASAP:
			case AT:
				return false;
			case DAILY:
			case MONTHLY:
			case WEEKLY:
			default:
				return true;
		}
	}

	/**
	 * On cancel.
	 */
	public void onCancel() {
	}

	/**
	 * On schedule.
	 */
	public void onSchedule() {

	}

	/**
	 * Prepare.
	 *
	 * @param session the session
	 */
	public void prepare(Session session) {
		// Override to actually do something
	}

	/**
	 * Attaches the full debug logs collected so far (if debugging is enabled) to this task,
	 * as its {@link DebugLog}.
	 */
	public void attachDebugLog() {
		if (this.fullLogs != null) {
			this.debugLog = new DebugLog(this.fullLogs.toString());
		}
	}

	/**
	 * Enable or disable full debugging on this task.
	 *
	 * @param debugEnabled true to enable full debugging
	 */
	public void setDebugEnabled(boolean debugEnabled) {
		this.debugEnabled = debugEnabled;
		if (debugEnabled && this.fullLogs == null) {
			this.fullLogs = new StringBuffer();
		}
	}

	/**
	 * This can return a hash for the task to select a stable runner.
	 * This is used to run all the tasks related to the same device
	 * on the same cluster runner.
	 *
	 * @return the hash or 0
	 */
	@Transient
	public long getRunnerHash() {
		return 0;
	}

	/**
	 * How this task accesses its target device (for tasks implementing
	 * {@link net.netshot.netshot.task.type.DeviceBasedTask}). Not a bean property on purpose
	 * (neither persisted nor serialized).
	 *
	 * @return the device lock policy
	 */
	public DeviceLockPolicy deviceLockPolicy() {
		return DeviceLockPolicy.NONE;
	}

	/**
	 * The {@link net.netshot.netshot.task.engine.TaskLockManager} namespace identifying the type of this task on a device,
	 * used with {@link DeviceLockPolicy#CANCEL_IF_SAME_TYPE}.
	 *
	 * @return the lock namespace
	 */
	public int deviceLockTypeNamespace() {
		return 0;
	}

	/**
	 * Schedule the task.
	 *
	 * @param reference the date reference
	 * @param type the type of schedule
	 * @param factor the scheduling factor
	 */
	public void schedule(Date reference, ScheduleType type, int factor) {
		this.scheduleType = type;
		this.scheduleFactor = factor;
		this.scheduleReference = reference;
	}

	/**
	 * Schedule the task.
	 *
	 * @param minutes Minutes to wait before starting the task
	 */
	public void schedule(int minutes) {
		this.scheduleType = ScheduleType.AT;
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.MINUTE, minutes);
		this.scheduleReference = calendar.getTime();
	}

	/**
	 * Sets the cancelled.
	 *
	 * @param reason the reason for cancellation
	 */
	public void setCancelled(String reason) {
		this.status = Status.CANCELLED;
		this.logger.warn(reason);
	}

	/**
	 * Sets the task as failed.
	 */
	public void setFailed() {
		this.status = Status.FAILURE;
	}

	/**
	 * Sets the logs.
	 *
	 * @param logs the new logs
	 */
	public void setLogs(String logs) {
		this.logs = new StringBuffer(logs);
	}

	/**
	 * Sets the logs.
	 *
	 * @param logs the new logs
	 */
	public void setLogs(StringBuffer logs) {
		this.logs = logs;
	}

	/**
	 * Gets the logs as text.
	 *
	 * @return the logs
	 */
	@Column(name = "log", length = 10000000)
	public String getLog() {
		return logs.toString();
	}

	/**
	 * Sets the log.
	 *
	 * @param log the new log
	 */
	public void setLog(String log) {
		this.logs = new StringBuffer(log);
	}

	/**
	 * Sets the running.
	 */
	public void setRunning() {
		this.logs = new StringBuffer();
		this.status = Status.RUNNING;
		this.executionDate = new Date();
	}

	/**
	 * Sets the scheduled.
	 */
	public void setScheduled() {
		this.status = Status.SCHEDULED;
	}

	/**
	 * Sets the task as waiting.
	 */
	public void setWaiting() {
		this.status = Status.WAITING;
	}

	/**
	 * Copies task execution results to another task instance.
	 * This is used to update a persistent task with results from execution
	 * without triggering cascade loading of associations.
	 *
	 * Subclasses should override this method and call super.copyResultsTo(target)
	 * to copy their own result fields.
	 *
	 * @param newTask the persistent task to copy results to
	 */
	public void copyResultsTo(Task newTask) {
		newTask.setStatus(this.status);
		newTask.setLog(this.getLog());
		newTask.setExecutionDate(this.executionDate);
		newTask.setDebugLog(this.debugLog);
		newTask.setAttributes(new HashMap<>(this.attributes));
	}

	@Override
	public String toString() {
		return "Task " + id + " (type " + this.getClass().getSimpleName() + ", target '" + target
			+ "', author '" + author + "', created on " + creationDate
			+ ", executed on " + executionDate + ", description '" + getTaskDescription()
			+ "', schedule type " + scheduleType + "', schedule factor " + scheduleFactor
			+ ", priority " + priority
			+ ")";
	}

}
