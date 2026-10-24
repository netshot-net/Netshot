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

import net.netshot.netshot.task.type.TaskSpec;

/**
 * Executes a type of task. The task only holds the data of the task; its execution lives in
 * a handler which receives its collaborators by constructor. The handler reports the outcome on the
 * run (status, log, results), which the task engine then saves.
 *
 * @param <S> the type of task (specification) this handler executes
 */
public interface TaskHandler<S extends TaskSpec> {

	/**
	 * Gets the type of task this handler executes. Each task type has exactly one handler.
	 *
	 * @return the task type
	 */
	Class<S> taskType();

	/**
	 * Runs the task. Any status set on the run is kept; a run left in RUNNING status is
	 * considered failed by the engine.
	 *
	 * @param task the task to run
	 * @param spec the specification of the task
	 * @param run the run, to report the outcome to
	 */
	void run(Task task, S spec, TaskRun run);
}
