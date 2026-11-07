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
package net.netshot.netshot.task;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The devices with an automatic snapshot queued: an automatic snapshot of a device isn't queued again
 * while one is. Shared by the scheduler of the automatic snapshots, the task manager (scheduling and
 * cancellation of the tasks) and the snapshot handler (end of the snapshot).
 */
public final class AutoSnapshotQueue {

	/** The IDs of the devices with a queued automatic snapshot. */
	private final Set<Long> deviceIds = ConcurrentHashMap.newKeySet();

	/**
	 * Marks an automatic snapshot of a device as queued.
	 *
	 * @param deviceId the ID of the device
	 * @return true if none was queued for the device (it is now)
	 */
	public boolean markQueued(long deviceId) {
		return this.deviceIds.add(deviceId);
	}

	/**
	 * Clears the queued automatic snapshot of a device.
	 *
	 * @param deviceId the ID of the device
	 */
	public void clear(long deviceId) {
		this.deviceIds.remove(deviceId);
	}

	/**
	 * Gets the number of devices with a queued automatic snapshot.
	 *
	 * @return the number of devices
	 */
	public int size() {
		return this.deviceIds.size();
	}
}
