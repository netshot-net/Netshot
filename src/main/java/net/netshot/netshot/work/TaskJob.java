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

import java.sql.SQLException;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.query.MutationQuery;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.TaskManager;
import net.netshot.netshot.cluster.ClusterManager;
import net.netshot.netshot.database.Database;
import net.netshot.netshot.hooks.Hook;
import net.netshot.netshot.hooks.HookTrigger;
import net.netshot.netshot.work.Task.DeviceLockPolicy;
import net.netshot.netshot.work.Task.Status;
import net.netshot.netshot.work.tasks.DeviceBasedTask;

/**
 * A Quartz job which runs a Netshot task.
 */
@DisallowConcurrentExecution
@Slf4j
public class TaskJob implements Job {

	/** The Constant NETSHOT_TASK. */
	public static final String NETSHOT_TASK = "Netshot Task";

	/**
	 * Instantiates a new task job.
	 */
	public TaskJob() {
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
		TaskJob.runTask(id);
	}

	/**
	 * Runs the given task, if it's still waiting to be run by this instance: takes the task lock,
	 * claims the task, waits for exclusive access to the target device if needed, runs the task,
	 * saves the result, executes the post-task hooks and schedules the next occurrence.
	 *
	 * @param id the task ID
	 * @throws JobExecutionException in case of error
	 */
	public static void runTask(long id) throws JobExecutionException {
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
			TaskJob.executeLocked(id, lockManager);
		}
		finally {
			// Released last, so that the task is seen as running until its result is saved
			lockManager.unlock(TaskLockManager.NS_TASK, id, id);
		}
	}

	/**
	 * Claims the task (atomically checks it is still waiting to be run by this instance, and marks it
	 * as running).
	 * @param session the DB session (in a transaction)
	 * @param id the task ID
	 * @return true if the task was claimed
	 */
	private static boolean claimTask(Session session, long id) {
		String runnerId = ClusterManager.getLocalInstanceId();
		MutationQuery claimQuery = session
			.createMutationQuery("update versioned Task t set t.status = :running where t.id = :id "
				+ "and (t.status = :waiting or t.status = :scheduled)"
				+ (runnerId == null ? "" : " and t.runnerId = :runnerId"))
			.setParameter("running", Status.RUNNING)
			.setParameter("id", id)
			.setParameter("waiting", Status.WAITING)
			.setParameter("scheduled", Status.SCHEDULED);
		if (runnerId != null) {
			claimQuery.setParameter("runnerId", runnerId);
		}
		return claimQuery.executeUpdate() == 1;
	}

	/**
	 * Waits (polling) until the given task gets exclusive access to the given device, or
	 * cancellation of the task is requested.
	 * No DB connection must be held by the caller while waiting.
	 * @param task the task
	 * @param deviceId the device ID
	 * @param lockManager the lock manager
	 * @return true if the device lock was acquired, false if cancellation was requested while waiting
	 * @throws SQLException in case of DB error while trying to lock the device
	 * @throws InterruptedException if interrupted while waiting
	 */
	private static boolean waitForDevice(Task task, long deviceId, TaskLockManager lockManager)
		throws SQLException, InterruptedException {
		if (lockManager.tryLock(TaskLockManager.NS_DEVICE, deviceId, task.getId())) {
			return true;
		}
		Long holderTaskId = lockManager.getLocalOwner(TaskLockManager.NS_DEVICE, deviceId);
		if (holderTaskId == null) {
			task.logger.info("Waiting for the device to be released by another task.");
		}
		else {
			task.logger.info("Waiting for the device to be released by task {}.", holderTaskId);
		}
		int pollInterval = Netshot.getConfig("netshot.tasks.devicelock.pollintervalms", 3000, 500, 60000);
		long startTime = System.currentTimeMillis();
		while (true) {
			Thread.sleep(pollInterval);
			if (task.isCancelRequestedFresh()) {
				return false;
			}
			if (lockManager.tryLock(TaskLockManager.NS_DEVICE, deviceId, task.getId())) {
				task.logger.info("Device acquired after {} s.", (System.currentTimeMillis() - startTime) / 1000);
				return true;
			}
			log.trace("Task {} still waiting for device {}.", task.getId(), deviceId);
		}
	}

	/**
	 * Executes the task, once the task lock is held.
	 * @param id the task ID
	 * @param lockManager the lock manager
	 * @throws JobExecutionException in case of error
	 */
	private static void executeLocked(long id, TaskLockManager lockManager) throws JobExecutionException {
		Task task = null;
		Session session = Database.getSession();
		try {
			Thread.sleep(1000);
			session.beginTransaction();
			if (!claimTask(session, id)) {
				Task skippedTask = session.get(Task.class, id);
				if (skippedTask == null) {
					log.error("The retrieved task {} is null.", id);
				}
				else if (skippedTask.getStatus() == Status.CANCELLED) {
					log.debug("Task {} was cancelled before starting, skipping execution.", id);
				}
				else {
					// Already run, or reassigned to another runner
					log.warn("Task {} is not waiting to be run by this instance anymore (status {}, runner {}), skipping.",
						id, skippedTask.getStatus(), skippedTask.getRunnerId());
				}
				session.getTransaction().commit();
				return;
			}
			task = session.get(Task.class, id);
			task.setRunning();
			log.trace("The task runner ID for {} is {}", task.getId(), task.getRunnerId());
			log.trace("Got the task.");
			task.prepare(session);
			log.trace("The task has prepared its fields.");
			session.getTransaction().commit();
		}
		catch (Exception e) {
			log.error("Error while retrieving, updating or preparing the task.", e);
			Database.rollbackSilently(session);
			try {
				session.clear();
				session.beginTransaction();
				Task eTask = (Task) session.get(Task.class, id);
				eTask.setRunning();
				eTask.setFailed();
				eTask.setLog(e.getMessage());
				if (task != null) {
					eTask.setDebugLog(task.getDebugLog());
				}
				session.getTransaction().commit();
			}
			catch (Exception e1) {
				log.error("Error while setting the task {} to FAILED.", id, e1);
			}
			throw new JobExecutionException("Unable to access or prepare the task.");
		}
		finally {
			session.close();
		}

		// Exclusive access to the target device, according to the task's policy
		// (no DB session is held from here, as this may wait)
		DeviceLockPolicy lockPolicy = task.deviceLockPolicy();
		Long deviceId = null;
		if (lockPolicy != DeviceLockPolicy.NONE && task instanceof DeviceBasedTask deviceTask
			&& deviceTask.getDevice() != null) {
			deviceId = deviceTask.getDevice().getId();
		}
		boolean typeLocked = false;
		boolean deviceLocked = false;
		boolean repeat = true;
		try {
			boolean runTask = true;
			if (deviceId != null) {
				if (lockPolicy == DeviceLockPolicy.CANCEL_IF_SAME_TYPE) {
					typeLocked = lockManager.tryLock(task.deviceLockTypeNamespace(), deviceId, id);
					if (!typeLocked) {
						task.setCancelled("A task of the same type is already running or waiting on this device, cancelling this task.");
						runTask = false;
					}
				}
				if (runTask) {
					deviceLocked = waitForDevice(task, deviceId, lockManager);
					if (!deviceLocked) {
						task.setCancelled("Cancelled while waiting for the device.");
						runTask = false;
						// Requested by a user: stop the repeat chain, as when cancelling a scheduled task
						repeat = false;
					}
				}
			}
			if (runTask) {
				log.warn("Running the task {} of type {}", id, task.getClass().getSimpleName());
				try {
					task.run();
				}
				catch (Error e) {
					log.error("Error while running the task {}.", id, e);
				}
			}
		}
		catch (SQLException e) {
			log.error("Unable to lock the device for task {}.", id, e);
			task.logger.error("Unable to lock the device: {}", e.getMessage());
			task.setFailed();
		}
		catch (InterruptedException e) {
			log.error("Task {} interrupted while waiting for the device.", id, e);
			task.logger.error("Interrupted while waiting for the device.");
			task.setFailed();
			Thread.currentThread().interrupt();
		}
		finally {
			if (deviceLocked) {
				lockManager.unlock(TaskLockManager.NS_DEVICE, deviceId, id);
			}
			if (typeLocked) {
				lockManager.unlock(task.deviceLockTypeNamespace(), deviceId, id);
			}
		}

		if (task.getStatus() == Status.RUNNING) {
			log.error("The task {} exited with a status of RUNNING.", id);
			task.setFailed();
		}

		log.trace("Updating the task with the result.");
		session = Database.getSession();
		try {
			session.beginTransaction();
			Task persistentTask = session.get(Task.class, id);
			task.copyResultsTo(persistentTask);
			session.getTransaction().commit();
		}
		catch (Exception e) {
			log.error("Error while updating the task {} after execution.", id, e);
			Database.rollbackSilently(session);
			try {
				session.clear();
				session.beginTransaction();
				Task eTask = session.get(Task.class, id);
				eTask.setFailed();
				session.getTransaction().commit();
			}
			catch (Exception e1) {
				log.error("Error while setting the task {} to FAILED.", id, e1);
			}
			throw new JobExecutionException("Unable to save the task.");
		}
		finally {
			session.close();
		}


		log.trace("Looking for post-task hooks.");
		session = Database.getSession();
		try {
			task = (Task) session.get(Task.class, id);
			// Re-initialize the lazy associations (e.g. device group/list) the task's clone()
			// will need for repeatTask() below, since that runs after this session is closed.
			task.prepare(session);
			List<Hook> hooks = session
				.createQuery("select h from Hook h join h.triggers t where t.type = :postTask and t.item = :taskName and h.enabled", Hook.class)
				.setParameter("postTask", HookTrigger.TriggerType.POST_TASK)
				.setParameter("taskName", task.getClass().getSimpleName())
				.list();

			for (Hook hook : hooks) {
				log.trace("Executing post-task hook {}", hook.getName());
				try {
					String result = hook.execute(task);
					log.info("Result of post-task hook '{}' after task {} is: {}", hook.getName(), task.getId(), result);
				}
				catch (Exception e) {
					log.warn("Error while executing hook {} after task {}", hook.getName(), task.getId(), e);
				}
			}
		}
		catch (Exception e) {
			log.error("Error while processing hooks after task {}.", id, e);
			Database.rollbackSilently(session);
		}
		finally {
			session.close();
		}

		if (repeat) {
			try {
				TaskManager.repeatTask(task);
			}
			catch (Exception e) {
				log.error("Unable to repeat the task {} again.", id, e);
			}
		}

		log.warn("End of task {} of type {}: {}.", id, task.getClass().getSimpleName(), task.getStatus());

	}

}
