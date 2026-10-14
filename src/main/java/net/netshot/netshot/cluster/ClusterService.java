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
package net.netshot.netshot.cluster;

import java.util.List;
import java.util.function.Supplier;

import net.netshot.netshot.cluster.ClusterMember.MastershipStatus;

/**
 * The read-only use cases on the high availability cluster: its members, and the mastership
 * status of the local node.
 */
public class ClusterService {

	/**
	 * The mastership status of the local node.
	 *
	 * @param clusterEnabled whether clustering is enabled
	 * @param master whether the local node is the master (true if clustering is not enabled)
	 * @param currentMasterId the instance ID of the current master, if any
	 */
	public record MasterStatus(boolean clusterEnabled, boolean master, String currentMasterId) {
	}

	private final Supplier<List<ClusterMember>> membersSource;

	/**
	 * Instantiates the service.
	 *
	 * @param membersSource gives a snapshot of the cluster members (empty if clustering is not
	 *        enabled); the composition root provides the cluster manager
	 */
	public ClusterService(Supplier<List<ClusterMember>> membersSource) {
		this.membersSource = membersSource;
	}

	/**
	 * Gets the cluster members.
	 *
	 * @return a snapshot of the members (empty list if clustering is not enabled)
	 */
	public List<ClusterMember> members() {
		return this.membersSource.get();
	}

	/**
	 * Gets the mastership status of the local node.
	 *
	 * @return the status
	 */
	public MasterStatus masterStatus() {
		List<ClusterMember> members = this.membersSource.get();
		boolean clusterEnabled = false;
		boolean master = true;
		String currentMasterId = null;
		if (members.size() > 0) {
			clusterEnabled = true;
			master = false;
			for (ClusterMember member : members) {
				if (MastershipStatus.MASTER.equals(member.getStatus())) {
					currentMasterId = member.getInstanceId();
					if (member.isLocal()) {
						master = true;
					}
				}
			}
		}
		return new MasterStatus(clusterEnabled, master, currentMasterId);
	}
}
