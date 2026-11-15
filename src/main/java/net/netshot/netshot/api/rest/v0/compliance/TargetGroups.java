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
package net.netshot.netshot.api.rest.v0.compliance;

import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * What the v0 representation of the policies and of the software and hardware rules needs: their target groups, and the
 * leniency of v0 about the driver of a rule.
 */
final class TargetGroups {

	private TargetGroups() {
	}

	/**
	 * Gets the target group of a rule, as written in its v0 representation.
	 *
	 * @param groupService the group use cases
	 * @param groupId the ID of the group, or null
	 * @return the group, or null for no group (or a group deleted meanwhile)
	 */
	static DeviceGroup find(GroupService groupService, Long groupId) {
		if (groupId == null) {
			return null;
		}
		try {
			return groupService.get(groupId);
		}
		catch (NetshotException e) {
			if (e.getErrorCode() == ErrorCode.NETSHOT_GROUP_NOT_FOUND) {
				return null;
			}
			throw e;
		}
	}

	/**
	 * Gives the driver of a rule as v0 always stored it: an unknown driver is taken for no driver (any driver).
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param driver the posted driver
	 * @return the driver, or null if it is unknown
	 */
	static String knownDriver(DriverRegistry driverRegistry, String driver) {
		return driverRegistry.getDriverByName(driver) == null ? null : driver;
	}
}
