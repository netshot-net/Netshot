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
package net.netshot.netshot.platform.secret;

/**
 * A reference to a secret stored in an external Vault instance.
 *
 * @param vaultInstanceId the ID of the Vault instance to read the secret from
 * @param vaultPath the KV v2 secret path and key holding the value, combined as {@code <path>/<key>}
 *        (the part after the last {@code /} is the key - see {@code net.netshot.netshot.vault.VaultKeyPath})
 */
public record VaultSecret(long vaultInstanceId, String vaultPath) implements Secret {
}
