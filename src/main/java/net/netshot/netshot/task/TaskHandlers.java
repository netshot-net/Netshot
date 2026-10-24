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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.netshot.netshot.task.type.TaskSpec;

/**
 * The registry of the task handlers, by task type. It is checked when built: every task
 * type ({@link TaskSpec}) must have exactly one handler.
 */
public class TaskHandlers {

	private final Map<Class<?>, TaskHandler<?>> handlers = new HashMap<>();

	/**
	 * Builds the registry.
	 *
	 * @param handlers all the task handlers
	 * @throws IllegalStateException if two handlers serve the same task type, or if a task type has no handler
	 */
	public TaskHandlers(Collection<? extends TaskHandler<?>> handlers) {
		for (TaskHandler<?> handler : handlers) {
			TaskHandler<?> previous = this.handlers.put(handler.taskType(), handler);
			if (previous != null) {
				throw new IllegalStateException("Several task handlers for the task type " + handler.taskType().getSimpleName());
			}
		}
		List<String> missing = new ArrayList<>();
		for (Class<?> specClass : TaskSpec.class.getPermittedSubclasses()) {
			if (!this.handlers.containsKey(specClass)) {
				missing.add(specClass.getSimpleName());
			}
		}
		if (!missing.isEmpty()) {
			throw new IllegalStateException("No task handler for the task type(s) " + String.join(", ", missing));
		}
	}

	/**
	 * Runs a task with its handler.
	 *
	 * @param task the task
	 * @param run the run
	 * @throws IllegalStateException if there is no handler for the type of task
	 */
	public void run(Task task, TaskRun run) {
		TaskHandler<?> handler = this.handlers.get(task.spec().getClass());
		if (handler == null) {
			throw new IllegalStateException("No task handler for the task type " + task.typeName());
		}
		TaskHandlers.run(handler, task, run);
	}

	private static <S extends TaskSpec> void run(TaskHandler<S> handler, Task task, TaskRun run) {
		handler.run(task, handler.taskType().cast(task.spec()), run);
	}
}
