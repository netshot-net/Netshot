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

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The command to replace the editable properties of a device. Every property is replaced by the value
 * of the command (a null collection being an empty one), except the secrets of the specific credential
 * sets, which are kept when their definitions leave them out.
 *
 * @param status the status (null for in production)
 * @param comments the comments (null for none)
 * @param mgmtAddress the management address (IP address or host name)
 * @param mgmtDomainId the ID of the management domain
 * @param accesses the per-access settings, not yet validated: transient {@link DeviceAccess} objects without specific
 *        credential set (an access which is not listed is removed)
 * @param specificCredentials the specific credential sets of the accesses (an access which has none loses its
 *        current one)
 * @param options the values of the driver options (null for none)
 * @param tags the tags (null for none)
 */
public record DeviceChange(
	Device.Status status,
	String comments,
	String mgmtAddress,
	long mgmtDomainId,
	List<DeviceAccess> accesses,
	List<AccessSpecificCredential> specificCredentials,
	Map<String, Object> options,
	Collection<String> tags) {
}
