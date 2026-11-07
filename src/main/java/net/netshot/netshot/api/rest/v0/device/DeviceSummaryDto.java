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
package net.netshot.netshot.api.rest.v0.device;


import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.device.Device;

/**
 * The summary representation of a device, as listed by the device list.
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
public record DeviceSummaryDto(
	long id,
	String name,
	String family,
	String mgmtAddress,
	Device.Status status,
	String driver,
	Boolean eol,
	Boolean eos,
	Boolean configCompliant,
	ConformanceLevel softwareLevel,
	Device.NetworkClass networkClass) {
}
