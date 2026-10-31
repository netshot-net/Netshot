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
package net.netshot.netshot.api.rest.v1.credentials;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.RefId;

/**
 * Where to read a field of a credential set from, instead of giving its value.
 *
 * @param vaultInstance the Vault instance (its ID, or an object with its ID)
 * @param path the path and key of the secret in the Vault instance ({@code app/creds/password})
 */
@Schema(name = "VaultSecretRequest", description = "Location of a secret in a Vault instance")
public record VaultSecretRequest(
	@JsonProperty(required = true) RefId vaultInstance,
	@JsonProperty(required = true) String path) {
}
