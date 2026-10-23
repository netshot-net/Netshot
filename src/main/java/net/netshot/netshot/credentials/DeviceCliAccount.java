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
 * An account to log into the CLI of devices (SSH or Telnet).
 */
public sealed interface DeviceCliAccount extends DeviceCredentialSet permits SshAccount, DeviceTelnetAccount {

	/**
	 * Gets the local username.
	 *
	 * @return the username
	 */
	String username();

	/**
	 * Gets the Vault instance the username is read from.
	 *
	 * @return the ID of the Vault instance, or null if the username is local
	 */
	Long usernameVaultInstanceId();

	/**
	 * Gets the Vault path of the username.
	 *
	 * @return the path (and key)
	 */
	String usernameVaultPath();

	/**
	 * Gets the local password.
	 *
	 * @return the password
	 */
	String password();

	/**
	 * Gets the Vault instance the password is read from.
	 *
	 * @return the ID of the Vault instance, or null if the password is local
	 */
	Long passwordVaultInstanceId();

	/**
	 * Gets the Vault path of the password.
	 *
	 * @return the path (and key)
	 */
	String passwordVaultPath();

	/**
	 * Gets the local super (enable) password.
	 *
	 * @return the super password
	 */
	String superPassword();

	/**
	 * Gets the Vault instance the super password is read from.
	 *
	 * @return the ID of the Vault instance, or null if the super password is local
	 */
	Long superPasswordVaultInstanceId();

	/**
	 * Gets the Vault path of the super password.
	 *
	 * @return the path (and key)
	 */
	String superPasswordVaultPath();

	/**
	 * Gets the username, local or in a Vault instance.
	 *
	 * @return the username
	 */
	default VaultableSecret usernameSecret() {
		return new VaultableSecret(this.username(), this.usernameVaultInstanceId(), this.usernameVaultPath());
	}

	/**
	 * Gets the password, local or in a Vault instance.
	 *
	 * @return the password
	 */
	default VaultableSecret passwordSecret() {
		return new VaultableSecret(this.password(), this.passwordVaultInstanceId(), this.passwordVaultPath());
	}

	/**
	 * Gets the super password, local or in a Vault instance.
	 *
	 * @return the super password
	 */
	default VaultableSecret superPasswordSecret() {
		return new VaultableSecret(this.superPassword(), this.superPasswordVaultInstanceId(), this.superPasswordVaultPath());
	}
}
