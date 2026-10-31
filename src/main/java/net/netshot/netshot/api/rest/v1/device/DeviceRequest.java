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
import net.netshot.netshot.inventory.device.Device;

/**
 * The editable properties of a device. The other properties of the device (family, serial number, interfaces...)
 * are read only: they are ignored when sent back.
 *
 * @param mgmtAddress the management address (IP address or host name)
 * @param mgmtDomain the management domain
 * @param status the status ({@code INPRODUCTION} by default)
 * @param comments the comments
 * @param tags the tags
 * @param options the values of the options of the driver
 * @param accesses the settings of the accesses (an access which is not listed gets its default settings)
 */
@Schema(name = "DeviceRequest", description = "Editable properties of a device")
@ReadOnlyFrom(DeviceDto.class)
public record DeviceRequest(
	@JsonProperty(required = true) String mgmtAddress,
	@JsonProperty(required = true) RefId mgmtDomain,
	Device.Status status,
	String comments,
	Set<String> tags,
	Map<String, Object> options,
	List<DeviceAccessRequest> accesses) {
}
