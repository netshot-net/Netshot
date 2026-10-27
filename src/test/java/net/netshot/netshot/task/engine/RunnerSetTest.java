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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.task.engine.TaskManager.RunnerSet;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Cluster runner selection")
public class RunnerSetTest {

	private static final int DEVICE_COUNT = 10000;

	private static RunnerSet runnerSet(Object... weightsAndIds) {
		RunnerSet set = new RunnerSet();
		for (int i = 0; i < weightsAndIds.length; i += 2) {
			set.add(((Number) weightsAndIds[i]).doubleValue(), (String) weightsAndIds[i + 1]);
		}
		return set;
	}

	@Test
	@DisplayName("A device always gets the same runner")
	void stableRunnerPerDevice() {
		RunnerSet set = runnerSet(100, "runner1", 100, "runner2");
		Set<String> used = new HashSet<>();
		for (long deviceId = 1; deviceId <= DEVICE_COUNT; deviceId++) {
			String runnerId = set.getRunnerId(deviceId);
			for (int i = 0; i < 5; i++) {
				Assertions.assertEquals(runnerId, set.getRunnerId(deviceId),
					"Device %d didn't get the same runner".formatted(deviceId));
			}
			used.add(runnerId);
		}
		Assertions.assertEquals(Set.of("runner1", "runner2"), used, "Both runners should get devices");
	}

	@Test
	@DisplayName("The selection doesn't depend on the order the runners were added in")
	void independentOfRunnerOrder() {
		RunnerSet set1 = runnerSet(100, "runner1", 80, "runner2", 20, "runner3");
		RunnerSet set2 = runnerSet(20, "runner3", 100, "runner1", 80, "runner2");
		for (long deviceId = 1; deviceId <= DEVICE_COUNT; deviceId++) {
			Assertions.assertEquals(set1.getRunnerId(deviceId), set2.getRunnerId(deviceId),
				"Device %d got different runners".formatted(deviceId));
		}
	}

	@Test
	@DisplayName("Devices are spread according to the runner weights")
	void weightedDistribution() {
		RunnerSet set = runnerSet(80, "runner2", 20, "runner3");
		Map<String, Integer> counts = new HashMap<>();
		for (long deviceId = 1; deviceId <= DEVICE_COUNT; deviceId++) {
			counts.merge(set.getRunnerId(deviceId), 1, Integer::sum);
		}
		double ratio = counts.getOrDefault("runner2", 0) / (double) DEVICE_COUNT;
		Assertions.assertEquals(0.8, ratio, 0.03, "runner2 should get about 80% of the devices");
	}

	@Test
	@DisplayName("Removing a runner only moves the devices it had")
	void minimalReassignment() {
		RunnerSet before = runnerSet(100, "runner1", 100, "runner2", 100, "runner3");
		RunnerSet after = runnerSet(100, "runner1", 100, "runner2");
		for (long deviceId = 1; deviceId <= DEVICE_COUNT; deviceId++) {
			String runnerBefore = before.getRunnerId(deviceId);
			if (!"runner3".equals(runnerBefore)) {
				Assertions.assertEquals(runnerBefore, after.getRunnerId(deviceId),
					"Device %d moved although its runner is still there".formatted(deviceId));
			}
		}
	}

	@Test
	@DisplayName("Tasks without hash are spread randomly according to the weights")
	void noHashIsWeightedRandom() {
		RunnerSet set = runnerSet(80, "runner2", 20, "runner3");
		Map<String, Integer> counts = new HashMap<>();
		for (int i = 0; i < DEVICE_COUNT; i++) {
			counts.merge(set.getRunnerId(0), 1, Integer::sum);
		}
		double ratio = counts.getOrDefault("runner2", 0) / (double) DEVICE_COUNT;
		Assertions.assertEquals(0.8, ratio, 0.03, "runner2 should get about 80% of the tasks");
	}

	@Test
	@DisplayName("A single runner gets everything")
	void singleRunner() {
		RunnerSet set = runnerSet(100, "runner1");
		for (long deviceId = 0; deviceId <= 100; deviceId++) {
			Assertions.assertEquals("runner1", set.getRunnerId(deviceId));
		}
	}
}
