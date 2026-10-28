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

import static net.netshot.netshot.platform.database.schema.Tables.TASK;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.MarkerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.platform.database.ConnectionPools;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.lifecycle.Service;
import net.netshot.netshot.task.AutoSnapshotQueue;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskEngine;
import net.netshot.netshot.task.TaskRepository;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * The TaskManager schedules and runs the tasks (Quartz master and runner schedulers), as a service:
 * when started, it schedules the existing tasks again; when stopped, it gives the running tasks some time
 * to end (see {@link #stop()}).
 */
@Slf4j
public final class TaskManager implements Service, TaskEngine {

	/** Key of the task manager in the context of the master scheduler (for the master jobs). */
	static final String CONTEXT_KEY = "netshotTaskManager";

	/** Max time (s) to wait, when Netshot stops, for the running tasks to end (they're marked as failed after it). */
	static final Setting<Integer> SHUTDOWN_TIMEOUT = Setting.integer("netshot.shutdown.timeout")
		.defaultValue(20).range(0, 3600).live();

	/** Number of threads running the tasks. */
	static final Setting<Integer> THREAD_COUNT = Setting.integer("netshot.tasks.threadcount")
		.defaultValue(10).range(1, 65535).restart();

	/** Possible modes for the task manager. */
	public enum Mode {
		SINGLE,
		CLUSTER_MASTER,
		CLUSTER_MEMBER,
	}

	/**
	 * A cluster member which can run tasks.
	 *
	 * @param instanceId the ID of the member
	 * @param priority the runner priority (only the members with the highest priority run tasks)
	 * @param weight the runner weight (share of the tasks among the members with the same priority)
	 */
	public record Runner(String instanceId, int priority, int weight) {
	}

	/**
	 * Store the available runners, with a weighted selector: weighted-random
	 * for tasks without hash, stable (weighted rendezvous hashing) for tasks with a hash,
	 * so that the tasks of a given device keep running on the same runner.
	 */
	public static final class RunnerSet {
		private final NavigableMap<Double, String> map = new TreeMap<Double, String>();
		private final Map<String, Double> weights = new HashMap<>();
		private final Random random;
		private double total = 0;

		public RunnerSet() {
			this.random = new Random();
		}

		public void add(double weight, String runnerId) {
			if (weight > 0) {
				this.total += weight;
				this.map.put(this.total, runnerId);
				this.weights.put(runnerId, weight);
			}
		}

		/**
		 * Selects a runner for a task.
		 *
		 * @param hash the task hash ({@link Task#runnerHash()}), 0 for no runner affinity
		 * @return the selected runner ID
		 */
		public String getRunnerId(long hash) {
			if (hash == 0) {
				double value = this.random.nextDouble() * this.total;
				return this.map.higherEntry(value).getValue();
			}
			// Weighted rendezvous hashing: the selection only depends on the hash and on
			// the runner set (not on the master instance or on the runner order), and only the
			// tasks of an added/removed runner move when the set changes
			String bestRunnerId = null;
			double bestScore = Double.NEGATIVE_INFINITY;
			for (Map.Entry<String, Double> runner : this.weights.entrySet()) {
				long mixed = mix64(hash ^ mix64(runner.getKey().hashCode()));
				// Uniform in ]0, 1[
				double uniform = ((mixed >>> 11) + 0.5) / (double) (1L << 53);
				double score = -runner.getValue() / Math.log(uniform);
				if (score > bestScore || (score == bestScore && runner.getKey().compareTo(bestRunnerId) < 0)) {
					bestScore = score;
					bestRunnerId = runner.getKey();
				}
			}
			return bestRunnerId;
		}

		/**
		 * SplitMix64 finalizer.
		 *
		 * @param value the value to mix
		 * @return the mixed value
		 */
		private static long mix64(long value) {
			long z = value + 0x9E3779B97F4A7C15L;
			z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
			z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
			return z ^ (z >>> 31);
		}
	}

	/** The number of threads running the tasks (read when the task manager is initialized). */
	private volatile int threadCount = THREAD_COUNT.defaultValue();

	/** Minimum number of DB pool connections to keep available beyond the task threads. */
	private static final int MIN_SPARE_DB_CONNECTIONS = 10;

	/** The master scheduler (used in master mode to dispatch jobs). */
	private volatile Scheduler masterScheduler;

	/** The runner scheduler (used to run jobs). */
	private volatile Scheduler runnerScheduler;

	/** Lock of the update of the runners. */
	private final Object runnersLock = new Object();

	/** Serializes the loading of the local tasks into the runner scheduler. */
	private final Object localTasksLock = new Object();

	/** TaskManager mode (set by the cluster thread, read from any thread). */
	private volatile Mode mode = Mode.SINGLE;

	/** Available runners (replaced as a whole by the cluster thread, never mutated after publication). */
	private volatile RunnerSet runnerSet;

	/** The configuration store. */
	private final ConfigStore config;

	/** The devices with a queued automatic snapshot. */
	private final AutoSnapshotQueue autoSnapshots;

	/** The task and device locks. */
	private final TaskLockManager lockManager;

	/** The clustering (none by default). */
	private volatile ClusterCoordinator cluster = ClusterCoordinator.NONE;

	/** The factory of the jobs which run the tasks. */
	private TaskJobFactory jobFactory;

	/** The number of tasks started on this instance. */
	private final AtomicLong startedTasks = new AtomicLong();

	/** The number of tasks finished on this instance, by status. */
	private final Map<Status, AtomicLong> finishedTasks = new EnumMap<>(Status.class);

	/** The number of tasks marked as failed when this instance stopped. */
	private final AtomicLong interruptedTasks = new AtomicLong();

	/** The task queries. */
	private static final TaskRepository REPOSITORY = new TaskRepository();

	/** The transaction support. */
	private final Transactions transactions;

	/**
	 * Creates the task manager.
	 *
	 * @param transactions the transaction support
	 * @param config the configuration store
	 * @param autoSnapshots the devices with a queued automatic snapshot
	 * @param lockManager the task and device locks
	 */
	public TaskManager(Transactions transactions, ConfigStore config, AutoSnapshotQueue autoSnapshots, TaskLockManager lockManager) {
		this.transactions = transactions;
		this.config = config;
		this.autoSnapshots = autoSnapshots;
		this.lockManager = lockManager;
		for (Status status : Status.values()) {
			this.finishedTasks.put(status, new AtomicLong());
		}
	}

	/**
	 * Sets the factory of the jobs, which run the tasks through their handlers (built after the
	 * task manager, as the handlers submit tasks to it).
	 *
	 * @param taskJobFactory the factory
	 */
	public void setJobFactory(TaskJobFactory taskJobFactory) {
		this.jobFactory = taskJobFactory;
	}

	/**
	 * Sets the clustering (when enabled; built after the task manager, as it uses it).
	 *
	 * @param coordinator the cluster coordinator
	 */
	public void setCluster(ClusterCoordinator coordinator) {
		this.cluster = coordinator;
	}

	/**
	 * Gets the clustering.
	 *
	 * @return the cluster coordinator ({@link ClusterCoordinator#NONE} without clustering)
	 */
	public ClusterCoordinator cluster() {
		return this.cluster;
	}

	/**
	 * Gets the transaction support of the task engine.
	 *
	 * @return the transaction support
	 */
	public Transactions transactions() {
		return this.transactions;
	}

	/**
	 * Gets the task and device locks.
	 *
	 * @return the lock manager
	 */
	public TaskLockManager lockManager() {
		return this.lockManager;
	}

	@Override
	public String name() {
		return "task manager";
	}

	@Override
	public void start() {
		this.startSchedulers();
		this.rescheduleAll();
		this.cancelOrphanedDelayedTasks();
	}

	@Override
	public void stop() {
		this.shutdown();
	}

	@Override
	public Map<String, Object> stats() {
		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("mode", this.mode.name());
		stats.put("threads", this.threadCount);
		stats.put("running", this.getRunningCount());
		stats.put("started", this.startedTasks.get());
		Map<String, Object> finished = new LinkedHashMap<>();
		for (Map.Entry<Status, AtomicLong> count : this.finishedTasks.entrySet()) {
			if (count.getValue().get() > 0) {
				finished.put(count.getKey().name(), count.getValue().get());
			}
		}
		stats.put("finished", finished);
		stats.put("interruptedOnStop", this.interruptedTasks.get());
		stats.put("queuedAutoSnapshots", this.autoSnapshots.size());
		return stats;
	}

	@Override
	public void cancel(Task task, String reason) throws SchedulerException {
		this.cancelTask(task, reason);
	}

	@Override
	public int threadCount() {
		return this.threadCount;
	}

	/**
	 * Requests all the runners (the other cluster members, and this instance) to load and run their new tasks.
	 */
	public void requestTasksLoad() {
		this.cluster.requestTasksLoad();
		this.scheduleLocalTasks();
	}

	/**
	 * Counts a task finished by a job of this instance.
	 * @param status the final status of the task
	 */
	void countFinishedTask(Status status) {
		this.finishedTasks.get(status).incrementAndGet();
	}

	/**
	 * Counts a task started by a job of this instance.
	 */
	void countStartedTask() {
		this.startedTasks.incrementAndGet();
	}

	/**
	 * Starts the schedulers (without scheduling the existing tasks again, as {@link #start()} does: tests).
	 */
	void startSchedulers() {
		if (this.jobFactory == null) {
			throw new IllegalStateException("The job factory of the task manager isn't set");
		}
		Config current = this.config.current();
		this.threadCount = current.get(THREAD_COUNT);
		// Running device tasks hold a DB connection from the main pool, so the pool must be
		// comfortably larger than the number of task threads
		int maxPoolSize = current.get(ConnectionPools.MAX_POOL_SIZE);
		if (this.threadCount > maxPoolSize - MIN_SPARE_DB_CONNECTIONS) {
			log.warn("The task thread count ({}) is too close to the DB pool max size ({}): "
				+ "tasks may starve the connection pool. Set netshot.db.pooler.maxpoolsize to at least {}.",
				this.threadCount, maxPoolSize,
				this.threadCount + MIN_SPARE_DB_CONNECTIONS);
		}
		try {
			Properties params = new Properties();
			params.put(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, "org.quartz.simpl.SimpleThreadPool");
			params.put("org.quartz.threadPool.threadCount", Integer.toString(this.threadCount));
			params.put(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "NetshotMasterScheduler");
			params.put("org.quartz.scheduler.skipUpdateCheck", "true");
			StdSchedulerFactory factory = new StdSchedulerFactory(params);
			this.masterScheduler = factory.getScheduler();
			// The master jobs get the task manager from the context of their scheduler
			this.masterScheduler.getContext().put(CONTEXT_KEY, this);
			this.masterScheduler.start();
		}
		catch (Exception e) {
			log.error(MarkerFactory.getMarker("FATAL"), "Unable to instantiate the Master Task Manager", e);
			throw new RuntimeException("Unable to instantiate the Master Task Manager.", e);
		}
		try {
			Properties params = new Properties();
			params.put(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, "org.quartz.simpl.SimpleThreadPool");
			params.put("org.quartz.threadPool.threadCount", Integer.toString(this.threadCount));
			params.put(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "NetshotRunnerScheduler");
			params.put("org.quartz.scheduler.skipUpdateCheck", "true");
			StdSchedulerFactory factory = new StdSchedulerFactory(params);
			this.runnerScheduler = factory.getScheduler();
			// The task jobs get their handlers from the factory (Quartz is the one creating them)
			this.runnerScheduler.setJobFactory(this.jobFactory);
			this.runnerScheduler.start();
		}
		catch (Exception e) {
			log.error(MarkerFactory.getMarker("FATAL"), "Unable to instantiate the Runner Task Manager", e);
			throw new RuntimeException("Unable to instantiate the Runner Task Manager.", e);
		}
	}

	/**
	 * Stops the task manager: no task is started any more, the running tasks are given some time
	 * ({@code netshot.shutdown.timeout}) to end, then the ones still running are marked as failed.
	 */
	public void shutdown() {
		Scheduler master = this.masterScheduler;
		Scheduler runner = this.runnerScheduler;
		if (master == null || runner == null) {
			return;
		}
		try {
			master.standby();
			runner.standby();
			long timeout = this.config.current().get(SHUTDOWN_TIMEOUT) * 1000L;
			long deadline = System.currentTimeMillis() + timeout;
			List<JobExecutionContext> running = runner.getCurrentlyExecutingJobs();
			if (!running.isEmpty()) {
				log.warn("Waiting up to {}s for {} running task(s) to end.", timeout / 1000, running.size());
			}
			while (!running.isEmpty() && System.currentTimeMillis() < deadline) {
				Thread.sleep(200);
				running = runner.getCurrentlyExecutingJobs();
			}
			for (JobExecutionContext job : running) {
				Object taskId = job.getJobDetail().getJobDataMap().get(TaskJob.NETSHOT_TASK);
				if (taskId instanceof Long id) {
					boolean failed = this.transactions.write(dsl -> REPOSITORY.failRunning(dsl, id,
						"Netshot was stopped while the task was running."));
					if (failed) {
						this.interruptedTasks.incrementAndGet();
						log.warn("Task {} was still running, it's marked as failed.", id);
					}
				}
			}
			master.shutdown(false);
			runner.shutdown(false);
		}
		catch (SchedulerException | DataAccessException e) {
			log.error("Error while stopping the task manager", e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Sets the current TaskManager mode.
	 * @param mode the new mode to set
	 */
	public void setMode(Mode mode) {
		this.mode = mode;
	}

	/**
	 * Returns the current TaskManager mode.
	 * @return the mode
	 */
	public Mode getMode() {
		return this.mode;
	}

	/**
	 * Gets the number of tasks currently running on this instance.
	 * @return the number of running tasks
	 */
	public int getRunningCount() {
		Scheduler runner = this.runnerScheduler;
		if (runner == null) {
			return 0;
		}
		try {
			return runner.getCurrentlyExecutingJobs().size();
		}
		catch (SchedulerException e) {
			return 0;
		}
	}

	/**
	 * Gets the number of task threads.
	 * @return the thread count
	 */
	public int getThreadCount() {
		return this.threadCount;
	}

	/**
	 * Runs some work in a transaction of the system pool (the cluster and master tasks
	 * don't compete with the task threads for the main pool).
	 *
	 * @param <T> the type of the result
	 * @param work the work
	 * @return the result
	 */
	private <T> T inSystemTransaction(Function<DSLContext, T> work) {
		return this.transactions.system(work);
	}

	/**
	 * Called when a task gets scheduled.
	 *
	 * @param task the task
	 */
	private void onSchedule(Task task) {
		if (task.spec() instanceof TakeSnapshotTask snapshot && snapshot.automatic() && snapshot.deviceId() != null) {
			this.autoSnapshots.markQueued(snapshot.deviceId());
		}
	}

	/**
	 * Called when a task gets cancelled.
	 *
	 * @param task the task
	 */
	private void onCancel(Task task) {
		if (task.spec() instanceof TakeSnapshotTask snapshot && snapshot.deviceId() != null) {
			this.autoSnapshots.clear(snapshot.deviceId());
		}
	}

	/**
	 * Add new tasks to master scheduler.
	 */
	public void scheduleNewTasks() {
		try {
			inSystemTransaction(dsl -> {
				for (Task task : REPOSITORY.findByStatus(dsl, Task.Status.NEW)) {
					log.trace("Scheduling new task {}.", task.id());
					onSchedule(task);
					REPOSITORY.changeStatus(dsl, task.id(), Status.SCHEDULED);
					try {
						addTaskToScheduler(task, false, true, false);
					}
					catch (SchedulerException e) {
						log.error("Error while scheduling new task", e);
					}
				}
				return null;
			});
		}
		catch (DataAccessException e) {
			log.error("Database error while scheduling new tasks (master scheduler)", e);
		}
	}

	/**
	 * Assigns a runner to a task and sets the task as waiting.
	 *
	 * @param dsl the jOOQ context
	 * @param task the task
	 */
	public void assignTaskRunner(DSLContext dsl, Task task) {
		long hash = task.runnerHash();
		String runnerId = this.runnerSet.getRunnerId(hash);
		log.info("Task {} (hash {}) is being assigned to runner {}.", task.id(), hash, runnerId);
		REPOSITORY.assignRunner(dsl, task.id(), runnerId);
	}

	/**
	 * Sets the possible task runners (the active cluster members, from the cluster manager).
	 * In-memory only: the caller is responsible for triggering {@link #reassignOrphanTasks()}
	 * afterwards (outside of the cluster network thread, as it hits the database).
	 * @param runners the active cluster members
	 */
	public void setRunners(Collection<Runner> runners) {
		Integer maxPriority = null;
		for (Runner runner : runners) {
			if (maxPriority == null || runner.priority() > maxPriority) {
				maxPriority = runner.priority();
			}
		}
		RunnerSet newRunnerSet = new RunnerSet();
		for (Runner runner : runners) {
			if (runner.priority() == maxPriority) {
				newRunnerSet.add(runner.weight(), runner.instanceId());
			}
		}
		synchronized (this.runnersLock) {
			this.runnerSet = newRunnerSet;
		}
	}



	/**
	 * Requests cancellation of a task that is already RUNNING. Unlike
	 * {@link #cancelTask}, this doesn't touch the Quartz scheduler (the job is genuinely
	 * running, not sitting in the scheduler waiting to fire) -- it just flags the task so
	 * that its own scheduling loop (or device wait) stops the next time it polls
	 * (the currently in-flight child is left to finish naturally).
	 *
	 * @param taskId the ID of the task to request cancellation of
	 * @throws DataAccessException if the task can't be updated
	 */
	public void requestCancel(long taskId) {
		try {
			this.transactions.run(dsl -> REPOSITORY.requestCancel(dsl, taskId));
		}
		catch (DataAccessException e) {
			log.error("Error while requesting cancellation of task {}.", taskId, e);
			throw e;
		}
	}

	/**
	 * Cancels the task.
	 *
	 * @param task the task
	 * @param reason the reason
	 * @throws SchedulerException the scheduler exception
	 * @throws DataAccessException if the task can't be updated
	 */
	public void cancelTask(Task task, String reason) throws SchedulerException {
		this.runnerScheduler.deleteJob(task.identity());
		this.masterScheduler.deleteJob(task.identity());
		try {
			this.transactions.run(dsl -> {
				onCancel(task);
				REPOSITORY.cancel(dsl, task.id(), reason);
			});
		}
		catch (DataAccessException e) {
			log.error("Error while updating the cancelled task.", e);
			throw e;
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
	private void addTaskToScheduler(Task task, boolean runnerTask,
		boolean checkExistence, boolean forceNow) throws SchedulerException {
		Scheduler scheduler = runnerTask ? runnerScheduler : masterScheduler;
		if (checkExistence) {
			if (scheduler.checkExists(task.identity())) {
				log.debug("Task {} is already present in the scheduler.", task.id());
				return;
			}
		}
		Class<? extends Job> jobClass = runnerTask ? TaskJob.class : MasterJob.class;
		JobDetail job = JobBuilder
			.newJob(jobClass)
			.withIdentity(task.identity())
			.build();
		job.getJobDataMap().put(TaskJob.NETSHOT_TASK, task.id());
		TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder
			.newTrigger()
			.withPriority(task.priority());
		Date when = task.nextExecutionDate();
		if (when == null || forceNow) {
			triggerBuilder.startNow();
		}
		else {
			triggerBuilder.startAt(when);
		}
		scheduler.scheduleJob(job, triggerBuilder.build());
	}

	/**
	 * Stores a task (if new) with a status, or changes the status of the stored task.
	 *
	 * @param task the task
	 * @param status the status
	 * @param schedule true to call the schedule hook
	 * @return the stored task
	 */
	private Task store(Task task, Status status, boolean schedule) {
		try {
			return this.transactions.write(dsl -> {
				if (schedule) {
					onSchedule(task);
				}
				if (task.id() == 0) {
					return REPOSITORY.add(dsl, task.withStatus(status));
				}
				REPOSITORY.changeStatus(dsl, task.id(), status);
				return task.withStatus(status);
			});
		}
		catch (RuntimeException e) {
			log.error("Error while saving the new task.", e);
			throw e;
		}
	}

	/**
	 * Adds a task to the system.
	 *
	 * @param task the task (stored if its ID is 0)
	 * @return the stored task
	 * @throws SchedulerException the scheduler exception
	 * @throws DataAccessException if the task can't be stored
	 */
	public Task addTask(Task task) throws SchedulerException {
		log.debug("Adding the task {} (mode {}).", task.id(), this.mode);
		switch (this.mode) {
			case CLUSTER_MASTER: {
				Task stored = store(task, Status.SCHEDULED, true);
				addTaskToScheduler(stored, false, false, false);
				return stored;
			}
			case CLUSTER_MEMBER: {
				Task stored = store(task, Status.NEW, false);
				this.cluster.requestTasksAssignment();
				return stored;
			}
			case SINGLE:
			default: {
				Task stored = store(task, Status.SCHEDULED, true);
				addTaskToScheduler(stored, true, false, false);
				return stored;
			}
		}
	}

	/**
	 * Repeats a task: adds its next occurrence, if it is repeating.
	 *
	 * @param task the task
	 * @throws SchedulerException the scheduler exception
	 * @throws DataAccessException if the task can't be stored
	 */
	public void repeatTask(Task task) throws SchedulerException {
		if (!task.isRepeating()) {
			return;
		}
		this.addTask(task.nextOccurrence());
	}

	/**
	 * Retrieve the tasks assigned to the local instance and add them to the runner scheduler.
	 * The calls are serialized: the master calls this from every task assignment thread, and
	 * concurrent calls would both pass the existence check of the same task.
	 */
	public void scheduleLocalTasks() {
		synchronized (this.localTasksLock) {
			try {
				List<Task> tasks = this.transactions.read(dsl -> REPOSITORY.findWhere(dsl,
					TaskRepository.statusIn(Status.WAITING).and(TASK.RUNNER_ID.eq(this.cluster.localInstanceId()))));
				for (Task task : tasks) {
					log.debug("Scheduling task {} for local runner {}.", task.id(), task.runnerId());
					try {
						addTaskToScheduler(task, true, true, true);
					}
					catch (SchedulerException e) {
						log.error("Unable to schedule the task {}.", task.id(), e);
					}
				}
			}
			catch (DataAccessException e) {
				log.error("Unable to retrieve the waiting tasks from the database", e);
			}
		}
	}

	/**
	 * Finds and reassigns tasks currently assigned to a failed runner.
	 */
	public void reassignOrphanTasks() {
		if (!Mode.CLUSTER_MASTER.equals(this.mode)) {
			return;
		}
		RunnerSet currentRunnerSet = this.runnerSet;
		if (currentRunnerSet == null || currentRunnerSet.map.isEmpty()) {
			return;
		}
		int reassignedCount;
		try {
			reassignedCount = inSystemTransaction(dsl -> {
				Condition active = TaskRepository.statusIn(Status.WAITING, Status.RUNNING);
				List<String> runnerIds = new ArrayList<>(dsl.selectDistinct(TASK.RUNNER_ID).from(TASK)
					.where(active.and(TASK.RUNNER_ID.isNotNull())).fetch(TASK.RUNNER_ID));
				runnerIds.removeIf(runnerId -> currentRunnerSet.map.values().contains(runnerId));
				if (runnerIds.isEmpty()) {
					return 0;
				}
				int count = 0;
				for (Task task : REPOSITORY.findWhere(dsl, active.and(TASK.RUNNER_ID.in(runnerIds).or(TASK.RUNNER_ID.isNull())))) {
					// A member considered as gone may actually still be running the task (e.g. it was
					// just unable to send hello messages for a while): only reassign the task if its
					// lock isn't held anymore (released by the database when the member really died)
					try {
						if (this.lockManager.isHeld(TaskLockManager.NS_TASK, task.id())) {
							log.info("Task {} (runner {}) is still being executed, not reassigning it.",
								task.id(), task.runnerId());
							continue;
						}
					}
					catch (SQLException e) {
						log.error("Unable to check whether task {} is still running, not reassigning it.", task.id(), e);
						continue;
					}
					log.debug("Reassigning task {} (formerly assigned to runner {}).", task.id(), task.runnerId());
					assignTaskRunner(dsl, task);
					count += 1;
				}
				return count;
			});
		}
		catch (Exception e) {
			log.error("Database error while re-assigning orphan tasks", e);
			reassignedCount = 0;
		}
		if (reassignedCount > 0) {
			log.info("{} orphan task(s) reassigned", reassignedCount);
			this.requestTasksLoad();
		}
	}

	/**
	 * Reschedules all tasks. To be executed at startup.
	 */
	public void rescheduleAll() {
		List<Task> tasks;
		try {
			String myId = this.cluster.localInstanceId();
			Condition condition = switch (this.mode) {
				case CLUSTER_MASTER -> TaskRepository.statusIn(Status.NEW, Status.SCHEDULED)
					.or(TaskRepository.statusIn(Status.WAITING, Status.RUNNING).and(TASK.RUNNER_ID.eq(myId)));
				case CLUSTER_MEMBER -> TaskRepository.statusIn(Status.WAITING, Status.RUNNING).and(TASK.RUNNER_ID.eq(myId));
				default -> TaskRepository.statusIn(Status.SCHEDULED, Status.WAITING, Status.RUNNING);
			};
			tasks = inSystemTransaction(dsl -> REPOSITORY.findWhere(dsl, condition));
		}
		catch (DataAccessException e) {
			log.error("Unable to retrieve the tasks from the database.", e);
			throw e;
		}
		Calendar inThirtySeconds = Calendar.getInstance();
		inThirtySeconds.add(Calendar.SECOND, 30);
		for (Task task : tasks) {
			try {
				if (masterScheduler.checkExists(task.identity())) {
					log.trace("The task {} already exists in the master scheduler.", task.id());
					continue;
				}
				if (runnerScheduler.checkExists(task.identity())) {
					log.trace("The task {} already exists in the runner scheduler.", task.id());
					continue;
				}
			}
			catch (SchedulerException e) {
				log.error("Error while checking task existence in the scheduler", e);
			}
			try {
				Date when = task.nextExecutionDate();
				if (when == null || when.after(inThirtySeconds.getTime())) {
					this.addTask(task);
				}
				else {
					this.cancelTask(task, "Task skipped, too late to run.");
				}
			}
			catch (SchedulerException e) {
				log.error("Unable to schedule the task {}.", task.id(), e);
			}
		}
	}

	/**
	 * Cancels any DELAYED task left with no parent -- a pre-created child of a group-based task
	 * whose parent no longer exists (deletion clears a child's {@code parent_task_id} rather than
	 * cascading), so nothing will ever promote it. To be called once at
	 * startup, on every node regardless of clustering mode: it's a plain, idempotent bulk update,
	 * so no cross-node coordination is needed the way {@link #reassignOrphanTasks()} needs.
	 */
	public void cancelOrphanedDelayedTasks() {
		try {
			int count = this.transactions.write(dsl -> REPOSITORY.setStatus(dsl, TASK.PARENT_TASK_ID.isNull(),
				Status.CANCELLED, Status.DELAYED));
			log.info("{} orphaned DELAYED task(s) cancelled at startup", count);
		}
		catch (DataAccessException e) {
			log.error("Error while cancelling orphaned DELAYED tasks", e);
		}
	}
}
