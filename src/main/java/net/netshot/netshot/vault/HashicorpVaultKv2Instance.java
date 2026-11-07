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

import java.time.Instant;

import lombok.Builder;
import net.netshot.netshot.platform.secret.LocalSecret;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * A HashiCorp Vault server, with a KV version 2 secret engine, Netshot logging in with AppRole or JWT.
 *
 * @param id the ID (0 for an instance not stored yet)
 * @param version the version, incremented at each change
 * @param changeDate the date of the last change
 * @param name the name
 * @param baseUrl the base URL of the server
 * @param namespace the namespace (Vault Enterprise)
 * @param httpsCaTrustMode how the HTTPS certificate of the server is trusted
 * @param httpsCustomCaCertificate the CA certificate trusted in custom CA mode
 * @param kvMountPath the mount path of the KV secret engine
 * @param authMethod the authentication method
 * @param appRoleMountPath the mount path of the AppRole authentication
 * @param appRoleId the AppRole role ID
 * @param appRoleSecretId the AppRole secret ID
 * @param jwtMountPath the mount path of the JWT authentication
 * @param jwtIdpTokenEndpoint the token endpoint of the identity provider issuing the JWT
 * @param jwtClientId the client ID at the identity provider
 * @param jwtClientSecret the client secret at the identity provider
 * @param jwtVaultRole the Vault role to log in with
 * @param jwtScope the scope to request
 */
@Builder(builderMethodName = "defaults", toBuilder = true)
public record HashicorpVaultKv2Instance(long id, int version, Instant changeDate, String name, String baseUrl, String namespace,
	HttpsCaTrustMode httpsCaTrustMode, String httpsCustomCaCertificate, String kvMountPath, AuthMethod authMethod,
	String appRoleMountPath, String appRoleId, LocalSecret appRoleSecretId, String jwtMountPath, String jwtIdpTokenEndpoint,
	String jwtClientId, LocalSecret jwtClientSecret, String jwtVaultRole, String jwtScope) implements VaultInstance {

	/** The authentication methods of Netshot to Vault. */
	public enum AuthMethod {
		APPROLE,
		JWT
	}

	/**
	 * Starts building an instance, with the default trust mode and mount paths.
	 *
	 * @param name the name
	 * @return the builder
	 */
	public static HashicorpVaultKv2InstanceBuilder builder(String name) {
		return defaults()
			.name(name)
			.httpsCaTrustMode(HttpsCaTrustMode.SYSTEM_TRUSTSTORE)
			.kvMountPath("secret")
			.appRoleMountPath("approle")
			.jwtMountPath("jwt");
	}

	@Override
	public HashicorpVaultKv2Instance withId(long newId) {
		return this.toBuilder().id(newId).build();
	}

	@Override
	public String toString() {
		return VaultInstance.describe(this);
	}
}
