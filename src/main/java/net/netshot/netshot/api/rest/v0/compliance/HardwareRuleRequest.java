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


import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * The request to add or edit a hardware compliance rule. A property absent from the request
 * has its default value: no group ({@code -1}), empty strings, no regular expression and no date.
 *
 * @param id not used (the rule is given by the path when editing)
 * @param group the ID of the group the rule applies to, {@code -1} for all the devices
 * @param driver the name of the device driver the rule applies to
 * @param partNumber the part number (or the regular expression it has to match)
 * @param partNumberRegExp whether the part number is a regular expression
 * @param family the device family (or the regular expression it has to match)
 * @param familyRegExp whether the family is a regular expression
 * @param endOfSale the end of sale date of the matching hardware
 * @param endOfLife the end of life date of the matching hardware
 */
// Transitional, see NewDeviceRequest
@XmlRootElement(name = "rsHardwareRule")
public record HardwareRuleRequest(
	long id,
	Long group,
	String driver,
	String partNumber,
	boolean partNumberRegExp,
	String family,
	boolean familyRegExp,
	Date endOfSale,
	Date endOfLife) {

	/**
	 * Applies the default values.
	 */
	public HardwareRuleRequest {
		if (group == null) {
			group = -1L;
		}
		if (driver == null) {
			driver = "";
		}
		if (partNumber == null) {
			partNumber = "";
		}
		if (family == null) {
			family = "";
		}
	}
}
