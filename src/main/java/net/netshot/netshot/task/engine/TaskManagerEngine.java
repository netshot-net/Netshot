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

import org.quartz.SchedulerException;

import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskEngine;

/**
 * The {@link TaskEngine} backed by the static {@link TaskManager}.
 */
public class TaskManagerEngine implements TaskEngine {

	@Override
	public void requestCancel(long taskId) {
		TaskManager.requestCancel(taskId);
	}

	@Override
	public void cancel(Task task, String reason) throws SchedulerException {
		TaskManager.cancelTask(task, reason);
	}

	@Override
	public int threadCount() {
		return TaskManager.getThreadCount();
	}
}
