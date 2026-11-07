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

import java.time.Instant;
import java.util.UUID;

import net.netshot.netshot.domain.Domain;

/**
 * A set of credentials to connect to devices: either a global one (which can be used by
 * the devices of its domain, or of all the domains), or one specific to one access of one
 * device. Its type (the name of its record) is stored as the discriminator.
 */
public sealed interface DeviceCredentialSet permits DeviceCliAccount, DeviceSnmpCommunity, DeviceHttpAccount {

	/**
	 * Generates a name for a credential set specific to a device access (the name of a
	 * credential set has to be unique).
	 *
	 * @return the name
	 */
	static String generateSpecificName() {
		return String.format("DEVICESPECIFIC-%s", UUID.randomUUID());
	}

	/**
	 * Describes a credential set (for the logs).
	 *
	 * @param credentialSet the credential set
	 * @return the description
	 */
	static String describe(DeviceCredentialSet credentialSet) {
		return "Device Credential Set " + credentialSet.id() + " (name '" + credentialSet.name()
			+ "', type " + credentialSet.getClass().getSimpleName() + ")";
	}

	/**
	 * Gets the ID.
	 *
	 * @return the ID (0 for a credential set which isn't stored yet)
	 */
	long id();

	/**
	 * Gets the version.
	 *
	 * @return the version, incremented on each change
	 */
	int version();

	/**
	 * Gets the time of the last change.
	 *
	 * @return the time
	 */
	Instant changeDate();

	/**
	 * Gets the name.
	 *
	 * @return the name, unique
	 */
	String name();

	/**
	 * Gets the domain the credential set is restricted to.
	 *
	 * @return the domain, or null for all the domains
	 */
	Domain mgmtDomain();

	/**
	 * Tells whether the credential set belongs to one access of one device.
	 *
	 * @return true for a device specific credential set, false for a global one
	 */
	boolean deviceSpecific();

	/**
	 * Gets the same credential set, with another identity.
	 *
	 * @param newId the new ID
	 * @param newName the new name
	 * @param newDeviceSpecific whether it is specific to a device access
	 * @return the credential set
	 */
	DeviceCredentialSet withIdentity(long newId, String newName, boolean newDeviceSpecific);

	/**
	 * Gets the same credential set, restricted to another domain.
	 *
	 * @param newMgmtDomain the domain, or null for all the domains
	 * @return the credential set
	 */
	DeviceCredentialSet withMgmtDomain(Domain newMgmtDomain);

	/**
	 * Gets the type of the credential set, as it is stored.
	 *
	 * @return the type
	 */
	default String typeName() {
		return this.getClass().getSimpleName();
	}
}
