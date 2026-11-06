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

import net.netshot.netshot.inventory.compliance.ConformanceLevel;

/**
 * The properties of a software rule to create, or to replace on an existing rule.
 *
 * @param groupId the ID of the device group the rule applies to, or null for all the devices.
 *        A group which doesn't exist is silently taken for "all the devices".
 * @param driver the name of the device driver the rule applies to (an unknown driver is taken for "all the drivers")
 * @param family the device family (or the regular expression it has to match)
 * @param familyRegExp whether the family is a regular expression
 * @param softwareVersion the software version (or the regular expression it has to match)
 * @param softwareVersionRegExp whether the software version is a regular expression
 * @param partNumber the part number (or the regular expression it has to match)
 * @param partNumberRegExp whether the part number is a regular expression
 * @param level the conformance level granted to the devices matching the rule
 */
public record SoftwareRuleDefinition(
	Long groupId,
	String driver,
	String family,
	boolean familyRegExp,
	String softwareVersion,
	boolean softwareVersionRegExp,
	String partNumber,
	boolean partNumberRegExp,
	ConformanceLevel level) {
}
