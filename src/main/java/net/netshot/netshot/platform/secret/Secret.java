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
package net.netshot.netshot.platform.secret;

/**
 * The value of a credential field: either held by Netshot ({@link LocalSecret}, encrypted at rest when it is a
 * secret), or read from an external Vault instance ({@link VaultSecret}). A field which is not set is null.
 */
public sealed interface Secret permits LocalSecret, VaultSecret {

	/**
	 * Gets a local value.
	 *
	 * @param value the value, in clear text
	 * @return the local secret, or null if the value is null
	 */
	static LocalSecret local(String value) {
		return value == null ? null : LocalSecret.of(value);
	}

	/**
	 * Gets a reference to a secret of a Vault instance.
	 *
	 * @param vaultInstanceId the ID of the Vault instance
	 * @param vaultPath the KV v2 secret path and key holding the value, combined as {@code <path>/<key>}
	 * @return the reference
	 */
	static VaultSecret vault(long vaultInstanceId, String vaultPath) {
		return new VaultSecret(vaultInstanceId, vaultPath);
	}

	/**
	 * Gets a field from the columns it is stored in.
	 *
	 * @param localValue the local value
	 * @param vaultInstanceId the ID of the Vault instance, or null if the field is local
	 * @param vaultPath the Vault path (and key)
	 * @return the secret (the Vault reference wins over the local value), or null if it is not set
	 */
	static Secret of(LocalSecret localValue, Long vaultInstanceId, String vaultPath) {
		return vaultInstanceId != null ? new VaultSecret(vaultInstanceId, vaultPath) : localValue;
	}

	/**
	 * Gets the local value of a field, as it is stored.
	 *
	 * @param secret the field (may be null)
	 * @return the local value, or null if the field is not set or read from Vault
	 */
	static LocalSecret localValue(Secret secret) {
		return secret instanceof LocalSecret l ? l : null;
	}

	/**
	 * Gets the Vault instance of a field, as it is stored.
	 *
	 * @param secret the field (may be null)
	 * @return the ID of the Vault instance, or null if the field is not read from Vault
	 */
	static Long vaultInstanceId(Secret secret) {
		return secret instanceof VaultSecret v ? v.vaultInstanceId() : null;
	}

	/**
	 * Gets the Vault path of a field, as it is stored.
	 *
	 * @param secret the field (may be null)
	 * @return the Vault path (and key), or null if the field is not read from Vault
	 */
	static String vaultPath(Secret secret) {
		return secret instanceof VaultSecret v ? v.vaultPath() : null;
	}
}
