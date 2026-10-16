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
package net.netshot.netshot.api.rest.v0.vault;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;

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
		VaultInstance instance = (VaultInstance) Hibernate.unproxy(vaultInstance);
		if (instance instanceof HashicorpVaultKv2Instance hc) {
			return new VaultInstanceDto.HashicorpVaultKv2(hc.getId(), hc.getName(), hc.getBaseUrl(), hc.getNamespace(),
				hc.getHttpsCaTrustMode(), hc.getHttpsCustomCaCertificate(), hc.getKvMountPath(), hc.getAuthMethod(),
				hc.getAppRoleMountPath(), hc.getAppRoleId(), hc.getAppRoleSecretId(), hc.getJwtMountPath(),
				hc.getJwtIdpTokenEndpoint(), hc.getJwtClientId(), hc.getJwtClientSecret(), hc.getJwtVaultRole(), hc.getJwtScope());
		}
		throw new IllegalArgumentException("Unknown kind of Vault instance: " + instance.getClass().getName());
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
	 * Maps a request to a transient Vault instance of the matching kind, holding what the client sent.
	 *
	 * @param request the request
	 * @return the Vault instance, not persisted
	 */
	public static VaultInstance toEntity(VaultInstanceRequest request) {
		if (request instanceof VaultInstanceRequest.HashicorpVaultKv2 hc) {
			HashicorpVaultKv2Instance instance = new HashicorpVaultKv2Instance(hc.getName());
			instance.setId(hc.getId());
			instance.setBaseUrl(hc.getBaseUrl());
			instance.setNamespace(hc.getNamespace());
			instance.setHttpsCaTrustMode(hc.getHttpsCaTrustMode());
			instance.setHttpsCustomCaCertificate(hc.getHttpsCustomCaCertificate());
			instance.setKvMountPath(hc.getKvMountPath());
			instance.setAuthMethod(hc.getAuthMethod());
			instance.setAppRoleMountPath(hc.getAppRoleMountPath());
			instance.setAppRoleId(hc.getAppRoleId());
			instance.setAppRoleSecretId(hc.getAppRoleSecretId());
			instance.setJwtMountPath(hc.getJwtMountPath());
			instance.setJwtIdpTokenEndpoint(hc.getJwtIdpTokenEndpoint());
			instance.setJwtClientId(hc.getJwtClientId());
			instance.setJwtClientSecret(hc.getJwtClientSecret());
			instance.setJwtVaultRole(hc.getJwtVaultRole());
			instance.setJwtScope(hc.getJwtScope());
			return instance;
		}
		throw new IllegalArgumentException("Unknown kind of Vault instance request: " + request.getClass().getName());
	}
}
