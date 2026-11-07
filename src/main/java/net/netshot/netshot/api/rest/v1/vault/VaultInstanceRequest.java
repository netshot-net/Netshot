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
package net.netshot.netshot.api.rest.v1.vault;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.api.rest.v1.Secret;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;

/**
 * The definition of a Vault instance, to create one ({@code POST}), replace one ({@code PUT}, and the result of a
 * {@code PATCH}) or test one. The read-only properties of the representation ({@link VaultInstanceDto}) are ignored.
 * Absent optional properties get their default.
 *
 * @param name the name
 * @param type the type of Vault instance (HASHICORP_KV2)
 * @param baseUrl the base URL of the server (http or https)
 * @param namespace the namespace (Vault Enterprise)
 * @param httpsCaTrustMode how the HTTPS certificate of the server is trusted (SYSTEM_TRUSTSTORE if absent)
 * @param httpsCustomCaCertificate the PEM CA certificate trusted in CUSTOM_CA mode
 * @param kvMountPath the mount path of the KV v2 secrets engine ("secret" if absent)
 * @param authMethod the authentication method
 * @param appRoleMountPath the mount path of the AppRole authentication ("approle" if absent)
 * @param appRoleId the AppRole role ID (required with APPROLE)
 * @param appRoleSecretId the AppRole secret ID (write-only; required with APPROLE; absent: unchanged)
 * @param jwtMountPath the mount path of the JWT authentication ("jwt" if absent)
 * @param jwtIdpTokenEndpoint the token endpoint of the identity provider (required with JWT)
 * @param jwtClientId the client ID at the identity provider (required with JWT)
 * @param jwtClientSecret the client secret (write-only; required with JWT; absent: unchanged)
 * @param jwtVaultRole the Vault role to log in with (required with JWT)
 * @param jwtScope the scope to request
 */
@Schema(name = "VaultInstanceRequest", description = "Definition of a Vault instance")
@ReadOnlyFrom(VaultInstanceDto.class)
public record VaultInstanceRequest(
	@JsonProperty(required = true) String name,
	@JsonProperty(required = true) @Schema(allowableValues = {"HASHICORP_KV2"}) String type,
	@JsonProperty(required = true) String baseUrl,
	String namespace,
	HttpsCaTrustMode httpsCaTrustMode,
	String httpsCustomCaCertificate,
	String kvMountPath,
	@JsonProperty(required = true) AuthMethod authMethod,
	String appRoleMountPath,
	String appRoleId,
	@JsonInclude(JsonInclude.Include.NON_NULL) Secret appRoleSecretId,
	String jwtMountPath,
	String jwtIdpTokenEndpoint,
	String jwtClientId,
	@JsonInclude(JsonInclude.Include.NON_NULL) Secret jwtClientSecret,
	String jwtVaultRole,
	String jwtScope) {
}
