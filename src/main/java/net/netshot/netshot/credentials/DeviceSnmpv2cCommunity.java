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

import jakarta.persistence.Entity;
import jakarta.xml.bind.annotation.XmlRootElement;


/**
 * A SNMPv2c community.
 */
@Entity
@XmlRootElement
public final class DeviceSnmpv2cCommunity extends DeviceSnmpCommunity {

	/**
	 * Instantiates a new device snmpv2c community.
	 */
	protected DeviceSnmpv2cCommunity() {

	}

	/**
	 * Instantiates a new device snmpv2c community.
	 *
	 * @param community the community
	 * @param name the name
	 */
	public DeviceSnmpv2cCommunity(String community, String name) {
		super(community, name);
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.credentials.DeviceSnmpCommunity#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (name == null ? 0 : name.hashCode());
		return result;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.credentials.DeviceSnmpCommunity#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof DeviceSnmpv2cCommunity)) {
			return false;
		}
		DeviceSnmpv2cCommunity other = (DeviceSnmpv2cCommunity) obj;
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		}
		else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

}
