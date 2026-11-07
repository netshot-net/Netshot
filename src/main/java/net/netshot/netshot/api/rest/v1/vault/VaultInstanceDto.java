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

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;

/**
 * A Vault instance (an external secret store), without its secrets.
 *
 * @param id the ID
 * @param url the path of the Vault instance
 * @param display the name
 * @param version the version, changed at each change (the ETag)
 * @param changeDate the date of the last change
 * @param name the name
 * @param type the type of Vault instance (HASHICORP_KV2)
 * @param baseUrl the base URL of the server
 * @param namespace the namespace (Vault Enterprise)
 * @param httpsCaTrustMode how the HTTPS certificate of the server is trusted
 * @param httpsCustomCaCertificate the PEM CA certificate trusted in CUSTOM_CA mode
 * @param kvMountPath the mount path of the KV v2 secrets engine
 * @param authMethod the authentication method (APPROLE or JWT)
 * @param appRoleMountPath the mount path of the AppRole authentication
 * @param appRoleId the AppRole role ID
 * @param hasAppRoleSecretId whether an AppRole secret ID is set (the secret is never returned)
 * @param jwtMountPath the mount path of the JWT authentication
 * @param jwtIdpTokenEndpoint the token endpoint of the identity provider issuing the JWT
 * @param jwtClientId the client ID at the identity provider
 * @param hasJwtClientSecret whether a client secret is set (the secret is never returned)
 * @param jwtVaultRole the Vault role to log in with
 * @param jwtScope the scope to request
 */
@Schema(name = "VaultInstance", description = "Vault instance, an external secret store (secrets are never returned)")
public record VaultInstanceDto(
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) long id,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String url,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String display,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) long version,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) Instant changeDate,
	@Brief String name,
	@Brief @Schema(allowableValues = {"HASHICORP_KV2"}) String type,
	@Brief String baseUrl,
	String namespace,
	@Brief HttpsCaTrustMode httpsCaTrustMode,
	String httpsCustomCaCertificate,
	String kvMountPath,
	@Brief AuthMethod authMethod,
	String appRoleMountPath,
	String appRoleId,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) boolean hasAppRoleSecretId,
	String jwtMountPath,
	String jwtIdpTokenEndpoint,
	String jwtClientId,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) boolean hasJwtClientSecret,
	String jwtVaultRole,
	String jwtScope) {
}
