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

import java.util.List;
import java.util.Map;

/**
 * The request to edit a device. A property absent from the request (or null) leaves the
 * corresponding attribute of the device unchanged.
 *
 * @param id not used (the device is given by the path)
 * @param enabled whether the device is enabled (in production) or disabled
 * @param comments the new comments
 * @param ipAddress the new management address
 * @param mgmtDomain the ID of the new management domain
 * @param accesses the new full set of per-access settings
 * @param options the new values of the driver options
 * @param tags the new tags (replacing all the existing ones)
 */
public record UpdateDeviceRequest(
	long id,
	Boolean enabled,
	String comments,
	String ipAddress,
	Long mgmtDomain,
	List<DeviceAccessDto> accesses,
	Map<String, Object> options,
	List<String> tags) {
}
