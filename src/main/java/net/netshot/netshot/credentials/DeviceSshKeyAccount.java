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

import lombok.Builder;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.platform.secret.Secret;

/**
 * An SSH account, authenticated with a private key (the password is the passphrase of the key).
 *
 * @param id the ID (0 for a credential set which isn't stored yet)
 * @param version the version, incremented on each change
 * @param changeDate the time of the last change
 * @param name the name, unique
 * @param mgmtDomain the domain the credential set is restricted to, or null for all the domains
 * @param deviceSpecific whether the credential set belongs to one access of one device (rather than being global)
 * @param username the username, local or in a Vault instance (null if not set)
 * @param password the passphrase of the private key, local or in a Vault instance (null if not set)
 * @param superPassword the super (enable) password, local or in a Vault instance (null if not set)
 * @param privateKey the private key, local or in a Vault instance (null if not set)
 */
@Builder(toBuilder = true)
public record DeviceSshKeyAccount(
	long id,
	int version,
	Instant changeDate,
	String name,
	Domain mgmtDomain,
	boolean deviceSpecific,
	Secret username,
	Secret password,
	Secret superPassword,
	Secret privateKey) implements SshAccount {

	@Override
	public DeviceSshKeyAccount withIdentity(long newId, String newName, boolean newDeviceSpecific) {
		return this.toBuilder().id(newId).name(newName).deviceSpecific(newDeviceSpecific).build();
	}

	@Override
	public DeviceSshKeyAccount withMgmtDomain(Domain newMgmtDomain) {
		return this.toBuilder().mgmtDomain(newMgmtDomain).build();
	}

	@Override
	public String toString() {
		return DeviceCredentialSet.describe(this);
	}
}
