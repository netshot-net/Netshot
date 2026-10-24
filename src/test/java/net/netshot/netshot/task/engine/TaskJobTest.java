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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.TaskHandlers;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.task.type.TaskSpec;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * Tests for the task execution flow of {@link TaskJob} when the task handler fails.
 */
@DatabaseTest
@DisplayName("Task execution with a failing handler (TaskJob, DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
public class TaskJobTest {

	Device device;

	@BeforeAll
	void initDb() throws Exception {
		TaskManager.init(Wiring.get().taskJobFactory());
		TaskLockManager.init(false);
	}

	@BeforeEach
	void createData() {
		Domain domain = TestDevices.addDomain("Test domain", "Fake domain for tests");
		Device fake = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1);
		device = TestDevices.add(fake, FakeDeviceFactory.getFakeCiscoIosConfig(fake));
	}

	@AfterEach
	void cleanUpData() {
		TestTasks.deleteAll();
		TestDevices.deleteAll();
	}

	/**
	 * Builds a handler which throws the given exception.
	 *
	 * @param <S> the task type
	 * @param taskType the task type
	 * @param exception the exception to throw
	 * @return the handler
	 */
	private static <S extends TaskSpec> TaskHandler<S> throwingHandler(Class<S> taskType, RuntimeException exception) {
		return new TaskHandler<>() {
			@Override
			public Class<S> taskType() {
				return taskType;
			}

			@Override
			public void run(Task task, S spec, TaskRun run) {
				throw exception;
			}
		};
	}

	@Test
	@DisplayName("A task whose handler throws a RuntimeException is saved as failed, with the error in its log")
	void runtimeExceptionFailsTheTask() throws Exception {
		RuntimeException exception = new IllegalStateException("Unexpected state");
		List<TaskHandler<?>> handlers = Arrays.stream(TaskSpec.class.getPermittedSubclasses())
			.<TaskHandler<?>>map(type -> throwingHandler(type.asSubclass(TaskSpec.class), exception))
			.toList();
		TaskJob taskJob = new TaskJob(new TaskHandlers(handlers), Wiring.get().domainEvents());
		long id = TestTasks.add(TakeSnapshotTask.of(device, "test", "tester", false, true, true), Status.SCHEDULED).id();

		Assertions.assertDoesNotThrow(() -> taskJob.runTask(id));

		Task task = TestTasks.find(id);
		Assertions.assertEquals(Status.FAILURE, task.status());
		Assertions.assertTrue(task.log().contains("Unexpected state"),
			"The task log should hold the error, got: " + task.log());
	}
}
