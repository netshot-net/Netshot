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

import java.util.LinkedHashMap;
import java.util.Map;

import net.netshot.netshot.platform.lifecycle.Service;

/**
 * The task manager as a service: starts the schedulers and schedules the existing tasks again,
 * then, when Netshot stops, gives the running tasks some time to end (see {@link TaskManager#shutdown()}).
 */
public final class TaskManagerService implements Service {

	/** The factory of the jobs. */
	private final TaskJobFactory taskJobFactory;

	/**
	 * Creates the service.
	 *
	 * @param taskJobFactory the factory of the jobs, which run the tasks through their handlers
	 */
	public TaskManagerService(TaskJobFactory taskJobFactory) {
		this.taskJobFactory = taskJobFactory;
	}

	@Override
	public String name() {
		return "task manager";
	}

	@Override
	public void start() {
		TaskManager.init(this.taskJobFactory);
		TaskManager.rescheduleAll();
		TaskManager.cancelOrphanedDelayedTasks();
	}

	@Override
	public void stop() {
		TaskManager.shutdown();
	}

	@Override
	public Map<String, Object> stats() {
		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("mode", TaskManager.getMode().name());
		stats.put("threads", TaskManager.getThreadCount());
		stats.put("running", TaskManager.getRunningCount());
		return stats;
	}
}
