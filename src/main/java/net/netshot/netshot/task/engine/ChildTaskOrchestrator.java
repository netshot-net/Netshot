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
package net.netshot.netshot.task.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;
import static net.netshot.netshot.platform.database.schema.Tables.TASK;

import org.quartz.SchedulerException;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.ScheduleMode;
import net.netshot.netshot.task.Task.SequentialScheduling;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskRepository;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.type.DeviceBasedTask;

/**
 * Orchestrates the child tasks of a group-based (or otherwise fan-out) parent task: pre-creates
 * them in DELAYED status, promotes them (all at once or one at a time depending on the parent's
 * schedule mode), and waits for them to reach a terminal status.
 */
@Slf4j
public class ChildTaskOrchestrator {

	/** Interval (ms) between two checks of the child tasks run in sequence. */
	static final Setting<Integer> SEQUENTIAL_POLL_INTERVAL = Setting.integer("netshot.tasks.sequentialpollintervalms")
		.defaultValue(3000).range(500, 60000).live();

	/**
	 * Hands a child task over to the task scheduler.
	 */
	@FunctionalInterface
	public interface ChildScheduler {
		/**
		 * Schedules the given (persisted) child task.
		 * @param child the child task
		 * @throws SchedulerException in case of scheduling error
		 */
		void schedule(Task child) throws SchedulerException;
	}

	private final ChildScheduler scheduler;

	private final TaskRepository repository = new TaskRepository();

	private final Transactions transactions = new Transactions();

	/**
	 * Creates the orchestrator.
	 * @param scheduler the scheduler to promote children with
	 */
	public ChildTaskOrchestrator(ChildScheduler scheduler) {
		this.scheduler = scheduler;
	}

	/**
	 * Looks up any child tasks already persisted for this parent, ordered by position among
	 * siblings -- used to resume a group-based task's run() after it gets re-executed from
	 * scratch (e.g. a Netshot restart mid-run picks the still-RUNNING parent back up via
	 * {@code TaskManager.rescheduleAll()}/{@code reassignOrphanTasks()} and calls run() again).
	 * Empty on a fresh (not-yet-started) run.
	 *
	 * @param parent the parent task
	 * @return the ordered list of already-persisted children, empty if none exist yet
	 */
	public List<Task> reloadExistingChildren(TaskRun parent) {
		List<Task> children = new ArrayList<>(
			this.transactions.read(dsl -> this.repository.findChildren(dsl, parent.getTask().id())));
		if (!children.isEmpty()) {
			parent.logger.info("Resuming from {} previously pre-created child task(s) "
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
	 * @param parent the parent task
	 * @return the stored children, in order
	 */
	public List<Task> preCreateChildren(TaskRun parent, List<Task> children) {
		Task parentTask = parent.getTask();
		List<Task> stored = this.transactions.write(dsl -> {
			List<Task> added = new ArrayList<>();
			int order = 0;
			for (Task child : children) {
				added.add(this.repository.add(dsl, child.toBuilder()
					.parentTaskId(parentTask.id())
					.childOrder(order)
					.priority(parentTask.priority())
					.status(Status.DELAYED)
					.build()));
				order++;
			}
			return added;
		});
		parent.logger.info("Pre-created {} child task(s), in DELAYED status.", children.size());
		return new ArrayList<>(stored);
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
	public Status promoteChildAndWait(Task child) throws SchedulerException, InterruptedException {
		this.scheduler.schedule(child);
		return this.pollChildUntilTerminal(child.id());
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
	 * @param parent the parent task
	 */
	public void cancelRemainingDelayedChildren(TaskRun parent, List<Task> children, int fromIndex) {
		List<Long> ids = new ArrayList<>();
		for (int i = fromIndex; i < children.size(); i++) {
			if (children.get(i).status() == Status.DELAYED) {
				ids.add(children.get(i).id());
			}
		}
		if (ids.isEmpty()) {
			return;
		}
		this.transactions.write(dsl -> this.repository.setStatus(dsl, TASK.ID.in(ids), Status.CANCELLED, Status.DELAYED));
		parent.logger.info("Cancelled {} remaining child task(s) that hadn't started yet.", ids.size());
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
	private String describeChildDevice(Task child) {
		if (!(child.spec() instanceof DeviceBasedTask deviceTask) || deviceTask.deviceId() == null) {
			return child.target();
		}
		String name = this.transactions.read(dsl -> dsl.select(DEVICE.NAME).from(DEVICE)
			.where(DEVICE.ID.eq(deviceTask.deviceId())).fetchOne(DEVICE.NAME));
		if (name == null) {
			return child.target();
		}
		return String.format("%s (device #%d)", name, deviceTask.deviceId());
	}

	// Gets whether the cancellation of the parent task was requested (fresh from the database)
	private boolean isCancelRequested(TaskRun parent) {
		return this.transactions.read(dsl -> this.repository.isCancelRequested(dsl, parent.getTask().id()));
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
	 * @param parent the parent task
	 * @return the final status this task should report
	 */
	public Status orchestrateChildren(TaskRun parent, List<Task> children) {
		if (parent.getTask().scheduleMode() == ScheduleMode.SEQUENTIAL) {
			return this.orchestrateSequentialChildren(parent, children);
		}
		return this.orchestrateParallelChildren(parent, children);
	}

	/**
	 * Marks a still-DELAYED child as FAILURE because it could not be scheduled (e.g. a
	 * Quartz or persistence error while promoting it) -- used by parallel orchestration so
	 * an unschedulable child is reported as a concrete failure instead of being left
	 * forever in DELAYED status.
	 *
	 * @param child the child task that failed to schedule
	 * @param parent the parent task
	 * @return the child task, failed
	 */
	private Task markChildAsSchedulingFailure(TaskRun parent, Task child) {
		try {
			this.transactions.write(dsl -> this.repository.setStatus(dsl, child.id(), Status.FAILURE, Status.DELAYED));
		}
		catch (Exception e) {
			log.error("Task {}. Error while marking child task {} as failed after a scheduling error.",
				parent.getTask().id(), child.id(), e);
		}
		return child.withStatus(Status.FAILURE);
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
	 * @param parent the parent task
	 * @return the final status this task should report
	 */
	private Status orchestrateParallelChildren(TaskRun parent, List<Task> children) {
		parent.logger.info("Orchestrating {} child task(s) in parallel mode.", children.size());
		boolean cancelHonored = false;
		boolean schedulingError = false;
		for (int i = 0; i < children.size(); i++) {
			Task child = children.get(i);
			if (child.status() != Status.DELAYED) {
				continue;
			}
			if (this.isCancelRequested(parent)) {
				parent.logger.info("Cancellation requested: not scheduling the remaining {} device(s).",
					children.size() - i);
				this.cancelRemainingDelayedChildren(parent, children, i);
				cancelHonored = true;
				break;
			}
			parent.logger.info("Scheduling child task {} for {}.", child.id(), this.describeChildDevice(child));
			try {
				this.scheduler.schedule(child);
			}
			catch (Exception e) {
				log.error("Task {}. Error while scheduling child task {} for {}.",
					parent.getTask().id(), child.id(), this.describeChildDevice(child), e);
				parent.logger.error("Error while scheduling child task {} for {}.",
					child.id(), this.describeChildDevice(child));
				schedulingError = true;
				children.set(i, this.markChildAsSchedulingFailure(parent, child));
			}
		}

		Map<Long, Task> pending = new HashMap<>();
		int succeeded = 0;
		int failed = 0;
		for (Task child : children) {
			if (isTerminalStatus(child.status())) {
				if (child.status() == Status.FAILURE) {
					failed++;
				}
				else if (child.status() == Status.SUCCESS) {
					succeeded++;
				}
			}
			else if (child.status() != Status.DELAYED) {
				// Promoted above (or already running from before a restart) -- wait for it.
				pending.put(child.id(), child);
			}
			// A still-DELAYED child here was left un-promoted due to cancellation above, and
			// already marked CANCELLED by cancelRemainingDelayedChildren -- nothing to wait for.
		}

		int intervalMs = Netshot.config().current().get(SEQUENTIAL_POLL_INTERVAL);
		try {
			while (!pending.isEmpty() && !cancelHonored) {
				Thread.sleep(intervalMs);
				if (this.isCancelRequested(parent)) {
					parent.logger.info("Cancellation requested: no longer waiting for the {} still-running "
						+ "device(s) (they will keep running to completion independently).", pending.size());
					cancelHonored = true;
					break;
				}
				Map<Long, Status> statuses = this.transactions.read(dsl -> this.repository.findStatuses(dsl, pending.keySet()));
				for (Map.Entry<Long, Status> row : statuses.entrySet()) {
					Status childStatus = row.getValue();
					if (isTerminalStatus(childStatus)) {
						Long childId = row.getKey();
						Task child = pending.remove(childId);
						if (childStatus == Status.FAILURE) {
							failed++;
							parent.logger.warn("Child task {} for {} failed.", childId, this.describeChildDevice(child));
						}
						else if (childStatus == Status.SUCCESS) {
							succeeded++;
						}
					}
				}
			}
		}
		catch (InterruptedException e) {
			log.error("Task {}. Interrupted while waiting for child tasks.", parent.getTask().id(), e);
			parent.logger.error("Interrupted while waiting for child tasks.");
		}

		Status finalStatus = schedulingError ? Status.FAILURE
			: (cancelHonored ? Status.CANCELLED : Status.SUCCESS);
		parent.logger.info("Parallel scheduling finished: {} succeeded, {} failed, out of {} device(s). Final status: {}.",
			succeeded, failed, children.size(), finalStatus);
		return finalStatus;
	}

	private Status orchestrateSequentialChildren(TaskRun parent, List<Task> children) {
		parent.logger.info("Orchestrating {} child task(s) in sequential mode (stop on failure: {}).",
			children.size(), parent.getTask().stopOnFailure());
		boolean anyFailed = false;
		boolean cancelHonored = false;
		boolean schedulingError = false;
		int succeeded = 0;
		int failed = 0;
		Long lastFailedChildId = null;
		try {
			for (int i = 0; i < children.size(); i++) {
				Task child = children.get(i);
				if (isTerminalStatus(child.status())) {
					if (child.status() == Status.FAILURE) {
						anyFailed = true;
						failed++;
						lastFailedChildId = child.id();
					}
					else if (child.status() == Status.SUCCESS) {
						succeeded++;
					}
					continue;
				}
				boolean cancellationRequested = this.isCancelRequested(parent);
				SequentialScheduling.NextAction next =
					SequentialScheduling.decideNextAction(anyFailed, parent.getTask().stopOnFailure(), cancellationRequested);
				if (next == SequentialScheduling.NextAction.STOP) {
					cancelHonored = cancelHonored || cancellationRequested;
					if (cancellationRequested) {
						parent.logger.info("Cancellation requested: stopping sequential scheduling at device {}/{}.",
							i + 1, children.size());
					}
					else {
						parent.logger.warn("Stopping sequential scheduling: child task {} failed and "
							+ "stop-on-failure is enabled.", lastFailedChildId);
					}
					this.cancelRemainingDelayedChildren(parent, children, i);
					break;
				}
				Status childStatus;
				if (child.status() == Status.DELAYED) {
					parent.logger.info("Scheduling child task {} for {} (sequential mode, {}/{}).",
						child.id(), this.describeChildDevice(child), i + 1, children.size());
					childStatus = this.promoteChildAndWait(child);
				}
				else {
					parent.logger.info("Resuming tracking of child task {} for {} (sequential mode, after restart).",
						child.id(), this.describeChildDevice(child));
					childStatus = this.pollChildUntilTerminal(child.id());
				}
				if (childStatus == Status.FAILURE) {
					anyFailed = true;
					failed++;
					lastFailedChildId = child.id();
					parent.logger.warn("Child task {} for {} failed.", child.id(), this.describeChildDevice(child));
				}
				else if (childStatus == Status.SUCCESS) {
					succeeded++;
				}
			}
		}
		catch (Exception e) {
			log.error("Task {}. Error during sequential child scheduling.", parent.getTask().id(), e);
			parent.logger.error("Error during sequential scheduling: {}", e.getMessage());
			schedulingError = true;
		}
		Status finalStatus = SequentialScheduling.decideFinalStatus(anyFailed, parent.getTask().stopOnFailure(), cancelHonored, schedulingError);
		parent.logger.info("Sequential scheduling finished: {} succeeded, {} failed, out of {} device(s). Final status: {}.",
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
	public Status pollChildUntilTerminal(long taskId) throws InterruptedException {
		int intervalMs = Netshot.config().current().get(SEQUENTIAL_POLL_INTERVAL);
		while (true) {
			Thread.sleep(intervalMs);
			Status childStatus = this.transactions.read(dsl -> this.repository.findStatus(dsl, taskId));
			if (childStatus == null || isTerminalStatus(childStatus)) {
				return childStatus;
			}
		}
	}

	/**
	 * Checks whether the given status is a terminal one.
	 * @param status the status
	 * @return true if the status is SUCCESS, FAILURE or CANCELLED
	 */
	private static boolean isTerminalStatus(Status status) {
		return status == Status.SUCCESS || status == Status.FAILURE || status == Status.CANCELLED;
	}

}
