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
package net.netshot.netshot.compliance;

import java.time.Instant;

import java.util.Set;
import java.util.regex.PatternSyntaxException;

import lombok.Builder;
import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Module;

/**
 * A software compliance rule: the devices matching it (driver, family, version, part number of a
 * module) get its conformance level. The rules are checked by priority, the first match wins.
 *
 * @param id the ID (0 for a rule not stored yet)
 * @param version the version, incremented on each change
 * @param changeDate the date of the last change
 * @param priority the priority (lowest first)
 * @param targetGroupId the ID of the group the rule applies to (null for all the devices)
 * @param driver the name of the driver of the devices (null for any)
 * @param family the family of the devices (empty for any)
 * @param familyRegExp true if the family is a regular expression
 * @param softwareVersion the software version of the devices (empty for any)
 * @param softwareVersionRegExp true if the software version is a regular expression
 * @param partNumber the part number of a module of the devices (null or empty for any)
 * @param partNumberRegExp true if the part number is a regular expression
 * @param level the conformance level of the matching devices
 */
@Builder(toBuilder = true)
public record SoftwareRule(long id, int version, Instant changeDate, double priority, Long targetGroupId, String driver, String family, boolean familyRegExp,
	String softwareVersion, boolean softwareVersionRegExp, String partNumber, boolean partNumberRegExp, ConformanceLevel level) {

	/**
	 * Gets a copy of the rule with another ID.
	 *
	 * @param newId the ID
	 * @return the rule
	 */
	public SoftwareRule withId(long newId) {
		return this.toBuilder().id(newId).build();
	}

	/**
	 * Gets a copy of the rule with another priority.
	 *
	 * @param newPriority the priority
	 * @return the rule
	 */
	public SoftwareRule withPriority(double newPriority) {
		return this.toBuilder().priority(newPriority).build();
	}

	/**
	 * Checks a device: gives the level of the rule if it matches the device,
	 * {@link ConformanceLevel#UNKNOWN} otherwise.
	 *
	 * @param device the device
	 * @param deviceGroupIds the IDs of the groups of the device
	 * @return the software level of the device according to this rule
	 */
	public ConformanceLevel check(Device device, Set<Long> deviceGroupIds) {
		if (targetGroupId != null && !deviceGroupIds.contains(targetGroupId)) {
			return ConformanceLevel.UNKNOWN;
		}
		if (driver != null && !driver.equals(device.driver())) {
			return ConformanceLevel.UNKNOWN;
		}
		if (familyRegExp) {
			try {
				if (!device.family().matches(family)) {
					return ConformanceLevel.UNKNOWN;
				}
			}
			catch (PatternSyntaxException e) {
				return ConformanceLevel.UNKNOWN;
			}
		}
		else {
			if (!"".equals(family) && !device.family().equals(family)) {
				return ConformanceLevel.UNKNOWN;
			}
		}
		if (softwareVersionRegExp) {
			try {
				if (!device.softwareVersion().matches(softwareVersion)) {
					return ConformanceLevel.UNKNOWN;
				}
			}
			catch (PatternSyntaxException e) {
				return ConformanceLevel.UNKNOWN;
			}
		}
		else {
			if (!"".equals(softwareVersion) && !device.softwareVersion().equals(softwareVersion)) {
				return ConformanceLevel.UNKNOWN;
			}
		}
		if (partNumber != null && !"".equals(partNumber)) {
			boolean moduleMatches = false;
			for (Module module : device.modules()) {
				if (partNumberRegExp) {
					try {
						if (!module.partNumber().matches(partNumber)) {
							continue;
						}
					}
					catch (PatternSyntaxException e) {
						return ConformanceLevel.UNKNOWN;
					}
				}
				else {
					if (!"".equals(partNumber) && !module.partNumber().equals(partNumber)) {
						continue;
					}
				}
				moduleMatches = true;
				break;
			}
			if (!moduleMatches) {
				return ConformanceLevel.UNKNOWN;
			}
		}
		return this.level;
	}

	@Override
	public String toString() {
		return "Software compliance rule " + id;
	}
}
