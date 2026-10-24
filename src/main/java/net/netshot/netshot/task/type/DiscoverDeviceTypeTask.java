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
package net.netshot.netshot.task.type;

import java.util.List;
import java.util.Set;

import lombok.Builder;
import net.netshot.netshot.task.Task;

/**
 * Discovers the type of a device (via SNMP), then adds the device and takes a first snapshot.
 *
 * @param deviceAddress the address of the device
 * @param domainId the ID of the domain of the new device
 * @param credentialSetIds the IDs of the SNMP credential sets to try
 * @param deviceComments the comments of the new device (or null)
 * @param deviceTags the tags of the new device
 * @param discoveredDeviceType the name of the discovered driver (result, or null)
 * @param snapshotTaskId the ID of the first snapshot task (result, or 0)
 * @param discoverResultDeviceId the ID of the new device (result, or null)
 */
@Builder(toBuilder = true)
public record DiscoverDeviceTypeTask(String deviceAddress, Long domainId, Set<Long> credentialSetIds, String deviceComments,
	List<String> deviceTags, String discoveredDeviceType, long snapshotTaskId, Long discoverResultDeviceId)
	implements TaskSpec, DomainBasedTask {

	/** Name of the result attribute giving the discovered device type. */
	public static final String DISCOVERED_DEVICE_TYPE = "discoveredDeviceType";

	/** Name of the result attribute giving the ID of the first snapshot task. */
	public static final String SNAPSHOT_TASK_ID = "snapshotTaskId";

	/**
	 * Canonical constructor: the null collections are replaced by empty ones.
	 */
	public DiscoverDeviceTypeTask {
		credentialSetIds = credentialSetIds == null ? Set.of() : Set.copyOf(credentialSetIds);
		deviceTags = deviceTags == null ? List.of() : List.copyOf(deviceTags);
	}

	/**
	 * Builds a new discovery task.
	 *
	 * @param deviceAddress the address of the device
	 * @param domainId the ID of the domain
	 * @param credentialSetIds the IDs of the SNMP credential sets to try
	 * @param comments the comments
	 * @param author the author
	 * @return the new task
	 */
	public static Task of(String deviceAddress, Long domainId, Set<Long> credentialSetIds, String comments, String author) {
		return Task.of(builder().deviceAddress(deviceAddress).domainId(domainId).credentialSetIds(credentialSetIds).build(),
			comments, deviceAddress, author);
	}

	@Override
	public String description() {
		return "Device autodiscovery";
	}

	@Override
	public String identityGroup() {
		return String.format("DiscoverDeviceType_%s", this.deviceAddress);
	}
}
