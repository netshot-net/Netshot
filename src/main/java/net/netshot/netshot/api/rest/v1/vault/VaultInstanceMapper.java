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
package net.netshot.netshot.api.rest.v1.vault;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;

import net.netshot.netshot.api.rest.v1.Secret;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance;
import net.netshot.netshot.vault.SecretChange;
import net.netshot.netshot.vault.VaultConnectionTestResult;
import net.netshot.netshot.vault.VaultInstance;
import net.netshot.netshot.vault.VaultInstanceDefinition;
import net.netshot.netshot.vault.VaultInstanceService;

/**
 * Maps the Vault instances between the domain records and their v1 representation. The representation never
 * holds a secret, only whether it is set.
 */
public final class VaultInstanceMapper {

	/** The path of the Vault instances. */
	public static final String PATH = "vault-instances";

	/** The type name of the HashiCorp Vault KV v2 instances. */
	public static final String TYPE_HASHICORP_KV2 = "HASHICORP_KV2";

	private VaultInstanceMapper() {
	}

	/**
	 * Gives the representation of a Vault instance by its ID only (not loaded: an instance deleted meanwhile).
	 *
	 * @param id the ID of the Vault instance
	 * @return the representation
	 */
	public static VaultInstanceDto byId(long id) {
		return new VaultInstanceDto(id, V1Links.of(PATH, id), null, 0, null, null, null, null, null, null, null, null,
			null, null, null, false, null, null, null, false, null, null);
	}

	/**
	 * Loads Vault instances in one query, to write the references to them.
	 *
	 * @param vaultInstanceService the Vault instance use cases
	 * @param ids the IDs of the Vault instances (possibly repeated; none, no query)
	 * @return the Vault instances by ID (the ones not found, by ID only)
	 */
	public static LongFunction<VaultInstanceDto> resolver(VaultInstanceService vaultInstanceService, Collection<Long> ids) {
		Set<Long> wanted = new HashSet<>(ids);
		Map<Long, VaultInstanceDto> loaded = new HashMap<>();
		if (!wanted.isEmpty()) {
			vaultInstanceService.list(ListQuery.byIds(wanted)).items().forEach(v -> loaded.put(v.id(), toDto(v)));
		}
		return id -> {
			VaultInstanceDto found = loaded.get(id);
			return found != null ? found : byId(id);
		};
	}

	/**
	 * Maps a Vault instance (without its secrets).
	 *
	 * @param vaultInstance the Vault instance (null gives null)
	 * @return the representation
	 */
	public static VaultInstanceDto toDto(VaultInstance vaultInstance) {
		if (vaultInstance == null) {
			return null;
		}
		return switch (vaultInstance) {
			case HashicorpVaultKv2Instance hc -> new VaultInstanceDto(hc.id(), V1Links.of(PATH, hc.id()), hc.name(), hc.version(),
				hc.changeDate(), hc.name(), TYPE_HASHICORP_KV2, hc.baseUrl(), hc.namespace(), hc.httpsCaTrustMode(),
				hc.httpsCustomCaCertificate(), hc.kvMountPath(), hc.authMethod(), hc.appRoleMountPath(), hc.appRoleId(),
				hc.appRoleSecretId() != null, hc.jwtMountPath(), hc.jwtIdpTokenEndpoint(), hc.jwtClientId(),
				hc.jwtClientSecret() != null, hc.jwtVaultRole(), hc.jwtScope());
		};
	}

	/**
	 * Maps a Vault instance to the request which would define it as it is (the base of a merge patch): the
	 * secrets are absent, which leaves them unchanged.
	 *
	 * @param vaultInstance the Vault instance
	 * @return the request
	 */
	public static VaultInstanceRequest toRequest(VaultInstance vaultInstance) {
		return switch (vaultInstance) {
			case HashicorpVaultKv2Instance hc -> new VaultInstanceRequest(hc.name(), TYPE_HASHICORP_KV2, hc.baseUrl(), hc.namespace(),
				hc.httpsCaTrustMode(), hc.httpsCustomCaCertificate(), hc.kvMountPath(), hc.authMethod(), hc.appRoleMountPath(),
				hc.appRoleId(), null, hc.jwtMountPath(), hc.jwtIdpTokenEndpoint(), hc.jwtClientId(), null, hc.jwtVaultRole(),
				hc.jwtScope());
		};
	}

	private static SecretChange change(Secret secret) {
		if (secret == null) {
			return SecretChange.UNCHANGED;
		}
		return secret.value() == null ? SecretChange.CLEARED : new SecretChange.Local(secret.value());
	}

	/**
	 * Maps a request to the definition the service takes.
	 *
	 * @param request the request
	 * @return the definition
	 * @throws NetshotException if the type is unknown
	 */
	public static VaultInstanceDefinition toDefinition(VaultInstanceRequest request) {
		if (!TYPE_HASHICORP_KV2.equals(request.type())) {
			throw NetshotException.invalidParameter("type", "Unknown type of Vault instance '%s': %s is expected".formatted(
				request.type(), TYPE_HASHICORP_KV2));
		}
		return new VaultInstanceDefinition(request.name(), request.baseUrl(), request.namespace(), request.httpsCaTrustMode(),
			request.httpsCustomCaCertificate(), request.kvMountPath(), request.authMethod(), request.appRoleMountPath(),
			request.appRoleId(), change(request.appRoleSecretId()), request.jwtMountPath(), request.jwtIdpTokenEndpoint(),
			request.jwtClientId(), change(request.jwtClientSecret()), request.jwtVaultRole(), request.jwtScope());
	}

	/**
	 * Maps the outcome of a connection test.
	 *
	 * @param result the outcome
	 * @return the representation
	 */
	public static VaultConnectionTestDto toDto(VaultConnectionTestResult result) {
		return new VaultConnectionTestDto(result.success(), result.message());
	}
}
