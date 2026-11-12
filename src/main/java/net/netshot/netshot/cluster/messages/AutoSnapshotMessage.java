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
package net.netshot.netshot.cluster.messages;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.cluster.ClusterMember;

/**
 * Cluster message requesting to take a snapshot of specific devices.
 * Let the master deduplicate the requests when multiple cluster members
 * receive the trap triggering the snapshot.
 */
@XmlRootElement
public class AutoSnapshotMessage extends ClusterMessage {

	/** Device IDs. */
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	Set<Long> deviceIds = new HashSet<>();

	/**
	 * Constructor.
	 * @param memberInfo = info about the cluster member
	 */
	public AutoSnapshotMessage(ClusterMember memberInfo) {
		super(memberInfo.getInstanceId());
	}

	/**
	 * Hidden constructor.
	 */
	protected AutoSnapshotMessage() {
	}

	public void addDeviceIds(Collection<Long> newDeviceIds) {
		this.deviceIds.addAll(newDeviceIds);
	}

	@Override
	public List<ClusterMessage> split() {
		return this.split(this.deviceIds, ids -> {
			AutoSnapshotMessage message = new AutoSnapshotMessage();
			message.addDeviceIds(ids);
			return message;
		});
	}
}
