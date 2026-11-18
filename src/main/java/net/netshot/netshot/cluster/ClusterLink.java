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
package net.netshot.netshot.cluster;

import java.util.List;

/**
 * What the features need from the cluster (beside the task engine, see {@code ClusterCoordinator}): the members,
 * and the requests to the other members to reload what was changed locally. {@link #NONE} without clustering.
 */
public interface ClusterLink {

	/** No clustering: no member, the requests do nothing. */
	ClusterLink NONE = new ClusterLink() {
		@Override
		public List<ClusterMember> getClusterMembers() {
			return List.of();
		}

		@Override
		public void requestDriverReload() {
		}

		@Override
		public void requestVaultInstanceReload(long vaultInstanceId) {
		}
	};

	/**
	 * Gets the members of the cluster.
	 *
	 * @return copies of the members (empty without clustering)
	 */
	List<ClusterMember> getClusterMembers();

	/**
	 * Requests all the members to reload the device drivers.
	 */
	void requestDriverReload();

	/**
	 * Requests all the members to invalidate their local Vault cache for a Vault instance (e.g. after its connection
	 * settings changed).
	 *
	 * @param vaultInstanceId the ID of the Vault instance
	 */
	void requestVaultInstanceReload(long vaultInstanceId);
}
