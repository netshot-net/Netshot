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
package net.netshot.netshot.vault;

import net.netshot.netshot.platform.secret.LocalSecret;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.platform.secret.VaultSecret;

/**
 * Resolves the actual value of a credential field: its local value (decrypted), or the value read from Vault
 * (implemented by {@link VaultManager}, built by the wiring).
 */
@FunctionalInterface
public interface SecretResolver {

	/** Resolves the local values only: a Vault-backed field can't be resolved (tests, tools). */
	SecretResolver LOCAL = secret -> switch (secret) {
		case null -> null;
		case LocalSecret l -> l.reveal();
		case VaultSecret _ -> throw new VaultException("A Vault-backed field can't be resolved without Vault access");
	};

	/**
	 * Resolves a field's actual value.
	 *
	 * @param secret the field to resolve (may be null)
	 * @return the resolved value, null if the field is not set
	 * @throws VaultException if the field is Vault-backed and the value couldn't be resolved
	 */
	String resolve(Secret secret) throws VaultException;
}
