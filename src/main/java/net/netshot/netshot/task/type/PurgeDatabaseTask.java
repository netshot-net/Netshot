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
package net.netshot.netshot.task.type;

import java.util.List;

import lombok.Builder;

/**
 * Purges the old data from the database (tasks, configurations, modules, orphan files), for all the
 * devices, the devices of a group, or a list of devices.
 *
 * @param deviceGroupId the ID of the target group (null for none)
 * @param deviceIds the IDs of the target devices (empty for none)
 * @param days the age (in days) of the tasks to purge (0 not to purge them)
 * @param configDays the age (in days) of the configurations to purge (-1 not to purge them)
 * @param configSize the minimum size (in kB) of the configurations to purge (0 for any)
 * @param configKeepDays the interval (in days) between two configurations to keep (0 for none)
 * @param moduleDays the age (in days) of the removed modules to purge (-1 not to purge them)
 * @param removeOrphanFiles true to remove the orphan binary files
 */
@Builder(builderMethodName = "defaults", toBuilder = true)
public record PurgeDatabaseTask(Long deviceGroupId, List<Long> deviceIds, int days, int configDays, int configSize,
	int configKeepDays, int moduleDays, boolean removeOrphanFiles) implements TaskSpec, GroupBasedTask {

	/**
	 * Canonical constructor: a null list of devices is replaced by an empty one.
	 */
	public PurgeDatabaseTask {
		deviceIds = deviceIds == null ? List.of() : List.copyOf(deviceIds);
	}

	/**
	 * Starts building a purge task specification, with the defaults (nothing purged).
	 *
	 * @return the builder
	 */
	public static PurgeDatabaseTaskBuilder builder() {
		return defaults().configDays(-1).moduleDays(-1);
	}

	@Override
	public String description() {
		return "Database purge";
	}

	@Override
	public String identityGroup() {
		return "PurgeDatabase";
	}
}
