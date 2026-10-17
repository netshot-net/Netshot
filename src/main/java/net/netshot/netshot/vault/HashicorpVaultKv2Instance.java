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
package net.netshot.netshot.vault;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.platform.database.StringEncryptorConverter;

/**
 * A HashiCorp Vault instance, using the KV v2 secrets engine.
 * <p>
 * Authenticates using either AppRole (static role_id/secret_id) or JWT
 * (a JWT obtained from an external OAuth2 IdP via the client_credentials
 * grant, then exchanged with Vault's JWT auth method).
 */
@Entity
public class HashicorpVaultKv2Instance extends VaultInstance {

	public enum AuthMethod {
		APPROLE,
		JWT
	}

	/** The KV v2 secrets engine mount path (e.g. "secret"). */
	@Getter(onMethod = @__({
		@Column(name = "kv_mount_path")
	}))
	@Setter
	private String kvMountPath = "secret";

	/** The authentication method used to log in to this Vault instance. */
	@Getter(onMethod = @__({
		@Enumerated(EnumType.STRING),
		@Column(name = "auth_method", nullable = false)
	}))
	@Setter
	private AuthMethod authMethod;

	/**
	 * The AppRole auth method's mount path (e.g. "approle" by default, but
	 * Vault/OpenBao allow mounting an auth method at any custom path, such
	 * as when multiple AppRole mounts serve different consumers).
	 */
	@Getter(onMethod = @__({
		@Column(name = "app_role_mount_path")
	}))
	@Setter
	private String appRoleMountPath = "approle";

	/** AppRole role_id (used when {@link #authMethod} is {@link AuthMethod#APPROLE}). */
	@Getter(onMethod = @__({
		@Column(name = "app_role_id")
	}))
	@Setter
	private String appRoleId;

	/** AppRole secret_id (used when {@link #authMethod} is {@link AuthMethod#APPROLE}). */
	@Getter(onMethod = @__({
		@Column(name = "app_role_secret_id"),
		@Convert(converter = StringEncryptorConverter.class)
	}))
	@Setter
	private String appRoleSecretId;

	/**
	 * The JWT auth method's mount path (e.g. "jwt" by default, but Vault/OpenBao
	 * allow mounting an auth method at any custom path, such as "oidc" or a
	 * per-tenant mount).
	 */
	@Getter(onMethod = @__({
		@Column(name = "jwt_mount_path")
	}))
	@Setter
	private String jwtMountPath = "jwt";

	/** External IdP token endpoint URL (used when {@link #authMethod} is {@link AuthMethod#JWT}). */
	@Getter(onMethod = @__({
		@Column(name = "jwt_idp_token_endpoint")
	}))
	@Setter
	private String jwtIdpTokenEndpoint;

	/** OAuth2 client ID used to obtain the JWT from the external IdP. */
	@Getter(onMethod = @__({
		@Column(name = "jwt_client_id")
	}))
	@Setter
	private String jwtClientId;

	/** OAuth2 client secret used to obtain the JWT from the external IdP. */
	@Getter(onMethod = @__({
		@Column(name = "jwt_client_secret"),
		@Convert(converter = StringEncryptorConverter.class)
	}))
	@Setter
	private String jwtClientSecret;

	/** Vault-side role name to present when logging in via the JWT auth method. */
	@Getter(onMethod = @__({
		@Column(name = "jwt_vault_role")
	}))
	@Setter
	private String jwtVaultRole;

	/** Optional OAuth2 scope to request from the external IdP. */
	@Getter(onMethod = @__({
		@Column(name = "jwt_scope")
	}))
	@Setter
	private String jwtScope;

	/**
	 * Instantiates a new Hashicorp Vault KV v2 instance.
	 */
	protected HashicorpVaultKv2Instance() {
		// Reserved for Hibernate
	}

	/**
	 * Instantiates a new Hashicorp Vault KV v2 instance.
	 * @param name the name
	 */
	public HashicorpVaultKv2Instance(String name) {
		super(name);
	}

}
