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

import java.util.List;

import net.netshot.netshot.task.Task;

/**
 * A task working on the devices of a group, or on a list of devices.
 */
public interface GroupBasedTask {

	/**
	 * Gets the ID of the target group.
	 *
	 * @return the group ID, null for none
	 */
	Long deviceGroupId();

	/**
	 * Gets the IDs of the target devices, in order, when there is no group.
	 *
	 * @return the device IDs (empty for none)
	 */
	List<Long> deviceIds();

	/**
	 * Builds a new task working on a group or a list of devices.
	 *
	 * @param <S> the type of specification
	 * @param spec the specification
	 * @param groupName the name of the target group (null for a device list)
	 * @param comments the comments
	 * @param author the author
	 * @return the new task
	 */
	static <S extends TaskSpec & GroupBasedTask> Task of(S spec, String groupName, String comments, String author) {
		return Task.of(spec, comments, spec.deviceGroupId() == null ? targetOf(spec.deviceIds()) : groupName, author);
	}

	/**
	 * Gets the description of a device list as a task target.
	 *
	 * @param deviceIds the device IDs
	 * @return the target description
	 */
	static String targetOf(List<Long> deviceIds) {
		return String.format("%d device(s)", deviceIds.size());
	}

	/**
	 * Gets the identity group of a group task, from a prefix.
	 *
	 * @param prefix the prefix
	 * @param deviceGroupId the group ID (or null)
	 * @return the identity group
	 */
	static String identityGroup(String prefix, Long deviceGroupId) {
		return String.format("%s_%d", prefix, deviceGroupId == null ? 0 : deviceGroupId);
	}
}
