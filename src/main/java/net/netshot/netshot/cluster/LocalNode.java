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

import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.task.AutoSnapshotScheduler;
import net.netshot.netshot.task.engine.TaskManager;
import net.netshot.netshot.task.engine.TaskManager.Mode;
import net.netshot.netshot.vault.VaultManager;

/**
 * What the cluster manager drives on the local node: its task engine, drivers, automatic snapshots
 * and Vault caches (replaced by a fake in the tests of the cluster manager).
 */
interface LocalNode {

	/**
	 * Sets the mode of the local task engine.
	 * @param mode the mode
	 */
	void setTaskMode(Mode mode);

	/**
	 * Gets the mode of the local task engine.
	 * @return the mode
	 */
	Mode getTaskMode();

	/**
	 * Sets the cluster members which run the tasks.
	 * @param runners the runners
	 */
	void setRunners(Collection<TaskManager.Runner> runners);

	/** Loads and schedules the tasks assigned to the local node. */
	void scheduleLocalTasks();

	/** Assigns and schedules the new tasks (on the master). */
	void scheduleNewTasks();

	/** Reschedules all the tasks (when the local node becomes the master). */
	void rescheduleAll();

	/** Reassigns the tasks of the members which left the cluster (on the master). */
	void reassignOrphanTasks();

	/** Checks the database connection of the task locks. */
	void checkTaskLocks();

	/**
	 * Gets the hash of all the loaded drivers.
	 * @return the hash
	 */
	String getDriverHash();

	/**
	 * Reloads the drivers.
	 * @throws Exception on error
	 */
	void reloadDrivers() throws Exception;

	/**
	 * Schedules an automatic snapshot of a device, if needed.
	 * @param deviceId the ID of the device
	 */
	void scheduleAutoSnapshot(long deviceId);

	/**
	 * Invalidates the local cache of a Vault instance.
	 * @param vaultInstanceId the ID of the Vault instance
	 */
	void invalidateVault(long vaultInstanceId);

	/**
	 * Builds the local node from the server components.
	 * @param driverRegistry the loaded device drivers
	 * @param autoSnapshotScheduler the scheduler of the automatic snapshots
	 * @param taskManager the task manager
	 * @param vaultManager the Vault client
	 * @return the local node
	 */
	static LocalNode of(DriverRegistry driverRegistry, AutoSnapshotScheduler autoSnapshotScheduler,
		TaskManager taskManager, VaultManager vaultManager) {
		return new LocalNode() {
			@Override
			public void setTaskMode(Mode mode) {
				taskManager.setMode(mode);
			}

			@Override
			public Mode getTaskMode() {
				return taskManager.getMode();
			}

			@Override
			public void setRunners(Collection<TaskManager.Runner> runners) {
				taskManager.setRunners(runners);
			}

			@Override
			public void scheduleLocalTasks() {
				taskManager.scheduleLocalTasks();
			}

			@Override
			public void scheduleNewTasks() {
				taskManager.scheduleNewTasks();
			}

			@Override
			public void rescheduleAll() {
				taskManager.rescheduleAll();
			}

			@Override
			public void reassignOrphanTasks() {
				taskManager.reassignOrphanTasks();
			}

			@Override
			public void checkTaskLocks() {
				taskManager.lockManager().check();
			}

			@Override
			public String getDriverHash() {
				return driverRegistry.getAllDriverHash();
			}

			@Override
			public void reloadDrivers() throws Exception {
				driverRegistry.refresh();
			}

			@Override
			public void scheduleAutoSnapshot(long deviceId) {
				autoSnapshotScheduler.scheduleSnapshotIfNeeded(deviceId);
			}

			@Override
			public void invalidateVault(long vaultInstanceId) {
				vaultManager.invalidate(vaultInstanceId);
			}
		};
	}
}
