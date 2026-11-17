/**
 * Copyright 2013-present Netshot
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

import org.quartz.Job;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.simpl.SimpleJobFactory;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.event.DomainEvents;
import net.netshot.netshot.task.TaskHandlers;
import net.netshot.netshot.task.TaskRepository;
import net.netshot.netshot.platform.config.ConfigStore;

/**
 * The Quartz job factory: builds the {@link TaskJob}s with the objects they need, and any other
 * job the usual way.
 */
public class TaskJobFactory implements JobFactory {

	private final TaskHandlers handlers;

	private final DomainEvents domainEvents;

	private final Transactions transactions;
	private final TaskManager taskManager;
	private final TaskLockManager lockManager;
	private final TaskRepository repository;

	/** The interval between two checks of the lock of a device, from the configuration. */
	private final ConfigStore.Binding<Integer> deviceLockPollInterval;

	private final JobFactory defaultFactory = new SimpleJobFactory();

	/**
	 * Creates the factory.
	 *
	 * @param transactions the transaction support (for the task jobs)
	 * @param handlers the task handlers, given to the task jobs
	 * @param domainEvents where the task jobs publish the end of the tasks
	 * @param taskManager the task manager (clustering, statistics, repetition of the tasks)
	 * @param lockManager the task and device locks
	 * @param repository the task repository (for the task jobs)
	 * @param config the configuration store (the interval between two checks of the lock of a device)
	 */
	public TaskJobFactory(Transactions transactions, TaskHandlers handlers, DomainEvents domainEvents, TaskManager taskManager,
		TaskLockManager lockManager, TaskRepository repository, ConfigStore config) {
		this.transactions = transactions;
		this.handlers = handlers;
		this.domainEvents = domainEvents;
		this.taskManager = taskManager;
		this.lockManager = lockManager;
		this.repository = repository;
		this.deviceLockPollInterval = config.bind(current -> current.get(TaskJob.DEVICE_LOCK_POLL_INTERVAL));
	}

	/**
	 * Builds a task job.
	 *
	 * @return a new task job
	 */
	public TaskJob newTaskJob() {
		return new TaskJob(this.transactions, this.handlers, this.domainEvents, this.taskManager, this.lockManager, this.repository,
			this.deviceLockPollInterval);
	}

	@Override
	public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler) throws SchedulerException {
		if (TaskJob.class.equals(bundle.getJobDetail().getJobClass())) {
			return this.newTaskJob();
		}
		return this.defaultFactory.newJob(bundle, scheduler);
	}
}
