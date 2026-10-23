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

import java.time.Instant;

import lombok.Builder;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.vault.VaultableSecret;

/**
 * An SSH account, authenticated with a private key (the password is the passphrase of the key).
 *
 * @param id the ID (0 for a credential set which isn't stored yet)
 * @param version the version, incremented on each change
 * @param changeDate the time of the last change
 * @param name the name, unique
 * @param mgmtDomain the domain the credential set is restricted to, or null for all the domains
 * @param deviceSpecific whether the credential set belongs to one access of one device (rather than being global)
 * @param username the username
 * @param usernameVaultInstanceId the ID of the Vault instance the username is read from, or null if it is local
 * @param usernameVaultPath the Vault path (and key) of the username
 * @param password the passphrase of the private key
 * @param passwordVaultInstanceId the ID of the Vault instance the passphrase of the private key is read from, or null if it is local
 * @param passwordVaultPath the Vault path (and key) of the passphrase of the private key
 * @param superPassword the super (enable) password
 * @param superPasswordVaultInstanceId the ID of the Vault instance the super (enable) password is read from, or null if it is local
 * @param superPasswordVaultPath the Vault path (and key) of the super (enable) password
 * @param privateKey the private key
 * @param privateKeyVaultInstanceId the ID of the Vault instance the private key is read from, or null if it is local
 * @param privateKeyVaultPath the Vault path (and key) of the private key
 */
@Builder(toBuilder = true)
public record DeviceSshKeyAccount(
	long id,
	int version,
	Instant changeDate,
	String name,
	Domain mgmtDomain,
	boolean deviceSpecific,
	String username,
	Long usernameVaultInstanceId,
	String usernameVaultPath,
	String password,
	Long passwordVaultInstanceId,
	String passwordVaultPath,
	String superPassword,
	Long superPasswordVaultInstanceId,
	String superPasswordVaultPath,
	String privateKey,
	Long privateKeyVaultInstanceId,
	String privateKeyVaultPath) implements SshAccount {

	@Override
	public DeviceSshKeyAccount withIdentity(long newId, String newName, boolean newDeviceSpecific) {
		return this.toBuilder().id(newId).name(newName).deviceSpecific(newDeviceSpecific).build();
	}

	@Override
	public DeviceSshKeyAccount withMgmtDomain(Domain newMgmtDomain) {
		return this.toBuilder().mgmtDomain(newMgmtDomain).build();
	}

	/**
	 * Gets the private key, local or in a Vault instance.
	 *
	 * @return the private key
	 */
	public VaultableSecret privateKeySecret() {
		return new VaultableSecret(this.privateKey, this.privateKeyVaultInstanceId, this.privateKeyVaultPath);
	}

	@Override
	public String toString() {
		return DeviceCredentialSet.describe(this);
	}
}
