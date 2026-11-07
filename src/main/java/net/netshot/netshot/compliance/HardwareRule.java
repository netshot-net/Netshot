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
package net.netshot.netshot.compliance;

import java.time.Instant;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import lombok.Builder;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Module;

/**
 * A hardware compliance rule: the end-of-sale and end-of-life dates of the modules matching it
 * (driver and family of the device, part number of the module).
 *
 * @param id the ID (0 for a rule not stored yet)
 * @param version the version, incremented on each change
 * @param changeDate the date of the last change
 * @param driver the name of the driver of the devices (null for any)
 * @param targetGroupId the ID of the group the rule applies to (null for all the devices)
 * @param family the family of the devices (empty for any)
 * @param familyRegExp true if the family is a regular expression
 * @param partNumber the part number of the modules
 * @param partNumberRegExp true if the part number is a regular expression
 * @param endOfSale the end-of-sale date of the matching modules
 * @param endOfLife the end-of-life date of the matching modules
 */
@Builder(toBuilder = true)
public record HardwareRule(long id, int version, Instant changeDate, String driver, Long targetGroupId, String family, boolean familyRegExp, String partNumber,
	boolean partNumberRegExp, Instant endOfSale, Instant endOfLife) {

	/**
	 * Gets a copy of the rule with another ID.
	 *
	 * @param newId the ID
	 * @return the rule
	 */
	public HardwareRule withId(long newId) {
		return this.toBuilder().id(newId).build();
	}

	/**
	 * Checks a device: gives its end-of-sale and end-of-life dates (and modules) the ones of
	 * the rule, if the rule matches and the dates are earlier.
	 *
	 * @param device the device
	 * @param deviceGroupIds the IDs of the groups of the device
	 * @return the device, with its end-of-sale and end-of-life dates
	 */
	public Device check(Device device, Set<Long> deviceGroupIds) {
		if (targetGroupId != null && !deviceGroupIds.contains(targetGroupId)) {
			return device;
		}
		if (driver != null && !driver.equals(device.driver())) {
			return device;
		}
		if (familyRegExp) {
			try {
				if (!device.family().matches(family)) {
					return device;
				}
			}
			catch (PatternSyntaxException e) {
				return device;
			}
		}
		else {
			if (!"".equals(family) && !device.family().equals(family)) {
				return device;
			}
		}
		Device checked = device;
		for (Module module : device.modules()) {
			if (partNumberRegExp) {
				try {
					if (!module.partNumber().matches(partNumber)) {
						continue;
					}
				}
				catch (PatternSyntaxException e) {
					return checked;
				}
			}
			else {
				if (!"".equals(partNumber) && !module.partNumber().equals(partNumber)) {
					continue;
				}
			}
			if (endOfLife != null && (checked.eolDate() == null || endOfLife.isBefore(checked.eolDate()))) {
				checked = checked.toBuilder().eolDate(endOfLife).eolModule(module).build();
			}
			if (endOfSale != null && (checked.eosDate() == null || endOfSale.isBefore(checked.eosDate()))) {
				checked = checked.toBuilder().eosDate(endOfSale).eosModule(module).build();
			}
		}
		return checked;
	}

	@Override
	public String toString() {
		return "Hardware compliance rule " + id;
	}
}
