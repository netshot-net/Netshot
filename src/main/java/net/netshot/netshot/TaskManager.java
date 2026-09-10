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

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.MarkerFactory;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.cluster.ClusterManager;
import net.netshot.netshot.cluster.ClusterMember;
import net.netshot.netshot.cluster.ClusterMember.MastershipStatus;
import net.netshot.netshot.database.Database;
import net.netshot.netshot.work.MasterJob;
import net.netshot.netshot.work.Task;
import net.netshot.netshot.work.Task.Status;
import net.netshot.netshot.work.TaskJob;

/**
 * The TaskManager schedules and runs the tasks.
 */
@Slf4j
public final class TaskManager {

	/**
	 * Settings/config for the current class.
	 */
	public static final class Settings {
		/** Max thread count for task execution. */
		@Getter
		private int threadCount;

		/**
		 * Load settings from config.
		 */
		private void load() {
			this.threadCount = Netshot.getConfig("netshot.tasks.threadcount", 10, 1, 65535);
		}
	}

	/** Possible modes for the task manager. */
	public enum Mode {
		SINGLE,
		CLUSTER_MASTER,
		CLUSTER_MEMBER,
	}

	/** Store the available runners with weighted-random selector. */
	public static final class RunnerSet {
		private final NavigableMap<Double, String> map = new TreeMap<Double, String>();
		private final Random random;
		private double total = 0;

		public RunnerSet() {
			this.random = new Random();
		}

		public void add(double weight, String runnerId) {
			if (weight > 0) {
				this.total += weight;
				this.map.put(this.total, runnerId);
			}
		}

		public String getNextRunnerId() {
			double value = this.random.nextDouble() * this.total;
			return this.map.higherEntry(value).getValue();
		}
	}

	/** Settings for this class. */
	public static final Settings SETTINGS = new Settings();

	/** The master scheduler (used in master mode to dispatch jobs). */
	private static Scheduler masterScheduler;

	/** The runner scheduler (used to run jobs). */
	private static Scheduler runnerScheduler;

	/** TaskManager mode. */
	private static Mode mode = Mode.SINGLE;

	/** Available runners. */
	private static RunnerSet runnerSet;

	/**
	 * Initializes the task manager.
	 */
	public static void init() {
		TaskManager.SETTINGS.load();
		try {
			Properties params = new Properties();
			params.put(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, "org.quartz.simpl.SimpleThreadPool");
			params.put("org.quartz.threadPool.threadCount", Integer.toString(TaskManager.SETTINGS.threadCount));
			params.put(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "NetshotMasterScheduler");
			params.put("org.quartz.scheduler.skipUpdateCheck", "true");
			StdSchedulerFactory factory = new StdSchedulerFactory(params);
			TaskManager.masterScheduler = factory.getScheduler();
			TaskManager.masterScheduler.start();
		}
		catch (Exception e) {
			log.error(MarkerFactory.getMarker("FATAL"), "Unable to instantiate the Master Task Manager", e);
			throw new RuntimeException("Unable to instantiate the Master Task Manager.", e);
		}
		try {
			Properties params = new Properties();
			params.put(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, "org.quartz.simpl.SimpleThreadPool");
			params.put("org.quartz.threadPool.threadCount", Integer.toString(TaskManager.SETTINGS.threadCount));
			params.put(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "NetshotRunnerScheduler");
			params.put("org.quartz.scheduler.skipUpdateCheck", "true");
			StdSchedulerFactory factory = new StdSchedulerFactory(params);
			TaskManager.runnerScheduler = factory.getScheduler();
			TaskManager.runnerScheduler.start();
		}
		catch (Exception e) {
			log.error(MarkerFactory.getMarker("FATAL"), "Unable to instantiate the Runner Task Manager", e);
			throw new RuntimeException("Unable to instantiate the Runner Task Manager.", e);
		}
	}

	/**
	 * Sets the current TaskManager mode.
	 * @param mode the new mode to set
	 */
	public static void setMode(Mode mode) {
		TaskManager.mode = mode;
	}

	/**
	 * Returns the current TaskManager mode.
	 * @return the mode
	 */
	public static Mode getMode() {
		return TaskManager.mode;
	}

	/**
	 * Gets the number of task threads.
	 * @return the thread count
	 */
	public static int getThreadCount() {
		return TaskManager.SETTINGS.threadCount;
	}

	/**
	 * Add new tasks to master scheduler.
	 */
	public static void scheduleNewTasks() {
		log.debug("Looking for new tasks to assign to available runners");
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			List<Task> tasks = session.createQuery("select t from Task t where t.status = :new", Task.class)
				.setParameter("new", Task.Status.NEW)
				.list();
			for (Task task : tasks) {
				task.onSchedule();
				task.setScheduled();
				session.persist(task);
				try {
					addTaskToScheduler(task, false, true, false);
				}
				catch (SchedulerException e) {
					log.error("Error while scheduling new task", e);
				}
			}
			session.getTransaction().commit();
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Database error while scheduling new tasks (master scheduler)", e);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Assigns a runner to a task and sets the task as waiting.
	 * @param task the task
	 */
	public static void assignTaskRunner(Task task) {
		long hash = task.getRunnerHash();
		String runnerId = TaskManager.runnerSet.getNextRunnerId();
		log.info("Task {} (hash {}) is being assigned to runner {}.", task.getId(), hash, runnerId);
		task.setRunnerId(runnerId);
		task.setWaiting();
	}

	/**
	 * Sets the possible task runners (to be called from ClusterManager).
	 * @param members the list of cluster members.
	 */
	public static void setRunners(Collection<ClusterMember> members) {
		Integer maxPriority = null;
		Set<ClusterMember> activeMembers = new HashSet<>();
		for (ClusterMember member : members) {
			if (MastershipStatus.MASTER.equals(member.getStatus())
				|| MastershipStatus.MEMBER.equals(member.getStatus())
				|| MastershipStatus.NEGOTIATING.equals(member.getStatus())) {
				activeMembers.add(member);
			}
		}
		for (ClusterMember member : activeMembers) {
			if (maxPriority == null || member.getRunnerPriority() > maxPriority) {
				maxPriority = member.getRunnerPriority();
			}
		}
		RunnerSet newRunnerSet = new RunnerSet();
		for (ClusterMember member : activeMembers) {
			if (member.getRunnerPriority() == maxPriority) {
				newRunnerSet.add(member.getRunnerWeight(), member.getInstanceId());
			}
		}
		synchronized (TaskManager.masterScheduler) {
			TaskManager.runnerSet = newRunnerSet;
		}
		TaskManager.reassignOrphanTasks();
	}

	/**
	 * Requests cancellation of a task that is already RUNNING in sequential mode. Unlike
	 * {@link #cancelTask}, this doesn't touch the Quartz scheduler (the job is genuinely
	 * running, not sitting in the scheduler waiting to fire) -- it just flags the task so
	 * that its own sequential scheduling loop stops scheduling further child tasks the
	 * next time it polls (the currently in-flight child is left to finish naturally).
	 *
	 * @param taskId the ID of the task to request cancellation of
	 * @throws HibernateException the Hibernate exception
	 */
	public static void requestCancel(long taskId) throws HibernateException {
		log.debug("Requesting cancellation of running sequential task {}.", taskId);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			session.createMutationQuery("update Task t set t.cancelRequested = true where t.id = :id")
				.setParameter("id", taskId)
				.executeUpdate();
			session.getTransaction().commit();
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while requesting cancellation of task {}.", taskId, e);
			throw e;
		}
		finally {
			session.close();
		}
	}

	/**
	 * Cancels the task.
	 *
	 * @param task the task
	 * @param reason the reason
	 * @throws SchedulerException the scheduler exception
	 * @throws HibernateException the hibernate exception
	 */
	public static void cancelTask(Task task, String reason) throws SchedulerException, HibernateException {
		log.debug("Cancelling task {}.", task);
		TaskManager.runnerScheduler.deleteJob(task.getIdentity());
		TaskManager.masterScheduler.deleteJob(task.getIdentity());
		log.trace("The task has been deleted from the scheduler(s).");
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			task.onCancel();
			task.setCancelled(reason);
			session.merge(task);
			session.getTransaction().commit();
			log.trace("Task successfully cancelled.");
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while updating the cancelled task.", e);
			throw e;
		}
		finally {
			session.close();
		}
	}

	/**
	 * Add the given task to the given scheduler.
	 * @param task the task to schedule
	 * @param runnerTask true to mark a runner task rather than a master task
	 * @param checkExistence checks that the task is not already in the scheduler
	 * @param forceNow force immediate execution or use task's execution time
	 * @throws SchedulerException
	 */
	private static void addTaskToScheduler(Task task, boolean runnerTask,
		boolean checkExistence, boolean forceNow) throws SchedulerException {
		Scheduler scheduler = runnerTask ? runnerScheduler : masterScheduler;
		if (checkExistence) {
			if (scheduler.checkExists(task.getIdentity())) {
				log.debug("Task already in the scheduler");
				return;
			}
		}
		Class<? extends Job> jobClass = runnerTask ? TaskJob.class : MasterJob.class;
		JobDetail job = JobBuilder
			.newJob(jobClass)
			.withIdentity(task.getIdentity())
			.build();
		job.getJobDataMap().put(TaskJob.NETSHOT_TASK, task.getId());
		TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder
			.newTrigger()
			.withPriority(task.getPriority());
		Date when = task.getNextExecutionDate();
		if (when == null || forceNow) {
			triggerBuilder.startNow();
		}
		else {
			triggerBuilder.startAt(when);
		}
		scheduler.scheduleJob(job, triggerBuilder.build());
		log.trace("Task successfully added to the scheduler.");
	}

	/**
	 * Adds a task to the database in single mode (no clustering).
	 * @param task The task to add
	 * @throws SchedulerException scheduler exception
	 * @throws HibernateException Hibernate exception
	 */
	private static void addTaskSingleMode(Task task) throws SchedulerException, HibernateException {
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			task.onSchedule();
			task.setScheduled();
			if (task.getId() == 0) {
				session.persist(task);
			}
			else {
				Task persistentTask = session.get(Task.class, task.getId());
				persistentTask.setScheduled();
				session.merge(persistentTask);
			}
			session.getTransaction().commit();
			session.evict(task);
			log.trace("Task successfully added to the database.");
		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the new task.", e);
			throw e;
		}
		finally {
			session.close();
		}
		addTaskToScheduler(task, true, false, false);
	}

	/**
	 * Adds a task to the system in cluster master mode.
	 * @param task The task to add
	 * @throws SchedulerException scheduler exception
	 * @throws HibernateException Hibernate exception
	 */
	private static void addTaskMasterMode(Task task) throws SchedulerException, HibernateException {
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			task.onSchedule();
			task.setScheduled();
			if (task.getId() == 0) {
				session.persist(task);
			}
			else {
				session.merge(task);
			}
			session.getTransaction().commit();
			session.evict(task);
			log.trace("Task successfully added to the database.");
		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the new task.", e);
			throw e;
		}
		finally {
			session.close();
		}
		addTaskToScheduler(task, false, false, false);
	}

	/**
	 * Adds a task to the system in cluster member mode.
	 * @param task The task to add
	 * @throws SchedulerException scheduler exception
	 * @throws HibernateException Hibernate exception
	 */
	private static void addTaskMemberMode(Task task) throws SchedulerException, HibernateException {
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			task.setStatus(Status.NEW);
			if (task.getId() == 0) {
				session.persist(task);
			}
			else {
				session.merge(task);
			}
			session.getTransaction().commit();
			session.evict(task);
			log.trace("Task successfully added to the database.");
		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the new task.", e);
			throw e;
		}
		finally {
			session.close();
		}
		ClusterManager.requestTasksAssignment();
	}

	/**
	 * Adds a task to the system.
	 *
	 * @param task the task
	 * @throws SchedulerException the scheduler exception
	 * @throws HibernateException the Hibernate exception
	 */
	public static void addTask(Task task) throws SchedulerException, HibernateException {
		log.debug("Adding task {} to the system.", task.getId());

		switch (TaskManager.mode) {
			case SINGLE:
				addTaskSingleMode(task);
				break;
			case CLUSTER_MASTER:
				addTaskMasterMode(task);
				break;
			case CLUSTER_MEMBER:
				addTaskMemberMode(task);
				break;
			default:
		}
	}

	/**
	 * Repeat task.
	 *
	 * @param task the task
	 * @throws SchedulerException the scheduler exception
	 * @throws HibernateException the hibernate exception
	 */
	public static void repeatTask(Task task) throws SchedulerException, HibernateException {
		log.debug("Repeating task {} if necessary.", task.getId());
		if (!task.isRepeating()) {
			log.trace("Not necessary.");
			return;
		}
		Task newTask;
		try {
			newTask = (Task) task.clone();
			log.trace("Got a clone, will schedule it.");
			TaskManager.addTask(newTask);
		}
		catch (CloneNotSupportedException e) {
			log.error("Unable to clone the task {}.", task.getId(), e);
		}
	}

	/**
	 * Retrieve the tasks assigned to the local instance and add them to the runner scheduler.
	 */
	public static void scheduleLocalTasks() {
		log.debug("Will retrieve the waiting tasks assigned to the local cluster member.");
		Session session = Database.getSession();
		try {
			List<Task> tasks = session.createQuery(
				"select t from Task t where t.status = :waiting and t.runnerId = :myId", Task.class)
				.setParameter("waiting", Task.Status.WAITING)
				.setParameter("myId", ClusterManager.getLocalInstanceId())
				.list();
			for (Task task : tasks) {
				log.trace("Found task {}, assigned to local cluster member {}", task.getId(),
					task.getRunnerId());
				addTaskToScheduler(task, true, true, true);
			}
		}
		catch (HibernateException e) {
			log.error("Unable to retrieve the waiting tasks from the database", e);
		}
		catch (SchedulerException e) {
			log.error("Unable to schedule the task", e);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Finds and reassigns tasks currently assigned to a failed runner.
	 */
	public static void reassignOrphanTasks() {
		if (!Mode.CLUSTER_MASTER.equals(TaskManager.mode)) {
			log.debug("Skipping reassignment of orphan tasks (not in MASTER mode)");
			return;
		}
		log.info("Looking for orphan tasks after change on the list of runners.");
		Session session = Database.getSession();
		int reassignedCount = 0;
		try {
			session.beginTransaction();
			List<String> runnerIds = session.createQuery(
				"select distinct t.runnerId from Task t where (t.status = :waiting or t.status = :running) and t.runnerId is not null",
				String.class)
				.setParameter("waiting", Task.Status.WAITING)
				.setParameter("running", Task.Status.RUNNING)
				.list();
			Iterator<String> runnerIdIt = runnerIds.iterator();
			while (runnerIdIt.hasNext()) {
				String runnerId = runnerIdIt.next();
				if (TaskManager.runnerSet.map.values().contains(runnerId)) {
					runnerIdIt.remove();
				}
				else {
					log.debug("The runner of ID {} doesn't exist anymore", runnerId);
				}
			}
			if (runnerIds.size() > 0) {
				List<Task> tasks = session.createQuery(
					"select t from Task t where (t.status = :waiting or t.status = :running) and "
						+ "(t.runnerId in :runnerIds or t.runnerId is null)",
					Task.class)
					.setParameter("waiting", Task.Status.WAITING)
					.setParameter("running", Task.Status.RUNNING)
					.setParameterList("runnerIds", runnerIds)
					.list();
				for (Task task : tasks) {
					log.debug("Task {} will be reassigned as its previously assigned runner {} has disappeared.",
						task.getId(), task.getRunnerId());
					assignTaskRunner(task);
					session.merge(task);
					reassignedCount += 1;
				}
				session.getTransaction().commit();
				ClusterManager.requestTasksLoad();
			}
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Database error while re-assigning orphan tasks", e);
		}
		finally {
			session.close();
		}
		log.info("{} orphan task(s) reassigned", reassignedCount);
	}

	/**
	 * Reschedules all tasks. To be executed at startup.
	 */
	public static void rescheduleAll() {
		log.debug("Will re-schedule the existing tasks from the database.");

		Session session = Database.getSession();
		List<Task> tasks;
		try {
			switch (TaskManager.mode) {
				case CLUSTER_MASTER:
					tasks = session.createQuery(
						"select t from Task t where t.status = :new or t.status = :scheduled or ((t.status = :waiting or t.status = :running) and t.runnerId = :myId)",
						Task.class)
						.setParameter("new", Task.Status.NEW)
						.setParameter("scheduled", Task.Status.SCHEDULED)
						.setParameter("waiting", Task.Status.WAITING)
						.setParameter("running", Task.Status.RUNNING)
						.setParameter("myId", ClusterManager.getLocalInstanceId())
						.list();
					break;
				case CLUSTER_MEMBER:
					tasks = session.createQuery(
						"select t from Task t where (t.status = :waiting or t.status = :running) and t.runnerId = :myId", Task.class)
						.setParameter("waiting", Task.Status.WAITING)
						.setParameter("running", Task.Status.RUNNING)
						.setParameter("myId", ClusterManager.getLocalInstanceId())
						.list();
					break;
				default:
					tasks = session.createQuery(
						"select t from Task t where t.status = :scheduled or t.status = :waiting or t.status = :running", Task.class)
						.setParameter("scheduled", Task.Status.SCHEDULED)
						.setParameter("waiting", Task.Status.WAITING)
						.setParameter("running", Task.Status.RUNNING)
						.list();
			}
		}
		catch (HibernateException e) {
			log.error("Unable to retrieve the tasks from the database.", e);
			throw e;
		}
		finally {
			session.close();
		}

		log.trace("Found {} task(s) to reschedule.", tasks.size());
		Calendar inThirtySeconds = Calendar.getInstance();
		inThirtySeconds.add(Calendar.SECOND, 30);
		for (Task task : tasks) {
			try {
				if (masterScheduler.checkExists(task.getIdentity())) {
					log.trace("The task {} is already in the master scheduler.", task.getId());
					continue;
				}
				if (runnerScheduler.checkExists(task.getIdentity())) {
					log.trace("The task {} is already in the runner scheduler.", task.getId());
					continue;
				}
			}
			catch (SchedulerException e) {
				log.error("Error while checking task existence in the scheduler", e);
			}
			try {
				Date when = task.getNextExecutionDate();
				if (when == null || when.after(inThirtySeconds.getTime())) {
					TaskManager.addTask(task);
				}
				else {
					TaskManager.cancelTask(task, "Task skipped, too late to run.");
				}
			}
			catch (SchedulerException e) {
				log.error("Unable to schedule the task {}.", task.getId(), e);
			}
		}
	}

	/**
	 * Cancels any DELAYED task left with no parent -- a pre-created child of a group-based task
	 * whose parent no longer exists (deletion clears a child's {@code parentTaskId} rather than
	 * cascading, see {@link Task}), so nothing will ever promote it. To be called once at
	 * startup, on every node regardless of clustering mode: it's a plain, idempotent bulk update,
	 * so no cross-node coordination is needed the way {@link #reassignOrphanTasks()} needs.
	 */
	public static void cancelOrphanedDelayedTasks() {
		log.debug("Looking for orphaned DELAYED tasks (no parent) to cancel.");
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			int count = session
				.createMutationQuery(
					"update Task t set t.status = :cancelled where t.status = :delayed and t.parentTaskId is null")
				.setParameter("cancelled", Task.Status.CANCELLED)
				.setParameter("delayed", Task.Status.DELAYED)
				.executeUpdate();
			session.getTransaction().commit();
			log.info("{} orphaned DELAYED task(s) cancelled at startup", count);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while cancelling orphaned DELAYED tasks", e);
		}
		finally {
			session.close();
		}
	}

	private TaskManager() {
	}

}
