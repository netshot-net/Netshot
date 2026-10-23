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

import org.quartz.SchedulerException;

/**
 * What the task use cases need from the task engine, besides the submission of new tasks
 * (see {@link TaskSubmitter}): the cancellation of tasks and the size of the engine.
 * The composition root ({@code boot.Wiring}) provides the real one, the tests a fake one.
 */
public interface TaskEngine {

	/**
	 * Asks a running task to cancel itself (honored by the task while it is waiting for its turn).
	 *
	 * @param taskId the ID of the task
	 */
	void requestCancel(long taskId);

	/**
	 * Cancels a scheduled task: removes it from the scheduler and stores it as cancelled.
	 *
	 * @param task the task
	 * @param reason the reason, logged in the task
	 * @throws SchedulerException if the task can't be removed from the scheduler
	 * @throws org.jooq.exception.DataAccessException if the task can't be stored
	 */
	void cancel(Task task, String reason) throws SchedulerException;

	/**
	 * Gets the number of task threads.
	 *
	 * @return the number of threads
	 */
	int threadCount();
}
