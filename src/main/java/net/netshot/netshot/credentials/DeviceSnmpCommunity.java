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
package net.netshot.netshot.credentials;

import net.netshot.netshot.platform.secret.Secret;

/**
 * SNMP credentials (v1, v2c or v3).
 */
public sealed interface DeviceSnmpCommunity extends DeviceCredentialSet
	permits DeviceSnmpv1Community, DeviceSnmpv2cCommunity, DeviceSnmpv3Community {

	/**
	 * Gets the community.
	 *
	 * @return the community, local or in a Vault instance (null if not set)
	 */
	Secret community();
}
