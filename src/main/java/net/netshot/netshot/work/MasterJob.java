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

import org.hibernate.Session;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.TaskManager;
import net.netshot.netshot.cluster.ClusterManager;
import net.netshot.netshot.database.Database;

/**
 * A Quartz job which runs a Netshot task.
 */
@DisallowConcurrentExecution
@Slf4j
public class MasterJob implements Job {

	/** The Constant NETSHOT_TASK. */
	public static final String NETSHOT_TASK = "Netshot Task";

	/**
	 * Instantiates a new task job.
	 */
	public MasterJob() {
	}

	/*(non-Javadoc)
	 * @see org.quartz.Job#execute(org.quartz.JobExecutionContext)
	 */
	public void execute(JobExecutionContext context) throws JobExecutionException {
		log.debug("Starting master task job.");
		Long id = (Long) context.getJobDetail().getJobDataMap()
			.get(NETSHOT_TASK);
		log.trace("The task id is {}.", id);
		Task task = null;
		Session session = Database.getSession();
		try {
			Thread.sleep(500);
			session.beginTransaction();
			task = (Task) session.get(Task.class, id);
			if (task == null) {
				log.error("The retrieved task {} is null.", id);
			}
			if (task != null && task.getStatus() == Task.Status.CANCELLED) {
				log.debug("Task {} was cancelled before being assigned to a runner, skipping.", id);
			}
			else {
				TaskManager.assignTaskRunner(task);
				session.merge(task);
			}
			session.getTransaction().commit();
		}
		catch (Exception e) {
			log.error("Error while retrieving and assigning the task to a runner.", e);
			Database.rollbackSilently(session);
			throw new JobExecutionException("Unable to assign the task to a runner.");
		}
		finally {
			session.close();
		}
		ClusterManager.requestTasksLoad();
	}

}
