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
package net.netshot.netshot.work;

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
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;

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
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.TaskManager;
import net.netshot.netshot.database.Database;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.DeviceGroup;
import net.netshot.netshot.device.Domain;
import net.netshot.netshot.rest.RestViews.DefaultView;
import net.netshot.netshot.rest.RestViews.HookView;
import net.netshot.netshot.work.tasks.CheckComplianceTask;
import net.netshot.netshot.work.tasks.CheckGroupComplianceTask;
import net.netshot.netshot.work.tasks.CheckGroupSoftwareTask;
import net.netshot.netshot.work.tasks.DiscoverDeviceTypeTask;
import net.netshot.netshot.work.tasks.PurgeDatabaseTask;
import net.netshot.netshot.work.tasks.RunDeviceGroupScriptTask;
import net.netshot.netshot.work.tasks.RunDeviceScriptTask;
import net.netshot.netshot.work.tasks.RunDiagnosticsTask;
import net.netshot.netshot.work.tasks.RunGroupDiagnosticsTask;
import net.netshot.netshot.work.tasks.ScanSubnetsTask;
import net.netshot.netshot.work.tasks.TakeGroupSnapshotTask;
import net.netshot.netshot.work.tasks.TakeSnapshotTask;


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
@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@Type(CheckComplianceTask.class),
	@Type(CheckGroupComplianceTask.class),
	@Type(CheckGroupSoftwareTask.class),
	@Type(DiscoverDeviceTypeTask.class),
	@Type(PurgeDatabaseTask.class),
	@Type(RunDeviceGroupScriptTask.class),
	@Type(RunDeviceScriptTask.class),
	@Type(RunDiagnosticsTask.class),
	@Type(RunGroupDiagnosticsTask.class),
	@Type(ScanSubnetsTask.class),
	@Type(TakeGroupSnapshotTask.class),
	@Type(TakeSnapshotTask.class),
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
			for (Class<?> clazz : Database.listClassesInPackage("net.netshot.netshot.work.tasks")) {
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
	 * out of the tasks' run() loop so it can be unit tested without Quartz/DB/real child
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
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected String author = "";

	/** The change date. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected Date changeDate;

	/** The comments. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected String comments = "";

	/** The creation date. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected Date creationDate = new Date();

	/** Debug enabled. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	protected boolean debugEnabled = false;

	/** The debug log. */
	@Getter(onMethod = @__({
		@OneToOne(orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL),
		@OnDelete(action = OnDeleteAction.SET_NULL)
	}))
	@Setter
	protected DebugLog debugLog;

	/** The execution date. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected Date executionDate;

	/** The id. */
	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected long id;

	/** The log. */
	protected StringBuffer logs = new StringBuffer();

	/** Full debug logs. */
	protected StringBuffer fullLogs = null;

	/** Task context. */
	protected TaskContext logger = new TaskContext() {
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
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected Date scheduleReference = new Date();

	/** The schedule type. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected ScheduleType scheduleType = ScheduleType.ASAP;

	/** The factor (to multiply type). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected int scheduleFactor = 1;

	/** The status. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected Status status = Status.NEW;

	/** The target. */
	@Getter(onMethod = @__({
		@Column(length = 10000),
		@XmlElement, @JsonView(DefaultView.class)
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
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String runnerId;

	/** Task priority. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private int priority = Trigger.DEFAULT_PRIORITY;

	/**
	 * The target device, shared by every task implementing {@link net.netshot.netshot.work.tasks.DeviceBasedTask}.
	 */
	@Getter(onMethod = @__({
		@ManyToOne(fetch = FetchType.LAZY),
		@OnDelete(action = OnDeleteAction.SET_NULL),
		@XmlElement, @JsonView(HookView.class)
	}))
	@Setter
	protected Device device;

	/**
	 * The target device group, shared by every task implementing {@link net.netshot.netshot.work.tasks.GroupBasedTask}.
	 */
	@Getter(onMethod = @__({
		@ManyToOne(fetch = FetchType.LAZY),
		@OnDelete(action = OnDeleteAction.SET_NULL)
	}))
	@Setter
	protected DeviceGroup deviceGroup;

	/**
	 * The domain, shared by every task implementing {@link net.netshot.netshot.work.tasks.DomainBasedTask}.
	 */
	@Getter(onMethod = @__({
		@ManyToOne(fetch = FetchType.LAZY),
		@OnDelete(action = OnDeleteAction.CASCADE)
	}))
	@Setter
	protected Domain domain;

	/** Compatible device driver (script tasks). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected String deviceDriver;

	/** The JS script to execute (script tasks). */
	@Getter(onMethod = @__({
		@Column(length = 10000000),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected String script;

	/** Variable values for the script (script tasks). */
	@Getter(onMethod = @__({
		@JdbcTypeCode(SqlTypes.JSON),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected Map<String, String> userInputValues;

	/** Automatically run a snapshot after successful script execution (script tasks). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected boolean runSnapshot;

	/** Automatically run diagnostics after successful script execution (script tasks). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected boolean runDiagnostics;

	/** Automatically check compliance after successful script execution (script tasks). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected boolean checkCompliance;

	/** Do not automatically start a run diagnostics task (snapshot tasks). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(HookView.class)
	}))
	@Setter
	protected boolean dontRunDiagnostics;

	/** Do not automatically start a check compliance task (snapshot/diagnostics tasks). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(HookView.class)
	}))
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
	 * {@link net.netshot.netshot.device.DeviceGroup}), consistent with how
	 * {@link net.netshot.netshot.device.DeviceGroup#cachedMemberships} owns its
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
	 * {@link net.netshot.netshot.work.tasks.GroupBasedTask} and
	 * {@link net.netshot.netshot.work.tasks.DeviceListBasedTask}: either the cached
	 * devices of its {@link #deviceGroup}, or its one-time device list, whichever is set
	 * (the two are mutually exclusive, enforced at the REST layer). Assumes prepare(),
	 * or the caller, has already initialized whichever source applies.
	 *
	 * @return the ordered list of target devices, empty if neither source is set
	 */
	@Transient
	protected List<Device> getTargetDevices() {
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
		@Column(name = "parent_task_id"),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected Long parentTaskId;

	/** Position of this task among its siblings, when it has a parent task. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected int childOrder;

	/** Parallel vs sequential scheduling of child tasks (group-based tasks only). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected ScheduleMode scheduleMode = ScheduleMode.PARALLEL;

	/** Whether to stop scheduling further child tasks after one fails (sequential mode only). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected boolean stopOnFailure;

	/**
	 * Set by a cancellation request on a task that is already RUNNING (sequential group
	 * tasks spend their whole execution in RUNNING, so the normal SCHEDULED-only cancel
	 * path doesn't apply) -- polled by the sequential scheduling loop between child tasks.
	 */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected boolean cancelRequested;

	/**
	 * Looks up any child tasks already persisted for this parent, ordered by position among
	 * siblings -- used to resume a group-based task's run() after it gets re-executed from
	 * scratch (e.g. a Netshot restart mid-run picks the still-RUNNING parent back up via
	 * {@code TaskManager.rescheduleAll()}/{@code reassignOrphanTasks()} and calls run() again).
	 * Empty on a fresh (not-yet-started) run.
	 *
	 * @return the ordered list of already-persisted children, empty if none exist yet
	 */
	protected List<Task> reloadExistingChildren() {
		Session session = Database.getSession(true);
		List<Task> children;
		try {
			children = session
				.createQuery("from Task t where t.parentTaskId = :id order by t.childOrder asc", Task.class)
				.setParameter("id", this.id)
				.list();
		}
		finally {
			session.close();
		}
		if (!children.isEmpty()) {
			this.logger.info("Resuming from {} previously pre-created child task(s) "
				+ "(this run was interrupted, e.g. by a Netshot restart).", children.size());
		}
		return children;
	}

	/**
	 * Pre-creates every child of this group-based task in a single transaction, all in
	 * {@link Status#DELAYED} -- i.e. persisted (with their device/config already set, and
	 * {@code parentTaskId}/{@code childOrder}/{@code priority} stamped by position in the given
	 * list) but deliberately not yet scheduled. This makes the parent's total child count known
	 * upfront (the frontend can show stable progress from the very start, since every child row
	 * already exists), and means a crash before this method returns leaves no partial rows behind
	 * (the whole batch is one transaction, so a fresh run() re-derives and re-persists it from
	 * scratch next time, exactly as if this were the first attempt).
	 *
	 * <p>The actual scheduling/waiting is done afterwards, by {@link #orchestrateChildren(List)}.
	 *
	 * @param children the children to pre-create, in order (not yet persisted)
	 */
	protected void preCreateChildren(List<Task> children) {
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			int order = 0;
			for (Task child : children) {
				child.setParentTaskId(this.getId());
				child.setChildOrder(order);
				child.setPriority(this.getPriority());
				child.setStatus(Status.DELAYED);
				session.persist(child);
				order++;
			}
			session.getTransaction().commit();
		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			throw e;
		}
		finally {
			session.close();
		}
		this.logger.info("Pre-created {} child task(s), in DELAYED status.", children.size());
	}

	/**
	 * Promotes the given still-{@link Status#DELAYED} child (schedules it) and blocks, polling
	 * the database every few seconds, until it reaches a terminal status.
	 *
	 * @param child the child task to promote (already persisted, in DELAYED status)
	 * @return the terminal status the child task reached
	 * @throws SchedulerException if the child task couldn't be scheduled
	 * @throws InterruptedException if the polling wait is interrupted
	 */
	protected Status promoteChildAndWait(Task child) throws SchedulerException, InterruptedException {
		TaskManager.addTask(child);
		return this.pollChildUntilTerminal(child.getId());
	}

	/**
	 * Bulk-cancels every child from the given position onward that's still {@link Status#DELAYED}
	 * -- used once a failure has occurred with stop-on-failure enabled (or cancellation was
	 * requested), so the remaining not-run devices still show up as CANCELLED rows in the child
	 * task list rather than being silently left DELAYED forever.
	 *
	 * @param children the full ordered list of children (as returned by
	 *        {@link #preCreateChildren(List)}/{@link #reloadExistingChildren()})
	 * @param fromIndex the index (inclusive) to start cancelling from
	 */
	protected void cancelRemainingDelayedChildren(List<Task> children, int fromIndex) {
		List<Long> ids = new ArrayList<>();
		for (int i = fromIndex; i < children.size(); i++) {
			if (children.get(i).getStatus() == Status.DELAYED) {
				ids.add(children.get(i).getId());
			}
		}
		if (ids.isEmpty()) {
			return;
		}
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			session.createMutationQuery(
				"update Task t set t.status = :cancelled where t.id in :ids and t.status = :delayed")
				.setParameter("cancelled", Status.CANCELLED)
				.setParameter("delayed", Status.DELAYED)
				.setParameter("ids", ids)
				.executeUpdate();
			session.getTransaction().commit();
		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			throw e;
		}
		finally {
			session.close();
		}
		this.logger.info("Cancelled {} remaining child task(s) that hadn't started yet.", ids.size());
	}

	/**
	 * Builds a short "device X (id Y)" fragment identifying the device a child task targets,
	 * for log messages -- falling back to just the target string if the child isn't
	 * device-based (shouldn't happen for the per-device children of a group-based task, but
	 * keeps this safe to call generically).
	 *
	 * @param child the child task
	 * @return a human-readable description of the device the child targets
	 */
	private static String describeChildDevice(Task child) {
		Device device = child.getDevice();
		if (device == null) {
			return child.getTarget();
		}
		return String.format("%s (device #%d)", device.getName(), device.getId());
	}

	/**
	 * Orchestrates the given pre-created children (from {@link #preCreateChildren(List)}, or
	 * {@link #reloadExistingChildren()} when resuming an interrupted run) according to this
	 * task's {@link #scheduleMode}, honoring stop-on-failure and cancellation, and returns the
	 * parent's final status. Shared by every group-based task that spawns per-device children
	 * (TakeGroupSnapshotTask, RunDeviceGroupScriptTask, RunGroupDiagnosticsTask), regardless of
	 * whether this is a fresh run or a resumed one: a child already past DELAYED (still live, or
	 * already terminal) is left alone or polled rather than re-promoted, so resuming after a
	 * restart is just "start the same walk over" -- there's no separate resume-specific logic.
	 *
	 * @param children the full ordered list of this task's children
	 * @return the final status this task should report
	 */
	protected Status orchestrateChildren(List<Task> children) {
		if (this.getScheduleMode() == ScheduleMode.SEQUENTIAL) {
			return this.orchestrateSequentialChildren(children);
		}
		return this.orchestrateParallelChildren(children);
	}

	/**
	 * Marks a still-DELAYED child as FAILURE because it could not be scheduled (e.g. a
	 * Quartz or persistence error while promoting it) -- used by parallel orchestration so
	 * an unschedulable child is reported as a concrete failure instead of being left
	 * forever in DELAYED status.
	 *
	 * @param child the child task that failed to schedule
	 */
	private void markChildAsSchedulingFailure(Task child) {
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			session.createMutationQuery(
				"update Task t set t.status = :failure where t.id = :id and t.status = :delayed")
				.setParameter("failure", Status.FAILURE)
				.setParameter("delayed", Status.DELAYED)
				.setParameter("id", child.getId())
				.executeUpdate();
			session.getTransaction().commit();
		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			log.error("Task {}. Error while marking child task {} as failed after a scheduling error.",
				this.getId(), child.getId(), e);
		}
		finally {
			session.close();
		}
		child.setStatus(Status.FAILURE);
	}

	/**
	 * Schedules every still-DELAYED child at once (rather than one at a time), then blocks --
	 * staying RUNNING -- until they've all reached a terminal status, polling the whole set
	 * together on each interval instead of one child at a time (unlike sequential mode, which
	 * only ever has one in flight). Cancellation stops scheduling any not-yet-promoted child
	 * (the remainder is cancelled, same as sequential) and, once all promotion attempts are
	 * done, stops *waiting* for the ones already running -- they keep going independently
	 * (nothing under this task's control can stop an already-running child), the parent just
	 * no longer blocks on them.
	 *
	 * <p>A child task actually failing (as opposed to failing to be scheduled in the first
	 * place) does not make this task report FAILURE -- for a parallel group-based task, a
	 * per-device failure is that device's own problem, not this task's; this task's job is
	 * only to schedule the children. So the only thing that turns final status into FAILURE
	 * here is a scheduling error (Quartz/persistence failure while promoting a child).
	 *
	 * @param children the full ordered list of this task's children
	 * @return the final status this task should report
	 */
	private Status orchestrateParallelChildren(List<Task> children) {
		this.logger.info("Orchestrating {} child task(s) in parallel mode.", children.size());
		boolean cancelHonored = false;
		boolean schedulingError = false;
		for (int i = 0; i < children.size(); i++) {
			Task child = children.get(i);
			if (child.getStatus() != Status.DELAYED) {
				continue;
			}
			if (this.isCancelRequestedFresh()) {
				this.logger.info("Cancellation requested: not scheduling the remaining {} device(s).",
					children.size() - i);
				this.cancelRemainingDelayedChildren(children, i);
				cancelHonored = true;
				break;
			}
			this.logger.info("Scheduling child task {} for {}.", child.getId(), describeChildDevice(child));
			try {
				TaskManager.addTask(child);
			}
			catch (Exception e) {
				log.error("Task {}. Error while scheduling child task {} for {}.",
					this.getId(), child.getId(), describeChildDevice(child), e);
				this.logger.error("Error while scheduling child task {} for {}.",
					child.getId(), describeChildDevice(child));
				schedulingError = true;
				this.markChildAsSchedulingFailure(child);
			}
		}

		Map<Long, Task> pending = new HashMap<>();
		int succeeded = 0;
		int failed = 0;
		for (Task child : children) {
			if (isTerminalStatus(child.getStatus())) {
				if (child.getStatus() == Status.FAILURE) {
					failed++;
				}
				else if (child.getStatus() == Status.SUCCESS) {
					succeeded++;
				}
			}
			else if (child.getStatus() != Status.DELAYED) {
				// Promoted above (or already running from before a restart) -- wait for it.
				pending.put(child.getId(), child);
			}
			// A still-DELAYED child here was left un-promoted due to cancellation above, and
			// already marked CANCELLED by cancelRemainingDelayedChildren -- nothing to wait for.
		}

		int intervalMs = Netshot.getConfig(
			"netshot.tasks.sequentialpollintervalms", 3000, 500, 60000);
		try {
			while (!pending.isEmpty() && !cancelHonored) {
				Thread.sleep(intervalMs);
				if (this.isCancelRequestedFresh()) {
					this.logger.info("Cancellation requested: no longer waiting for the {} still-running "
						+ "device(s) (they will keep running to completion independently).", pending.size());
					cancelHonored = true;
					break;
				}
				Session session = Database.getSession(true);
				try {
					List<Object[]> rows = session
						.createQuery("select t.id, t.status from Task t where t.id in :ids", Object[].class)
						.setParameter("ids", pending.keySet())
						.list();
					for (Object[] row : rows) {
						Status childStatus = (Status) row[1];
						if (isTerminalStatus(childStatus)) {
							Long childId = (Long) row[0];
							Task child = pending.remove(childId);
							if (childStatus == Status.FAILURE) {
								failed++;
								this.logger.warn("Child task {} for {} failed.", childId, describeChildDevice(child));
							}
							else if (childStatus == Status.SUCCESS) {
								succeeded++;
							}
						}
					}
				}
				finally {
					session.close();
				}
			}
		}
		catch (InterruptedException e) {
			log.error("Task {}. Interrupted while waiting for child tasks.", this.getId(), e);
			this.logger.error("Interrupted while waiting for child tasks.");
		}

		Status finalStatus = schedulingError ? Status.FAILURE
			: (cancelHonored ? Status.CANCELLED : Status.SUCCESS);
		this.logger.info("Parallel scheduling finished: {} succeeded, {} failed, out of {} device(s). Final status: {}.",
			succeeded, failed, children.size(), finalStatus);
		return finalStatus;
	}

	private Status orchestrateSequentialChildren(List<Task> children) {
		this.logger.info("Orchestrating {} child task(s) in sequential mode (stop on failure: {}).",
			children.size(), this.isStopOnFailure());
		boolean anyFailed = false;
		boolean cancelHonored = false;
		boolean schedulingError = false;
		int succeeded = 0;
		int failed = 0;
		Long lastFailedChildId = null;
		try {
			for (int i = 0; i < children.size(); i++) {
				Task child = children.get(i);
				if (isTerminalStatus(child.getStatus())) {
					if (child.getStatus() == Status.FAILURE) {
						anyFailed = true;
						failed++;
						lastFailedChildId = child.getId();
					}
					else if (child.getStatus() == Status.SUCCESS) {
						succeeded++;
					}
					continue;
				}
				boolean cancellationRequested = this.isCancelRequestedFresh();
				SequentialScheduling.NextAction next =
					SequentialScheduling.decideNextAction(anyFailed, this.isStopOnFailure(), cancellationRequested);
				if (next == SequentialScheduling.NextAction.STOP) {
					cancelHonored = cancelHonored || cancellationRequested;
					if (cancellationRequested) {
						this.logger.info("Cancellation requested: stopping sequential scheduling at device {}/{}.",
							i + 1, children.size());
					}
					else {
						this.logger.warn("Stopping sequential scheduling: child task {} failed and "
							+ "stop-on-failure is enabled.", lastFailedChildId);
					}
					this.cancelRemainingDelayedChildren(children, i);
					break;
				}
				Status childStatus;
				if (child.getStatus() == Status.DELAYED) {
					this.logger.info("Scheduling child task {} for {} (sequential mode, {}/{}).",
						child.getId(), describeChildDevice(child), i + 1, children.size());
					childStatus = this.promoteChildAndWait(child);
				}
				else {
					this.logger.info("Resuming tracking of child task {} for {} (sequential mode, after restart).",
						child.getId(), describeChildDevice(child));
					childStatus = this.pollChildUntilTerminal(child.getId());
				}
				if (childStatus == Status.FAILURE) {
					anyFailed = true;
					failed++;
					lastFailedChildId = child.getId();
					this.logger.warn("Child task {} for {} failed.", child.getId(), describeChildDevice(child));
				}
				else if (childStatus == Status.SUCCESS) {
					succeeded++;
				}
			}
		}
		catch (Exception e) {
			log.error("Task {}. Error during sequential child scheduling.", this.getId(), e);
			this.logger.error("Error during sequential scheduling: {}", e.getMessage());
			schedulingError = true;
		}
		Status finalStatus = SequentialScheduling.decideFinalStatus(anyFailed, this.isStopOnFailure(), cancelHonored, schedulingError);
		this.logger.info("Sequential scheduling finished: {} succeeded, {} failed, out of {} device(s). Final status: {}.",
			succeeded, failed, children.size(), finalStatus);
		return finalStatus;
	}

	/**
	 * Polls the database every few seconds until the given task reaches a terminal
	 * status (SUCCESS, FAILURE or CANCELLED).
	 *
	 * @param taskId the ID of the task to poll
	 * @return the terminal status reached
	 * @throws InterruptedException if the polling wait is interrupted
	 */
	protected Status pollChildUntilTerminal(long taskId) throws InterruptedException {
		int intervalMs = Netshot.getConfig(
			"netshot.tasks.sequentialpollintervalms", 3000, 500, 60000);
		while (true) {
			Thread.sleep(intervalMs);
			Session session = Database.getSession(true);
			try {
				Status childStatus = session
					.createQuery("select t.status from Task t where t.id = :id", Status.class)
					.setParameter("id", taskId)
					.uniqueResult();
				if (childStatus == null || isTerminalStatus(childStatus)) {
					return childStatus;
				}
			}
			finally {
				session.close();
			}
		}
	}

	/**
	 * Checks whether cancellation of this (running, sequential-mode) task has been
	 * requested, re-reading the flag directly from the database rather than relying on
	 * this possibly-stale in-memory instance.
	 *
	 * @return true if cancellation was requested
	 */
	@Transient
	protected boolean isCancelRequestedFresh() {
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
	 * Checks whether the given status is a terminal one.
	 * @param status the status
	 * @return true if the status is SUCCESS, FAILURE or CANCELLED
	 */
	protected static boolean isTerminalStatus(Status status) {
		return status == Status.SUCCESS || status == Status.FAILURE || status == Status.CANCELLED;
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
	@XmlElement
	@JsonView(DefaultView.class)
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
	@XmlElement
	@JsonView(DefaultView.class)
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
	@XmlElement
	@JsonView(DefaultView.class)
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
	 * Run.
	 */
	public abstract void run();

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
	@XmlElement
	@JsonView(DefaultView.class)
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
