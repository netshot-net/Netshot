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
package net.netshot.netshot.api.rest.v1.credentials;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Ref;
import net.netshot.netshot.api.rest.v1.vault.VaultInstanceDto;

/**
 * Where a Vault-backed field of a credential set is read from (a location, not a secret).
 *
 * @param vaultInstance the Vault instance
 * @param path the path and key of the secret in the Vault instance
 */
@Schema(name = "VaultSecret", description = "Location of a secret in a Vault instance")
public record VaultSecretDto(@Ref VaultInstanceDto vaultInstance, String path) {
}
