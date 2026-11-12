/**
 * Copyright 2013-present Netshot
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.quartz.Scheduler;
import org.quartz.impl.SchedulerRepository;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * Loading of the tasks assigned to the local cluster member into the runner scheduler
 * ({@link TaskManager#scheduleLocalTasks()}). On the master, every task assignment thread calls it,
 * so concurrent calls see the same waiting tasks.
 */
@DatabaseTest
@DisplayName("Loading of the local tasks into the runner scheduler (DB-backed)")
@TestInstance(Lifecycle.PER_CLASS)
class LocalTaskSchedulingTest {

	private static final String RUNNER_ID = "testrunner0000000001";

	private Device device;
	private Scheduler runnerScheduler;
	private ListAppender<ILoggingEvent> appender;

	@BeforeEach
	void init() throws Exception {
		TaskManager taskManager = TestWiring.get().taskManager();
		taskManager.startSchedulers();
		taskManager.setCluster(new ClusterCoordinator() {
			@Override
			public String localInstanceId() {
				return RUNNER_ID;
			}

			@Override
			public void requestTasksAssignment() {
			}

			@Override
			public void requestTasksLoad() {
			}

			@Override
			public void requestAutoSnapshot(long deviceId) {
			}
		});
		// The jobs must stay in the scheduler to be checked
		this.runnerScheduler = SchedulerRepository.getInstance().lookup("NetshotRunnerScheduler");
		this.runnerScheduler.standby();
		Domain domain = TestDevices.addDomain("Test domain", "Fake domain for tests");
		Device fake = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 1);
		this.device = TestDevices.add(fake.toBuilder().status(Device.Status.DISABLED).build(),
			FakeDeviceFactory.getFakeCiscoIosConfig(fake));
		this.appender = new ListAppender<>();
		this.appender.start();
		((Logger) LoggerFactory.getLogger(TaskManager.class)).addAppender(this.appender);
	}

	@AfterEach
	void cleanUp() throws Exception {
		((Logger) LoggerFactory.getLogger(TaskManager.class)).detachAppender(this.appender);
		TestWiring.get().taskManager().setCluster(ClusterCoordinator.NONE);
		this.runnerScheduler.clear();
		this.runnerScheduler.start();
		TestTasks.deleteAll();
		TestDevices.deleteAll();
	}

	@Test
	@DisplayName("Concurrent loads (one per task assignment on the master) schedule each task once, without error")
	void concurrentLoads() throws Exception {
		List<Task> tasks = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			Task task = TakeSnapshotTask.of(this.device, "test", "tester", false, true, true)
				.toBuilder().runnerId(RUNNER_ID).build();
			tasks.add(TestTasks.add(task, Status.WAITING));
		}

		int threadCount = 16;
		CyclicBarrier barrier = new CyclicBarrier(threadCount);
		List<Thread> threads = new ArrayList<>();
		for (int i = 0; i < threadCount; i++) {
			Thread thread = new Thread(() -> {
				try {
					barrier.await();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
				TestWiring.get().taskManager().scheduleLocalTasks();
			});
			thread.start();
			threads.add(thread);
		}
		for (Thread thread : threads) {
			thread.join(30000);
		}

		List<String> errors = this.appender.list.stream()
			.filter(e -> e.getLevel().isGreaterOrEqual(Level.ERROR))
			.map(ILoggingEvent::getFormattedMessage)
			.toList();
		Assertions.assertEquals(List.of(), errors, "No error should be logged");
		for (Task task : tasks) {
			Assertions.assertTrue(this.runnerScheduler.checkExists(task.identity()),
				"Task " + task.id() + " should be in the runner scheduler");
		}
	}
}
