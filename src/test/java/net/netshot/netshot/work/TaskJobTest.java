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

import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.work.Task.Status;

/**
 * Tests for the run step of {@link TaskJob}.
 */
@DisplayName("Task run (TaskJob)")
public class TaskJobTest {

	@BeforeAll
	static void initConfig() {
		// Loading the task classes reads the configuration
		TestConfig.init(new Properties());
	}

	/**
	 * Builds a task which runs the given code.
	 * @param code the code of the task
	 * @return the task
	 */
	private static Task taskRunning(Runnable code) {
		Task task = new Task("test", "target", "tester") {
			@Override
			public JobKey getIdentity() {
				return new JobKey("test");
			}

			@Override
			public String getTaskDescription() {
				return "Test task";
			}

			@Override
			public void run() {
				code.run();
			}
		};
		task.setRunning();
		return task;
	}

	@Test
	@DisplayName("A task which throws a RuntimeException fails, with the error in its log")
	void runtimeExceptionFailsTheTask() {
		Task task = taskRunning(() -> {
			throw new IllegalStateException("Unexpected state");
		});
		TaskJob.runSafely(task);
		Assertions.assertEquals(Status.FAILURE, task.getStatus());
		Assertions.assertTrue(task.getLog().contains("Unexpected state"),
			"The task log should hold the error, got: " + task.getLog());
	}

	@Test
	@DisplayName("A task which ends normally keeps its own status")
	void normalTaskKeepsItsStatus() {
		Task task = taskRunning(() -> { });
		TaskJob.runSafely(task);
		Assertions.assertEquals(Status.RUNNING, task.getStatus());
	}
}
