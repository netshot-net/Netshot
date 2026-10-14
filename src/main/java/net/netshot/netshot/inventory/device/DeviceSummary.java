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
package net.netshot.netshot.inventory.device;

import net.netshot.netshot.compliance.SoftwareRule;

/**
 * The few attributes of a device needed to list devices, projected by
 * {@link DeviceRepository#findSummaries} without loading the device entities.
 *
 * @param id the device ID
 * @param name the name
 * @param family the family
 * @param mgmtAddress the management address
 * @param status the status
 * @param driver the name of the device driver
 * @param eol whether the device is past its end of life date
 * @param eos whether the device is past its end of sale date
 * @param configCompliant whether the device has no non-conforming configuration compliance result
 * @param softwareLevel the software conformance level
 * @param networkClass the network class
 */
public record DeviceSummary(
	long id,
	String name,
	String family,
	String mgmtAddress,
	Device.Status status,
	String driver,
	Boolean eol,
	Boolean eos,
	Boolean configCompliant,
	SoftwareRule.ConformanceLevel softwareLevel,
	Device.NetworkClass networkClass) {
}
