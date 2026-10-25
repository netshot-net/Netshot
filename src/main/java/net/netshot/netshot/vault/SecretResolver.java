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
 * Resolves the actual value of a credential field: its local value, or the value read from Vault
 * (implemented by {@link VaultManager}, built by the wiring).
 */
@FunctionalInterface
public interface SecretResolver {

	/** Resolves the local values only: a Vault-backed field can't be resolved (tests, tools). */
	SecretResolver LOCAL = secret -> {
		if (secret.isVaultBacked()) {
			throw new VaultException("A Vault-backed field can't be resolved without Vault access");
		}
		return secret.localValue();
	};

	/**
	 * Resolves a field's actual value.
	 *
	 * @param secret the field to resolve
	 * @return the resolved value
	 * @throws VaultException if the field is Vault-backed and the value couldn't be resolved
	 */
	String resolve(VaultableSecret secret) throws VaultException;
}
