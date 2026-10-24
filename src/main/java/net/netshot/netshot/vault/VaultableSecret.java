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
package net.netshot.netshot.vault;

/**
 * A field that can either hold a local (encrypted-at-rest) value, or a
 * reference to a secret stored in an external Vault instance (identified by
 * {@link #vaultInstanceId} and {@link #vaultPath}, the latter combining the
 * KV v2 secret path and the key within it, e.g. {@code "app/creds/username"}
 * - see {@link VaultKeyPath}).
 * <p>
 * This is a value object, not stored as such: each credential set holds the 3 values of
 * each of its vaultable fields as its own components (as they are stored, in columns of
 * their own), and gives them bundled through {@code xxxSecret()}, for {@link VaultManager}
 * to resolve them uniformly.
 *
 * @param localValue the local (encrypted-at-rest) value, used when not Vault-backed
 * @param vaultInstanceId the ID of the Vault instance to read the secret from, or null if not Vault-backed
 * @param vaultPath the KV v2 secret path and key holding the value, combined as {@code <path>/<key>}
 *        (the part after the last {@code /} is the key - see {@link VaultKeyPath})
 */
public record VaultableSecret(String localValue, Long vaultInstanceId, String vaultPath) {

	/**
	 * Tells whether the value is read from a Vault instance.
	 *
	 * @return true if the value is Vault-backed
	 */
	public boolean isVaultBacked() {
		return this.vaultInstanceId != null;
	}
}
