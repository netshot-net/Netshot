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
package net.netshot.netshot.api.rest.v1.device;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;
import net.netshot.netshot.api.rest.v1.Ref;
import net.netshot.netshot.api.rest.v1.domain.DomainDto;
import net.netshot.netshot.api.rest.v1.group.GroupDto;
import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.device.Device;

/**
 * A device. The interfaces, modules, configurations, tasks and results of a device are sub-resources
 * ({@code /devices/{id}/interfaces}...).
 *
 * @param id the ID
 * @param url the path of the device
 * @param display the display name
 * @param version the version, incremented on each change (the ETag)
 * @param changeDate the date of the last change
 * @param createdDate the creation date
 * @param creator the user who added the device
 * @param name the name
 * @param mgmtAddress the management address (IP address or host name)
 * @param status the status
 * @param mgmtDomain the management domain
 * @param family the family (model range)
 * @param driver the name of the driver
 * @param driverDescription the description of the driver
 * @param networkClass the network class
 * @param softwareVersion the software version
 * @param softwareLevel the software compliance level
 * @param serialNumber the serial number
 * @param location the location
 * @param contact the contact
 * @param comments the comments
 * @param cachedIpAddress the IP address the management address last resolved to
 * @param compliant whether the device has no non-conforming configuration compliance result
 * @param endOfLife whether the device is past its end of life date
 * @param endOfSale whether the device is past its end of sale date
 * @param eolDate the end of life date
 * @param eolModule the module responsible for the end of life
 * @param eosDate the end of sale date
 * @param eosModule the module responsible for the end of sale
 * @param lastSnapshotSuccessDate when the last snapshot succeeded (null if never)
 * @param lastSnapshotFailureDate when the last snapshot failed (null if never)
 * @param tags the tags
 * @param virtualDevices the names of the virtual devices
 * @param vrfInstances the names of the VRF instances
 * @param options the values of the driver options
 * @param attributes the attributes collected by the driver
 * @param accesses the per-access settings
 * @param ownerGroups the groups the device belongs to
 */
@Schema(name = "Device", description = "Device (network or security equipment)")
public record DeviceDto(
	long id,
	String url,
	String display,
	Long version,
	Instant changeDate,
	Instant createdDate,
	String creator,
	@Brief String name,
	@Brief String mgmtAddress,
	@Brief Device.Status status,
	@Ref DomainDto mgmtDomain,
	@Brief String family,
	@Brief String driver,
	@Brief String driverDescription,
	@Brief Device.NetworkClass networkClass,
	String softwareVersion,
	@Brief ConformanceLevel softwareLevel,
	String serialNumber,
	String location,
	String contact,
	String comments,
	String cachedIpAddress,
	@Brief Boolean compliant,
	@Brief Boolean endOfLife,
	@Brief Boolean endOfSale,
	Instant eolDate,
	ModuleDto eolModule,
	Instant eosDate,
	ModuleDto eosModule,
	Instant lastSnapshotSuccessDate,
	Instant lastSnapshotFailureDate,
	Set<String> tags,
	Set<String> virtualDevices,
	Set<String> vrfInstances,
	Map<String, Object> options,
	List<DeviceAttributeDto> attributes,
	List<DeviceAccessDto> accesses,
	@Ref List<GroupDto> ownerGroups) {

	/** The properties a device summary (the light list query) can fill. */
	public static final Set<String> SUMMARY_PROPERTIES = Set.of("id", "url", "display", "name", "mgmtAddress", "status",
		"family", "driver", "driverDescription", "networkClass", "softwareLevel", "compliant", "endOfLife", "endOfSale");
}
