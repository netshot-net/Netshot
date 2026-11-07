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

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.api.rest.v1.RefId;

/**
 * The device to add.
 *
 * @param mgmtAddress the management address (IP address or host name)
 * @param mgmtDomain the management domain
 * @param driver the name of the device driver; if absent, the type of the device is discovered (an SNMP community is
 *        needed), and the device is only created if the discovery succeeds
 * @param comments the comments
 * @param tags the tags
 * @param options the values of the options of the driver (with a driver only)
 * @param accesses the settings of the accesses (with a driver only)
 */
@Schema(name = "DeviceCreationRequest", description = "Device to add")
@ReadOnlyFrom(DeviceDto.class)
public record DeviceCreationRequest(
	@JsonProperty(required = true) String mgmtAddress,
	@JsonProperty(required = true) RefId mgmtDomain,
	@Schema(description = "Name of the device driver; if absent, the device type is auto discovered") String driver,
	String comments,
	Set<String> tags,
	Map<String, Object> options,
	List<DeviceAccessRequest> accesses) {
}
