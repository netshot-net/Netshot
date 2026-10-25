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

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.ScheduleType;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;

/**
 * Tests of {@link TaskManager#repeatTask(Task)}: once a recurring task has run, its next occurrence
 * is stored, with the same parameters (the group or the device list included).
 *
 * <p>Regression tests: with the former Hibernate entities, the clone of a task reloaded after its run
 * touched lazy collections which were not initialized, and the next occurrence was silently never stored.</p>
 */
@DatabaseTest
@DisplayName("Recurring task rescheduling after execution (TaskManager.repeatTask, DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
public class TaskRepeatTest {

	@BeforeAll
	void initDb() throws Exception {
		TestWiring.get().taskManager().startSchedulers();
	}

	@AfterEach
	void cleanUpData() {
		TestTasks.deleteAll();
		TestDevices.deleteAll();
	}

	// Stores a recurring purge task, restricted to a group (or not), and returns its ID.
	private long persistRecurringPurgeTask(Long groupId) {
		return TestTasks.add(Task.of(PurgeDatabaseTask.builder().deviceGroupId(groupId).days(1).configDays(0).moduleDays(0).build(),
			"Test", "Global", "tester").scheduled(Instant.now(), ScheduleType.DAILY, 1)).id();
	}

	// The tasks but the given one
	private static List<Task> others(long taskId) {
		return TestTasks.all().stream().filter(t -> t.id() != taskId).toList();
	}

	@Test
	@DisplayName("An unrestricted recurring purge task is repeated after execution")
	void recurringPurgeTaskIsRepeatedAfterExecution() {
		long taskId = this.persistRecurringPurgeTask(null);
		Task reloaded = TestTasks.find(taskId);

		Assertions.assertDoesNotThrow(() -> TestWiring.get().taskManager().repeatTask(reloaded),
			"Repeating the task should not throw");

		List<Task> others = others(taskId);
		Assertions.assertEquals(1, others.size(), "A new occurrence of the recurring task should have been persisted");
		Assertions.assertEquals(reloaded.spec(), others.get(0).spec(), "Same parameters");
		Assertions.assertEquals(Task.Status.SCHEDULED, others.get(0).status());
	}

	@Test
	@DisplayName("A per-group recurring purge task is repeated after execution, still restricted to the group")
	void recurringPerGroupPurgeTaskIsRepeatedAfterExecution() {
		long groupId;
		groupId = TestDevices.addStaticGroup("Group for repeat test").id();
		long taskId = this.persistRecurringPurgeTask(groupId);
		Task reloaded = TestTasks.find(taskId);

		Assertions.assertDoesNotThrow(() -> TestWiring.get().taskManager().repeatTask(reloaded),
			"Repeating a per-group task should not throw");

		List<Task> others = others(taskId);
		Assertions.assertEquals(1, others.size(), "A new occurrence of the recurring task should have been persisted");
		Assertions.assertEquals(groupId, ((PurgeDatabaseTask) others.get(0).spec()).deviceGroupId());
	}
}
