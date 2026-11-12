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
package net.netshot.netshot.cluster;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.netshot.netshot.task.engine.TaskManager;
import net.netshot.netshot.task.engine.TaskManager.Mode;

/**
 * Local node of a cluster manager under test, which records what the cluster manager asks it.
 */
final class RecordingNode implements LocalNode {

	/** The mode of the task engine. */
	volatile Mode mode;

	/** The IDs of the last runners. */
	volatile List<String> runners = List.of();

	/** The number of calls of each action. */
	final AtomicInteger scheduleLocalTasks = new AtomicInteger();
	final AtomicInteger scheduleNewTasks = new AtomicInteger();
	final AtomicInteger rescheduleAll = new AtomicInteger();
	final AtomicInteger reassignOrphanTasks = new AtomicInteger();
	final AtomicInteger reloadDrivers = new AtomicInteger();

	/** The devices of the scheduled automatic snapshots. */
	final Set<Long> autoSnapshots = ConcurrentHashMap.newKeySet();

	/** The invalidated Vault instances. */
	final Set<Long> invalidatedVaults = ConcurrentHashMap.newKeySet();

	@Override
	public void setTaskMode(Mode newMode) {
		this.mode = newMode;
	}

	@Override
	public Mode getTaskMode() {
		return this.mode;
	}

	@Override
	public void setRunners(Collection<TaskManager.Runner> newRunners) {
		this.runners = newRunners.stream().map(TaskManager.Runner::instanceId).sorted().toList();
	}

	@Override
	public void scheduleLocalTasks() {
		this.scheduleLocalTasks.incrementAndGet();
	}

	@Override
	public void scheduleNewTasks() {
		this.scheduleNewTasks.incrementAndGet();
	}

	@Override
	public void rescheduleAll() {
		this.rescheduleAll.incrementAndGet();
	}

	@Override
	public void reassignOrphanTasks() {
		this.reassignOrphanTasks.incrementAndGet();
	}

	@Override
	public void checkTaskLocks() {
	}

	@Override
	public String getDriverHash() {
		return "test-driver-hash";
	}

	@Override
	public void reloadDrivers() {
		this.reloadDrivers.incrementAndGet();
	}

	@Override
	public void scheduleAutoSnapshot(long deviceId) {
		this.autoSnapshots.add(deviceId);
	}

	@Override
	public void invalidateVault(long vaultInstanceId) {
		this.invalidatedVaults.add(vaultInstanceId);
	}
}
