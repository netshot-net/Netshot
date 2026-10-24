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
package net.netshot.netshot.testing;

import static net.netshot.netshot.platform.database.schema.Tables.DEBUG_LOG;
import static net.netshot.netshot.platform.database.schema.Tables.TASK;
import static net.netshot.netshot.platform.database.schema.Tables.TASK_DEVICE_LIST_MEMBERS;

import java.util.List;

import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskRepository;

/**
 * Stores and reads tasks for the tests, each call in its own transaction, through the repository.
 */
public final class TestTasks {

	private static final Transactions TRANSACTIONS = new Transactions();

	private static final TaskRepository REPOSITORY = new TaskRepository();

	private TestTasks() {
	}

	/**
	 * Stores a task (without scheduling it).
	 *
	 * @param task the task (its ID is ignored)
	 * @return the stored task
	 */
	public static Task add(Task task) {
		return TRANSACTIONS.write(dsl -> REPOSITORY.add(dsl, task));
	}

	/**
	 * Stores a task with a status (without scheduling it).
	 *
	 * @param task the task (its ID is ignored)
	 * @param status the status
	 * @return the stored task
	 */
	public static Task add(Task task, Task.Status status) {
		return add(task.withStatus(status));
	}

	/**
	 * Reads a task.
	 *
	 * @param id the ID
	 * @return the task, null if there is no such task
	 */
	public static Task find(long id) {
		return TRANSACTIONS.read(dsl -> REPOSITORY.find(dsl, id));
	}

	/**
	 * Lists all the tasks, by ID.
	 *
	 * @return the tasks
	 */
	public static List<Task> all() {
		return TRANSACTIONS.read(dsl -> REPOSITORY.findWhere(dsl, org.jooq.impl.DSL.noCondition()));
	}

	/**
	 * Lists the children of a task, in order.
	 *
	 * @param parentId the ID of the parent task
	 * @return the children
	 */
	public static List<Task> children(long parentId) {
		return TRANSACTIONS.read(dsl -> REPOSITORY.findChildren(dsl, parentId));
	}

	/**
	 * Gets the debug log of a task.
	 *
	 * @param id the ID of the task
	 * @return the debug log, null for none
	 */
	public static String debugLog(long id) {
		return TRANSACTIONS.read(dsl -> REPOSITORY.findDebugLog(dsl, id));
	}

	/**
	 * Attaches a debug log to a task.
	 *
	 * @param id the ID of the task
	 * @param text the debug log
	 */
	public static void setDebugLog(long id, String text) {
		TRANSACTIONS.run(dsl -> {
			long debugLogId = dsl.insertInto(DEBUG_LOG).set(DEBUG_LOG.TEXT, text).returning(DEBUG_LOG.ID).fetchOne(DEBUG_LOG.ID);
			dsl.update(TASK).set(TASK.DEBUG_LOG, debugLogId).where(TASK.ID.eq(id)).execute();
		});
	}

	/**
	 * Gets the stored device list of a task (the list positions).
	 *
	 * @param id the ID of the task
	 * @return the positions and device IDs, by position
	 */
	public static List<long[]> deviceListMembers(long id) {
		return TRANSACTIONS.read(dsl -> dsl.select(TASK_DEVICE_LIST_MEMBERS.LIST_POSITION, TASK_DEVICE_LIST_MEMBERS.DEVICE_ID)
			.from(TASK_DEVICE_LIST_MEMBERS).where(TASK_DEVICE_LIST_MEMBERS.TASK_ID.eq(id)).orderBy(TASK_DEVICE_LIST_MEMBERS.LIST_POSITION)
			.fetch(r -> new long[] {r.value1(), r.value2()}));
	}

	/**
	 * Changes the status of a task.
	 *
	 * @param id the ID
	 * @param status the new status
	 */
	public static void setStatus(long id, Task.Status status) {
		TRANSACTIONS.run(dsl -> REPOSITORY.setStatus(dsl, id, status));
	}

	/**
	 * Deletes a task.
	 *
	 * @param id the ID
	 */
	public static void delete(long id) {
		TRANSACTIONS.run(dsl -> dsl.deleteFrom(TASK).where(TASK.ID.eq(id)).execute());
	}

	/**
	 * Deletes all the tasks.
	 */
	public static void deleteAll() {
		TRANSACTIONS.run(dsl -> dsl.deleteFrom(TASK).execute());
	}
}
