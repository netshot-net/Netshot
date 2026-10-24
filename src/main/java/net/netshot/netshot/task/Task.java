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
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;

import org.quartz.JobKey;
import org.quartz.Trigger;

import lombok.Builder;
import net.netshot.netshot.task.type.TaskSpec;

/**
 * A task, as stored: what to do (the {@link TaskSpec specification} of its type), when (the schedule)
 * and where it stands (status, log of the last run...). A task is immutable: its execution state lives
 * in a {@link TaskRun}, saved back once the run is over, and the status changes are made by the
 * {@link TaskRepository}.
 *
 * @param id the ID (0 for a task not stored yet)
 * @param version the version (incremented on each change)
 * @param author who created the task
 * @param comments the comments
 * @param creationDate when the task was created
 * @param changeDate when the task was changed last
 * @param executionDate when the task (last) started to run
 * @param scheduleReference the reference date of the schedule
 * @param scheduleType the type of schedule
 * @param scheduleFactor the factor of the schedule (e.g. 2 for every 2 days)
 * @param priority the priority (1 to 9)
 * @param status the status
 * @param target the description of the target
 * @param runnerId the ID of the cluster member assigned to run the task
 * @param debugEnabled true to keep the full debug log
 * @param parentTaskId the ID of the parent task (null for none)
 * @param childOrder the order of the task among the children of the parent task
 * @param scheduleMode how the child tasks are run (for the tasks with children)
 * @param stopOnFailure true to stop the sequential child tasks on the first failure
 * @param cancelRequested true when cancellation was requested while the task is running
 * @param log the log of the last run
 * @param spec what the task does, depending on its type
 */
@Builder(builderMethodName = "defaults", toBuilder = true)
public record Task(long id, int version, String author, String comments, Instant creationDate, Instant changeDate,
	Instant executionDate, Instant scheduleReference, ScheduleType scheduleType, int scheduleFactor, int priority,
	Status status, String target, String runnerId, boolean debugEnabled, Long parentTaskId, int childOrder,
	ScheduleMode scheduleMode, boolean stopOnFailure, boolean cancelRequested, String log, TaskSpec spec) {

	public enum ScheduleType {
		ASAP,
		AT,
		DAILY,
		MONTHLY,
		WEEKLY,
		HOURLY,
	}

	/**
	 * How the child tasks of a task are run.
	 */
	public enum ScheduleMode {
		PARALLEL,
		SEQUENTIAL,
	}

	/**
	 * How a task gets exclusive access to its target device.
	 */
	public enum DeviceLockPolicy {
		/** No lock. */
		NONE,
		/** Cancelled if a task of the same type already works on the device, then waits for the device. */
		CANCEL_IF_SAME_TYPE,
		/** Waits for the device. */
		WAIT,
	}

	public enum Status {
		CANCELLED,
		FAILURE,
		NEW,
		RUNNING,
		SCHEDULED,
		SUCCESS,
		WAITING,
		DELAYED
	}

	/**
	 * The decisions of the sequential scheduling of child tasks.
	 */
	public static final class SequentialScheduling {

		public enum NextAction {
			CONTINUE,
			STOP,
		}

		/**
		 * Decides whether to schedule the next child task.
		 *
		 * @param lastChildFailed true if the last child task failed
		 * @param stopOnFailure true to stop on the first failure
		 * @param cancelRequested true if the cancellation was requested
		 * @return the next action
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
		 * Decides the final status of the parent task.
		 *
		 * @param anyChildFailed true if a child task failed
		 * @param stopOnFailure true to stop on the first failure
		 * @param cancelHonored true if the cancellation was honored
		 * @param schedulingError true if a child task couldn't be scheduled
		 * @return the final status
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

	/**
	 * Starts building a new task, with the default values (to be run as soon as possible).
	 *
	 * @param spec what the task does
	 * @param comments the comments
	 * @param target the description of the target
	 * @param author who creates the task
	 * @return the builder
	 */
	public static TaskBuilder builder(TaskSpec spec, String comments, String target, String author) {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		return defaults()
			.spec(spec)
			.comments(comments)
			.target(target)
			.author(author)
			.creationDate(now)
			.scheduleReference(now)
			.scheduleType(ScheduleType.ASAP)
			.scheduleFactor(1)
			.priority(Trigger.DEFAULT_PRIORITY)
			.status(Status.NEW)
			.scheduleMode(ScheduleMode.PARALLEL)
			.log("");
	}

	/**
	 * Builds a new task, with the default values (to be run as soon as possible).
	 *
	 * @param spec what the task does
	 * @param comments the comments
	 * @param target the description of the target
	 * @param author who creates the task
	 * @return the new task
	 */
	public static Task of(TaskSpec spec, String comments, String target, String author) {
		return builder(spec, comments, target, author).build();
	}

	/**
	 * Gets the type name of the task (the name of the class of its specification, e.g. TakeSnapshotTask).
	 *
	 * @return the type name
	 */
	public String typeName() {
		return this.spec.typeName();
	}

	/**
	 * Gets the description of the type of task.
	 *
	 * @return the description
	 */
	public String description() {
		return this.spec.description();
	}

	/**
	 * Gets the identity of the task in the schedulers.
	 *
	 * @return the identity
	 */
	public JobKey identity() {
		return new JobKey(String.format("Task_%d", this.id), this.spec.identityGroup());
	}

	/**
	 * Gets the next execution date of the task.
	 *
	 * @return the next execution date, null for as soon as possible
	 */
	public Date nextExecutionDate() {
		int factor = this.scheduleFactor;
		if (factor <= 0) {
			factor = 1;
		}
		int unit = 0;
		switch (this.scheduleType) {
			case AT:
				return this.scheduleReference == null ? null : Date.from(this.scheduleReference);
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
		Calendar inOneMinute = Calendar.getInstance();
		inOneMinute.add(Calendar.MINUTE, 1);
		Calendar targetCalendar = Calendar.getInstance();
		targetCalendar.setTime(Date.from(this.scheduleReference));
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

	/**
	 * Tells whether the task is repeating.
	 *
	 * @return true if the task is repeating
	 */
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
	 * Gets the hash used to choose the runner of the task.
	 *
	 * @return the hash
	 */
	public long runnerHash() {
		return this.spec.runnerHash();
	}

	/**
	 * Gets how the task gets exclusive access to its target device.
	 *
	 * @return the lock policy
	 */
	public DeviceLockPolicy deviceLockPolicy() {
		return this.spec.deviceLockPolicy();
	}

	/**
	 * Gets the next occurrence of a repeating task: the same task, not stored yet, waiting to be scheduled.
	 *
	 * @return the next occurrence
	 */
	public Task nextOccurrence() {
		return this.toBuilder()
			.id(0)
			.version(0)
			.changeDate(null)
			.executionDate(null)
			.status(Status.NEW)
			.runnerId(null)
			// A repeated task is a fresh top-level run, not a child of the original's parent
			.parentTaskId(null)
			.childOrder(0)
			.cancelRequested(false)
			.log("")
			.build();
	}

	/**
	 * Copies the task, scheduled at a given date.
	 *
	 * @param reference the reference date
	 * @param type the type of schedule
	 * @param factor the factor of the schedule
	 * @return the scheduled task
	 */
	public Task scheduled(Instant reference, ScheduleType type, int factor) {
		return this.toBuilder().scheduleReference(reference).scheduleType(type).scheduleFactor(factor).build();
	}

	/**
	 * Copies the task, scheduled in some minutes.
	 *
	 * @param minutes the number of minutes from now
	 * @return the scheduled task
	 */
	public Task scheduledIn(int minutes) {
		return this.toBuilder()
			.scheduleType(ScheduleType.AT)
			.scheduleReference(Instant.now().plus(minutes, ChronoUnit.MINUTES))
			.build();
	}

	/**
	 * Copies the task with another ID.
	 *
	 * @param newId the ID
	 * @return the task with the ID
	 */
	public Task withId(long newId) {
		return this.toBuilder().id(newId).build();
	}

	/**
	 * Copies the task with another status.
	 *
	 * @param newStatus the status
	 * @return the task with the status
	 */
	public Task withStatus(Status newStatus) {
		return this.toBuilder().status(newStatus).build();
	}

	@Override
	public String toString() {
		return "Task " + id + " (type " + this.typeName() + ", target '" + target
			+ "', author '" + author + "', created on " + (creationDate == null ? null : Date.from(creationDate))
			+ ", executed on " + (executionDate == null ? null : Date.from(executionDate)) + ", description '" + description()
			+ "', schedule type " + scheduleType + "', schedule factor " + scheduleFactor
			+ ", priority " + priority
			+ ")";
	}
}
