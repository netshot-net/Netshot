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
package net.netshot.netshot.task.handler;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.TaskHandlers;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunGroupDiagnosticsTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TaskSpec;
import net.netshot.netshot.testing.UnitTest;

/**
 * Tests of the task handler registry and of the handlers which can be checked without a database.
 */
@UnitTest
@DisplayName("Task handlers")
public class TaskHandlersTest {

	/**
	 * All the handlers; they only keep their collaborators, so none is needed to build them.
	 * @return the handlers
	 */
	private static List<TaskHandler<?>> allHandlers() {
		return new ArrayList<>(List.of(
			new CheckComplianceHandler(null),
			new CheckGroupComplianceHandler(null),
			new CheckGroupSoftwareHandler(),
			new DiscoverDeviceTypeHandler(null, null),
			new PurgeDatabaseHandler(),
			new RunDeviceGroupScriptHandler(null, null),
			new RunDeviceScriptHandler(null, null),
			new RunDiagnosticsHandler(null, null, null),
			new RunGroupDiagnosticsHandler(null),
			new ScanSubnetsHandler(null),
			new TakeGroupSnapshotHandler(null),
			new TakeSnapshotHandler(null, null)));
	}

	@Test
	@DisplayName("Every task type has a handler")
	void everyTaskTypeHasAHandler() {
		Assertions.assertTrue(TaskSpec.class.getPermittedSubclasses().length > 0, "No task type found");
		List<TaskHandler<?>> handlers = allHandlers();
		Assertions.assertDoesNotThrow(() -> new TaskHandlers(handlers));
		for (Class<?> specClass : TaskSpec.class.getPermittedSubclasses()) {
			Assertions.assertTrue(handlers.stream().anyMatch(handler -> handler.taskType().equals(specClass)),
				"No handler for " + specClass.getSimpleName());
		}
	}

	@Test
	@DisplayName("The registry refuses to start when a task type has no handler")
	void missingHandlerIsRejected() {
		List<TaskHandler<?>> handlers = allHandlers();
		handlers.removeIf(handler -> handler instanceof TakeSnapshotHandler);
		IllegalStateException e = Assertions.assertThrows(IllegalStateException.class, () -> new TaskHandlers(handlers));
		Assertions.assertTrue(e.getMessage().contains("TakeSnapshotTask"), e.getMessage());
	}

	@Test
	@DisplayName("The registry refuses two handlers for the same task type")
	void duplicateHandlerIsRejected() {
		List<TaskHandler<?>> handlers = allHandlers();
		handlers.add(new PurgeDatabaseHandler());
		Assertions.assertThrows(IllegalStateException.class, () -> new TaskHandlers(handlers));
	}

	@Test
	@DisplayName("The registry runs a task with the handler of its type")
	void registryDispatchesByType() {
		TaskHandlers handlers = new TaskHandlers(allHandlers());
		Task task = TaskRunner.forDevices(new CheckGroupSoftwareTask(null, TaskRunner.ids()), "test", "tester");
		TaskRun run = new TaskRun(task);
		handlers.run(task, run);
		Assertions.assertEquals(Status.CANCELLED, run.getStatus());
	}

	@Test
	@DisplayName("Group handlers cancel a task which has neither a group nor a device")
	void emptyGroupTasksAreCancelled() {
		List<Task> tasks = List.of(
			TaskRunner.forDevices(new CheckGroupComplianceTask(null, TaskRunner.ids()), "test", "tester"),
			TaskRunner.forDevices(new CheckGroupSoftwareTask(null, TaskRunner.ids()), "test", "tester"),
			TaskRunner.forDevices(new TakeGroupSnapshotTask(null, TaskRunner.ids(), -1, true, true), "test", "tester"),
			TaskRunner.forDevices(new RunGroupDiagnosticsTask(null, TaskRunner.ids(), true), "test", "tester"),
			TaskRunner.forDevices(new RunDeviceGroupScriptTask(null, null, null, null, null, false, false, false), "test", "tester"));
		TaskHandlers handlers = new TaskHandlers(allHandlers());
		for (Task task : tasks) {
			TaskRun run = new TaskRun(task);
			handlers.run(task, run);
			Assertions.assertEquals(Status.CANCELLED, run.getStatus(), task.typeName());
			Assertions.assertTrue(run.getLog().contains("Neither a device group nor a device list is set"), task.typeName());
		}
	}
}
