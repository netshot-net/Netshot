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
import java.util.Set;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.cluster.ClusterMember;

/**
 * Cluster message requesting other servers to invalidate their local
 * Vault token/secret cache for the given Vault instance IDs (e.g. after
 * an admin changed a Vault instance's connection settings).
 */
@XmlRootElement
public class VaultInstanceChangedMessage extends ClusterMessage {

	/** IDs of the Vault instances whose cache should be invalidated. */
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	Set<Long> vaultInstanceIds = new HashSet<>();

	/**
	 * Constructor.
	 * @param memberInfo = info about the cluster member
	 */
	public VaultInstanceChangedMessage(ClusterMember memberInfo) {
		super(memberInfo.getInstanceId());
	}

	/**
	 * Hidden constructor.
	 */
	protected VaultInstanceChangedMessage() {
	}

	public void addVaultInstanceIds(Collection<Long> newVaultInstanceIds) {
		this.vaultInstanceIds.addAll(newVaultInstanceIds);
	}
}
