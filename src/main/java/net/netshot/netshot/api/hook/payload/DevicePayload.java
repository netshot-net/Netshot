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
package net.netshot.netshot.api.hook.payload;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.platform.rest.RestViews.HookView;

/**
 * A device, as nested in the webhook payloads of the tasks (the target of a device task, or the
 * device created by a discovery task). It is lighter than the REST representation: it has neither
 * the ID, the management domain, the attributes nor the driver options.
 *
 * @param accesses the per-access settings
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
 * @param location the location
 * @param mgmtAddress the management address
 * @param name the name
 * @param networkClass the network class
 * @param ownerGroups the groups the device belongs to
 * @param realDeviceType the description of the device driver
 * @param serialNumber the serial number
 * @param softwareLevel the software conformance level
 * @param softwareVersion the software version
 * @param status the status
 * @param tags the tags
 */
@JacksonXmlRootElement(localName = "Device")
@JsonView(HookView.class)
public record DevicePayload(
	List<DeviceAccessPayload> accesses,
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
	ModulePayload eolModule,
	Date eosDate,
	ModulePayload eosModule,
	String family,
	String location,
	String mgmtAddress,
	String name,
	Device.NetworkClass networkClass,
	List<DeviceGroupPayload> ownerGroups,
	String realDeviceType,
	String serialNumber,
	SoftwareRule.ConformanceLevel softwareLevel,
	String softwareVersion,
	Device.Status status,
	List<String> tags) {
}
