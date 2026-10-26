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

import org.quartz.Job;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.simpl.SimpleJobFactory;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import net.netshot.netshot.platform.event.DomainEvents;
import net.netshot.netshot.task.TaskHandlers;

/**
 * The Quartz job factory: builds the {@link TaskJob}s with the objects they need, and any other
 * job the usual way.
 */
public class TaskJobFactory implements JobFactory {

	private final TaskHandlers handlers;

	private final DomainEvents domainEvents;

	private final TaskManager taskManager;

	private final JobFactory defaultFactory = new SimpleJobFactory();

	/**
	 * Creates the factory.
	 *
	 * @param handlers the task handlers, given to the task jobs
	 * @param domainEvents where the task jobs publish the end of the tasks
	 * @param taskManager the task manager (locks, clustering, repetition of the tasks)
	 */
	public TaskJobFactory(TaskHandlers handlers, DomainEvents domainEvents, TaskManager taskManager) {
		this.handlers = handlers;
		this.domainEvents = domainEvents;
		this.taskManager = taskManager;
	}

	/**
	 * Builds a task job.
	 *
	 * @return a new task job
	 */
	public TaskJob newTaskJob() {
		return new TaskJob(this.taskManager.transactions(), this.handlers, this.domainEvents, this.taskManager);
	}

	@Override
	public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler) throws SchedulerException {
		if (TaskJob.class.equals(bundle.getJobDetail().getJobClass())) {
			return this.newTaskJob();
		}
		return this.defaultFactory.newJob(bundle, scheduler);
	}
}
