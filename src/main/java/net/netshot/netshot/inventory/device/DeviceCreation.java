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
package net.netshot.netshot.inventory.device;

import java.util.List;
import java.util.Map;


/**
 * The command to add a device: either immediately (the type of the device is known) or
 * through an auto discovery task.
 *
 * @param autoDiscover whether to discover the type of the device (the device is then
 *        created if the discovery succeeds) instead of creating it immediately
 * @param ipAddress the management address (IP address or host name)
 * @param domainId the ID of the management domain
 * @param deviceType the name of the device driver (not used when auto discovering)
 * @param accesses the per-access settings, not yet validated (may be null); transient
 *        {@link DeviceAccess} objects without specific credential set, which the service
 *        validates and completes
 * @param options the values of the driver options (may be null)
 * @param comments the comments (may be null)
 * @param tags the tags (may be null)
 * @param author the name of the user adding the device
 * @param specificCredentials the specific credential sets of the accesses (may be null)
 */
public record DeviceCreation(
	boolean autoDiscover,
	String ipAddress,
	long domainId,
	String deviceType,
	List<DeviceAccess> accesses,
	Map<String, Object> options,
	String comments,
	List<String> tags,
	String author,
	List<AccessSpecificCredential> specificCredentials) {

	/**
	 * Builds a command without specific credential sets.
	 *
	 * @param autoDiscover whether to discover the type of the device
	 * @param ipAddress the management address
	 * @param domainId the ID of the management domain
	 * @param deviceType the name of the device driver
	 * @param accesses the per-access settings
	 * @param options the values of the driver options
	 * @param comments the comments
	 * @param tags the tags
	 * @param author the name of the user adding the device
	 */
	public DeviceCreation(boolean autoDiscover, String ipAddress, long domainId, String deviceType, List<DeviceAccess> accesses,
		Map<String, Object> options, String comments, List<String> tags, String author) {
		this(autoDiscover, ipAddress, domainId, deviceType, accesses, options, comments, tags, author, List.of());
	}
}
