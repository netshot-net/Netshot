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

import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;

/**
 * What a client defines of a HashiCorp Vault instance (v1 API). The secrets say what to do with the stored ones.
 *
 * @param name the name
 * @param baseUrl the base URL of the server
 * @param namespace the namespace
 * @param httpsCaTrustMode how the certificate of the server is trusted
 * @param httpsCustomCaCertificate the CA certificate trusted in custom CA mode
 * @param kvMountPath the mount path of the KV secret engine
 * @param authMethod the authentication method
 * @param appRoleMountPath the mount path of the AppRole authentication
 * @param appRoleId the AppRole role ID
 * @param appRoleSecretId what to do with the AppRole secret ID
 * @param jwtMountPath the mount path of the JWT authentication
 * @param jwtIdpTokenEndpoint the token endpoint of the identity provider
 * @param jwtClientId the client ID at the identity provider
 * @param jwtClientSecret what to do with the client secret
 * @param jwtVaultRole the Vault role to log in with
 * @param jwtScope the scope to request
 */
public record VaultInstanceDefinition(String name, String baseUrl, String namespace, HttpsCaTrustMode httpsCaTrustMode,
	String httpsCustomCaCertificate, String kvMountPath, AuthMethod authMethod, String appRoleMountPath, String appRoleId,
	SecretChange appRoleSecretId, String jwtMountPath, String jwtIdpTokenEndpoint, String jwtClientId,
	SecretChange jwtClientSecret, String jwtVaultRole, String jwtScope) {
}
