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
package net.netshot.netshot.credentials;

import net.netshot.netshot.vault.VaultableSecret;

/**
 * SNMP credentials (v1, v2c or v3).
 */
public sealed interface DeviceSnmpCommunity extends DeviceCredentialSet
	permits DeviceSnmpv1Community, DeviceSnmpv2cCommunity, DeviceSnmpv3Community {

	/**
	 * Gets the local community.
	 *
	 * @return the community
	 */
	String community();

	/**
	 * Gets the Vault instance the community is read from.
	 *
	 * @return the ID of the Vault instance, or null if the community is local
	 */
	Long communityVaultInstanceId();

	/**
	 * Gets the Vault path of the community.
	 *
	 * @return the path (and key)
	 */
	String communityVaultPath();

	/**
	 * Gets the community, local or in a Vault instance.
	 *
	 * @return the community
	 */
	default VaultableSecret communitySecret() {
		return new VaultableSecret(this.community(), this.communityVaultInstanceId(), this.communityVaultPath());
	}
}
