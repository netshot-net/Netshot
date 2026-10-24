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
package net.netshot.netshot.task.type;

import net.netshot.netshot.task.Task;

/**
 * What a task does, depending on its type: one record per type of task, named after the type
 * (the name is stored as the type of the task). The other aggregates (devices, groups...) are
 * referenced by ID.
 */
public sealed interface TaskSpec permits TakeSnapshotTask, RunDiagnosticsTask, CheckComplianceTask, RunDeviceScriptTask,
	DiscoverDeviceTypeTask, ScanSubnetsTask, PurgeDatabaseTask, TakeGroupSnapshotTask, CheckGroupComplianceTask,
	CheckGroupSoftwareTask, RunGroupDiagnosticsTask, RunDeviceGroupScriptTask {

	/**
	 * Gets the type name (as stored), e.g. TakeSnapshotTask.
	 *
	 * @return the type name
	 */
	default String typeName() {
		return this.getClass().getSimpleName();
	}

	/**
	 * Gets the description of the type of task.
	 *
	 * @return the description
	 */
	String description();

	/**
	 * Gets the group of the identity of the task in the schedulers.
	 *
	 * @return the group
	 */
	String identityGroup();

	/**
	 * Gets the hash used to choose the runner of the task.
	 *
	 * @return the hash
	 */
	default long runnerHash() {
		return 0;
	}

	/**
	 * Gets how the task gets exclusive access to its target device.
	 *
	 * @return the lock policy
	 */
	default Task.DeviceLockPolicy deviceLockPolicy() {
		return Task.DeviceLockPolicy.NONE;
	}

	/**
	 * Gets the lock namespace of the type of task, for {@link Task.DeviceLockPolicy#CANCEL_IF_SAME_TYPE}.
	 *
	 * @return the lock namespace
	 */
	default int deviceLockTypeNamespace() {
		return 0;
	}
}
