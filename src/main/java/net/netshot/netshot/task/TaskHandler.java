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

/**
 * Executes a type of task. The task entity only holds the data of the task; its execution lives in
 * a handler which receives its collaborators by constructor. The handler reports the outcome on the
 * task itself (status, log, result attributes), which the task engine then saves.
 *
 * @param <T> the type of task this handler executes
 */
public interface TaskHandler<T extends Task> {

	/**
	 * Gets the type of task this handler executes. Each concrete task type has exactly one handler.
	 *
	 * @return the task type
	 */
	Class<T> taskType();

	/**
	 * Runs the task. Any status set on the task is kept; a task left in RUNNING status is
	 * considered failed by the engine.
	 *
	 * @param task the task to run
	 */
	void run(T task);
}
