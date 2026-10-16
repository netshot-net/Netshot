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
package net.netshot.netshot.api.rest.v0.device;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.api.rest.v0.domain.DomainDto;
import net.netshot.netshot.api.rest.v0.group.DeviceGroupDto;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * The full representation of a device. In XML, the root element is {@code Device}.
 *
 * @param accesses the per-access settings
 * @param attributes the attributes collected by the device driver
 * @param cachedIpAddress the IP address the management address last resolved to
 * @param changeDate the last change date
 * @param comments the comments
 * @param compliant whether the device has no non-conforming configuration compliance result
 * @param contact the contact
 * @param createdDate the creation date
 * @param creator the user who added the device
 * @param driver the name of the device driver
 * @param endOfLife whether the device is past its end of life date
 * @param endOfSale whether the device is past its end of sale date
 * @param eolDate the end of life date
 * @param eolModule the module responsible for the end of life
 * @param eosDate the end of sale date
 * @param eosModule the module responsible for the end of sale
 * @param family the family
 * @param id the device ID
 * @param location the location
 * @param mgmtAddress the management address
 * @param mgmtDomain the management domain
 * @param name the name
 * @param networkClass the network class
 * @param options the values of the driver options
 * @param ownerGroups the groups the device belongs to
 * @param realDeviceType the description of the device driver
 * @param serialNumber the serial number
 * @param softwareLevel the software conformance level
 * @param softwareVersion the software version
 * @param status the status
 * @param tags the tags
 */
@JacksonXmlRootElement(localName = "Device")
@JsonView(DefaultView.class)
public record DeviceDto(
	List<DeviceAccessDto> accesses,
	List<DeviceAttributeDto> attributes,
	String cachedIpAddress,
	Date changeDate,
	String comments,
	boolean compliant,
	String contact,
	Date createdDate,
	String creator,
	String driver,
	boolean endOfLife,
	boolean endOfSale,
	Date eolDate,
	ModuleDto eolModule,
	Date eosDate,
	ModuleDto eosModule,
	String family,
	long id,
	String location,
	String mgmtAddress,
	DomainDto mgmtDomain,
	String name,
	Device.NetworkClass networkClass,
	Map<String, Object> options,
	List<DeviceGroupDto> ownerGroups,
	String realDeviceType,
	String serialNumber,
	SoftwareRule.ConformanceLevel softwareLevel,
	String softwareVersion,
	Device.Status status,
	List<String> tags) {
}
