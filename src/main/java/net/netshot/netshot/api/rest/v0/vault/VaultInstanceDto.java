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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.credentials.HideSecretSerializer;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;

/**
 * The representation of a Vault instance, according to its kind (the {@code type} property).
 * The secrets (AppRole secret ID, JWT client secret) are never written: {@code =} stands for them.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = VaultInstanceDto.HashicorpVaultKv2.class, name = "Vault KV v2"),
})
public sealed interface VaultInstanceDto {

	/**
	 * Gets the Vault instance ID.
	 *
	 * @return the ID
	 */
	long id();

	/**
	 * Gets the name of the Vault instance.
	 *
	 * @return the name
	 */
	String name();

	/**
	 * A HashiCorp Vault instance using the KV v2 secrets engine.
	 *
	 * @param id the Vault instance ID
	 * @param name the name
	 * @param baseUrl the base URL of the Vault server
	 * @param namespace the namespace (Vault Enterprise)
	 * @param httpsCaTrustMode how the certificate of the server is trusted
	 * @param httpsCustomCaCertificate the PEM trust anchors, for the custom CA mode
	 * @param kvMountPath the mount path of the KV v2 secrets engine
	 * @param authMethod the authentication method
	 * @param appRoleMountPath the mount path of the AppRole authentication method
	 * @param appRoleId the AppRole role ID
	 * @param appRoleSecretId the AppRole secret ID (masked)
	 * @param jwtMountPath the mount path of the JWT authentication method
	 * @param jwtIdpTokenEndpoint the token endpoint of the identity provider
	 * @param jwtClientId the OAuth2 client ID
	 * @param jwtClientSecret the OAuth2 client secret (masked)
	 * @param jwtVaultRole the Vault role to log in as
	 * @param jwtScope the OAuth2 scope
	 */
	@JacksonXmlRootElement(localName = "HashicorpVaultKv2Instance")
	record HashicorpVaultKv2(
		long id,
		String name,
		String baseUrl,
		String namespace,
		HttpsCaTrustMode httpsCaTrustMode,
		String httpsCustomCaCertificate,
		String kvMountPath,
		AuthMethod authMethod,
		String appRoleMountPath,
		String appRoleId,
		@JsonSerialize(using = HideSecretSerializer.class) String appRoleSecretId,
		String jwtMountPath,
		String jwtIdpTokenEndpoint,
		String jwtClientId,
		@JsonSerialize(using = HideSecretSerializer.class) String jwtClientSecret,
		String jwtVaultRole,
		String jwtScope) implements VaultInstanceDto {
	}
}
