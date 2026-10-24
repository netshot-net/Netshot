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

import java.sql.SQLException;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.cluster.ClusterManager;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.event.DomainEvents;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.DeviceLockPolicy;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandlers;
import net.netshot.netshot.task.TaskRepository;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.event.TaskFinished;
import net.netshot.netshot.task.type.DeviceBasedTask;

/**
 * A Quartz job which runs a Netshot task.
 */
@DisallowConcurrentExecution
@Slf4j
public class TaskJob implements Job {

	/** The Constant NETSHOT_TASK. */
	public static final String NETSHOT_TASK = "Netshot Task";

	private final TaskHandlers handlers;

	private final DomainEvents domainEvents;

	private final TaskRepository repository = new TaskRepository();

	private final Transactions transactions = new Transactions();

	/**
	 * Instantiates a new task job. Quartz builds the jobs through the {@link TaskJobFactory}.
	 *
	 * @param handlers the task handlers, by task type
	 * @param domainEvents where the end of a task is published
	 */
	public TaskJob(TaskHandlers handlers, DomainEvents domainEvents) {
		this.handlers = handlers;
		this.domainEvents = domainEvents;
	}

	/*(non-Javadoc)
	 * @see org.quartz.Job#execute(org.quartz.JobExecutionContext)
	 */
	public void execute(JobExecutionContext context) throws JobExecutionException {
		log.debug("Starting job.");
		Long id = (Long) context.getJobDetail().getJobDataMap()
			.get(NETSHOT_TASK);
		if (id == 0) {
			log.error("Invalid task, id is 0");
			return;
		}
		this.runTask(id);
	}

	/**
	 * Runs the given task, if it's still waiting to be run by this instance: takes the task lock,
	 * claims the task, waits for exclusive access to the target device if needed, runs the task,
	 * saves the result, executes the post-task hooks and schedules the next occurrence.
	 *
	 * @param id the task ID
	 * @throws JobExecutionException in case of error
	 */
	public void runTask(long id) throws JobExecutionException {
		log.trace("The task id is {}.", id);
		// The task lock prevents the same task from running twice at the same time,
		// on this instance or (cluster mode) on any other cluster member
		TaskLockManager lockManager = TaskLockManager.get();
		try {
			if (!lockManager.tryLock(TaskLockManager.NS_TASK, id, id)) {
				log.warn("Task {} is already running (locally or on another cluster member), skipping.", id);
				return;
			}
		}
		catch (SQLException e) {
			log.error("Unable to acquire the lock of task {}.", id, e);
			throw new JobExecutionException("Unable to lock the task.", e);
		}
		try {
			this.executeLocked(id, lockManager);
		}
		finally {
			// Released last, so that the task is seen as running until its result is saved
			lockManager.unlock(TaskLockManager.NS_TASK, id, id);
		}
	}

	/**
	 * Waits (polling) until the given task gets exclusive access to the given device, or
	 * cancellation of the task is requested.
	 * No DB connection must be held by the caller while waiting.
	 *
	 * @param run the run of the task
	 * @param deviceId the device ID
	 * @param lockManager the lock manager
	 * @return true if the device was acquired, false if the cancellation was requested
	 * @throws SQLException if the lock can't be taken
	 * @throws InterruptedException if interrupted while waiting
	 */
	private boolean waitForDevice(TaskRun run, long deviceId, TaskLockManager lockManager)
		throws SQLException, InterruptedException {
		long taskId = run.getTask().id();
		if (lockManager.tryLock(TaskLockManager.NS_DEVICE, deviceId, taskId)) {
			return true;
		}
		Long holderTaskId = lockManager.getLocalOwner(TaskLockManager.NS_DEVICE, deviceId);
		if (holderTaskId == null) {
			run.logger.info("Waiting for the device to be released by another task.");
		}
		else {
			run.logger.info("Waiting for the device to be released by task {}.", holderTaskId);
		}
		int pollInterval = Netshot.getConfig("netshot.tasks.devicelock.pollintervalms", 3000, 500, 60000);
		long startTime = System.currentTimeMillis();
		while (true) {
			Thread.sleep(pollInterval);
			if (this.transactions.read(dsl -> this.repository.isCancelRequested(dsl, taskId))) {
				return false;
			}
			if (lockManager.tryLock(TaskLockManager.NS_DEVICE, deviceId, taskId)) {
				run.logger.info("Device acquired after {} s.", (System.currentTimeMillis() - startTime) / 1000);
				return true;
			}
			log.trace("Task {} still waiting for device {}.", taskId, deviceId);
		}
	}

	// Claims the task and marks it as started, returns the run (or null if the task was not claimed)
	private TaskRun start(long id) {
		return this.transactions.write(dsl -> {
			if (!this.repository.claim(dsl, id, ClusterManager.getLocalInstanceId())) {
				Task skippedTask = this.repository.find(dsl, id);
				if (skippedTask == null) {
					log.error("The retrieved task {} is null.", id);
				}
				else if (skippedTask.status() == Status.CANCELLED) {
					log.debug("Task {} was cancelled before starting, skipping execution.", id);
				}
				else {
					// Already run, or reassigned to another runner
					log.warn("Task {} is not waiting to be run by this instance anymore (status {}, runner {}), skipping.",
						id, skippedTask.status(), skippedTask.runnerId());
				}
				return null;
			}
			TaskRun run = new TaskRun(this.repository.find(dsl, id));
			this.repository.start(dsl, run);
			log.trace("The task runner ID for {} is {}", id, run.getTask().runnerId());
			return run;
		});
	}

	private void executeLocked(long id, TaskLockManager lockManager) throws JobExecutionException {
		TaskRun run;
		try {
			Thread.sleep(1000);
			run = this.start(id);
			if (run == null) {
				return;
			}
		}
		catch (Exception e) {
			log.error("Error while retrieving, updating or preparing the task.", e);
			try {
				this.transactions.run(dsl -> this.repository.fail(dsl, id, e.getMessage() == null ? "" : e.getMessage()));
			}
			catch (Exception e1) {
				log.error("Error while setting the task {} to FAILED.", id, e1);
			}
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new JobExecutionException("Unable to access or prepare the task.");
		}
		Task task = run.getTask();

		// Exclusive access to the target device, according to the task's policy
		// (no DB session is held from here, as this may wait)
		DeviceLockPolicy lockPolicy = task.deviceLockPolicy();
		Long deviceId = null;
		if (lockPolicy != DeviceLockPolicy.NONE && task.spec() instanceof DeviceBasedTask deviceTask) {
			deviceId = deviceTask.deviceId();
		}
		int lockTypeNamespace = task.spec().deviceLockTypeNamespace();
		boolean typeLocked = false;
		boolean deviceLocked = false;
		boolean repeat = true;
		try {
			boolean runTask = true;
			if (deviceId != null) {
				if (lockPolicy == DeviceLockPolicy.CANCEL_IF_SAME_TYPE) {
					typeLocked = lockManager.tryLock(lockTypeNamespace, deviceId, id);
					if (!typeLocked) {
						run.cancel("A task of the same type is already running or waiting on this device, cancelling this task.");
						runTask = false;
					}
				}
				if (runTask) {
					deviceLocked = this.waitForDevice(run, deviceId, lockManager);
					if (!deviceLocked) {
						run.cancel("Cancelled while waiting for the device.");
						runTask = false;
						// Requested by a user: stop the repeat chain, as when cancelling a scheduled task
						repeat = false;
					}
				}
			}
			if (runTask) {
				log.warn("Running the task {} of type {}", id, task.typeName());
				try {
					this.handlers.run(task, run);
				}
				catch (RuntimeException | Error e) {
					// Must not escape: the task would never be saved and would stay RUNNING
					log.error("Error while running the task {}.", id, e);
					run.logger.error("Error while running the task: {}", e.toString());
					run.fail();
				}
			}
		}
		catch (SQLException e) {
			log.error("Unable to lock the device for task {}.", id, e);
			run.logger.error("Unable to lock the device: {}", e.getMessage());
			run.fail();
		}
		catch (InterruptedException e) {
			log.error("Task {} interrupted while waiting for the device.", id, e);
			run.logger.error("Interrupted while waiting for the device.");
			run.fail();
			Thread.currentThread().interrupt();
		}
		finally {
			if (deviceLocked) {
				lockManager.unlock(TaskLockManager.NS_DEVICE, deviceId, id);
			}
			if (typeLocked) {
				lockManager.unlock(lockTypeNamespace, deviceId, id);
			}
		}

		if (run.getStatus() == Status.RUNNING) {
			log.error("The task {} exited with a status of RUNNING.", id);
			run.fail();
		}

		log.trace("Updating the task with the result.");
		try {
			this.transactions.run(dsl -> this.repository.saveRun(dsl, run));
		}
		catch (Exception e) {
			log.error("Error while updating the task {} after execution.", id, e);
			try {
				this.transactions.run(dsl -> this.repository.fail(dsl, id, null));
			}
			catch (Exception e1) {
				log.error("Error while setting the task {} to FAILED.", id, e1);
			}
			throw new JobExecutionException("Unable to save the task.");
		}

		// The listeners (such as the post-task hooks) are called here, in this thread
		log.trace("Looking for post-task hooks.");
		try {
			this.domainEvents.publish(new TaskFinished(id, task.typeName()));
		}
		catch (Exception e) {
			log.error("Error while processing hooks after task {}.", id, e);
		}

		if (repeat) {
			try {
				TaskManager.repeatTask(task);
			}
			catch (Exception e) {
				log.error("Unable to repeat the task {} again.", id, e);
			}
		}

		log.warn("End of task {} of type {}: {}.", id, task.typeName(), run.getStatus());
	}
}
