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
package net.netshot.netshot.api.rest.v0.vault;

import java.util.ArrayList;
import java.util.List;

import net.netshot.netshot.platform.secret.LocalSecret;
import net.netshot.netshot.vault.SecretChange;
import net.netshot.netshot.vault.VaultInstanceDefinition;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance;
import net.netshot.netshot.vault.VaultInstance;

/**
 * Maps the Vault instances between the entities and their REST representation.
 * All the methods are pure functions. The representation never holds a secret.
 */
public final class VaultInstanceMapper {

	private VaultInstanceMapper() {
		// Static helpers only
	}

	/**
	 * Maps a Vault instance to its representation, according to its kind.
	 *
	 * @param vaultInstance the Vault instance
	 * @return the DTO
	 * @throws IllegalArgumentException if the kind of Vault instance is unknown
	 */
	public static VaultInstanceDto toDto(VaultInstance vaultInstance) {
		return switch (vaultInstance) {
			case HashicorpVaultKv2Instance hc -> new VaultInstanceDto.HashicorpVaultKv2(hc.id(), hc.name(), hc.baseUrl(), hc.namespace(),
				hc.httpsCaTrustMode(), hc.httpsCustomCaCertificate(), hc.kvMountPath(), hc.authMethod(),
				hc.appRoleMountPath(), hc.appRoleId(), masked(hc.appRoleSecretId()), hc.jwtMountPath(),
				hc.jwtIdpTokenEndpoint(), hc.jwtClientId(), masked(hc.jwtClientSecret()), hc.jwtVaultRole(), hc.jwtScope());
		};
	}

	// The DTO masks the secrets: only whether there is one matters (it is not decrypted)
	private static String masked(LocalSecret secret) {
		return secret == null ? null : "=";
	}

	/**
	 * Maps Vault instances to their representation.
	 *
	 * @param vaultInstances the Vault instances
	 * @return the DTOs, in the same order
	 */
	public static List<VaultInstanceDto> toDtos(List<VaultInstance> vaultInstances) {
		List<VaultInstanceDto> dtos = new ArrayList<>(vaultInstances.size());
		vaultInstances.forEach(vaultInstance -> dtos.add(toDto(vaultInstance)));
		return dtos;
	}

	/**
	 * Builds the definition of a Vault instance from a request, as the v0 API always read it: a secret which is not given
	 * (or given as "=") is unchanged. The ID of the request is ignored.
	 *
	 * @param request the request
	 * @return the definition
	 */
	public static VaultInstanceDefinition toDefinition(VaultInstanceRequest request) {
		if (request instanceof VaultInstanceRequest.HashicorpVaultKv2 hc) {
			return new VaultInstanceDefinition(hc.getName(), hc.getBaseUrl(), hc.getNamespace(), hc.getHttpsCaTrustMode(),
				hc.getHttpsCustomCaCertificate(), hc.getKvMountPath(), hc.getAuthMethod(), hc.getAppRoleMountPath(), hc.getAppRoleId(),
				secret(hc.getAppRoleSecretId()), hc.getJwtMountPath(), hc.getJwtIdpTokenEndpoint(), hc.getJwtClientId(),
				secret(hc.getJwtClientSecret()), hc.getJwtVaultRole(), hc.getJwtScope());
		}
		throw new IllegalArgumentException("Unknown kind of Vault instance request: " + request.getClass().getName());
	}

	private static SecretChange secret(String value) {
		return value == null ? SecretChange.UNCHANGED : new SecretChange.Local(value);
	}
}
