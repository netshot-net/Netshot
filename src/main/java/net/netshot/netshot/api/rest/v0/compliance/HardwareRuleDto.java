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
package net.netshot.netshot.api.rest.v0.compliance;

import java.util.Date;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.api.rest.v0.group.DeviceGroupDto;

/**
 * A hardware compliance rule.
 *
 * @param deviceType the description of the device driver
 * @param driver the name of the device driver the rule applies to, or null for all
 * @param endOfLife the end of life date of the matching hardware, or null
 * @param endOfSale the end of sale date of the matching hardware, or null
 * @param family the device family (or the regular expression it has to match)
 * @param familyRegExp whether the family is a regular expression
 * @param id the rule ID
 * @param partNumber the part number (or the regular expression it has to match)
 * @param partNumberRegExp whether the part number is a regular expression
 * @param targetGroup the group the rule applies to, or null for all the devices
 */
@JacksonXmlRootElement(localName = "HardwareRule")
public record HardwareRuleDto(
	String deviceType,
	String driver,
	Date endOfLife,
	Date endOfSale,
	String family,
	boolean familyRegExp,
	long id,
	String partNumber,
	boolean partNumberRegExp,
	DeviceGroupDto targetGroup) {
}
