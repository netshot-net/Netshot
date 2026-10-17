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
package net.netshot.netshot.credentials;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.platform.database.StringEncryptorConverter;
import net.netshot.netshot.vault.VaultInstance;
import net.netshot.netshot.vault.VaultableSecret;

/**
 * HTTP credentials: a username (used for Basic auth) and a password, which
 * doubles as the Bearer token or apiKey value when the access's declared
 * authentication scheme (see {@code Http.AuthScheme}) isn't Basic - a single
 * credential set only ever serves one scheme (whichever the access
 * declares), so there is no need for a separate token field/column. The
 * driver never sees these values directly.
 */
@Entity
public class DeviceHttpAccount extends DeviceCredentialSet {

	/** The username (used for Basic auth only). Shares the "username" physical column (SINGLE_TABLE). */
	@Getter
	@Setter
	private String username;

	/** See {@code DeviceCliAccount.usernameVaultInstanceId} for why this isn't named "usernameVaultInstance". Shared with {@code DeviceCliAccount}/{@code DeviceSnmpv3Community}. */
	@Getter(onMethod = @__({
		@ManyToOne
	}))
	@Setter
	private VaultInstance usernameVaultInstanceId;

	@Getter(onMethod = @__({
		@Column(name = "username_vault_path")
	}))
	@Setter
	private String usernameVaultPath;

	private Long usernamePendingVaultInstanceId;

	/** The password - or, when the scheme is Bearer/apiKey, the token/key value. Shares the "password" physical column. */
	@Getter(onMethod = @__({
		@Convert(converter = StringEncryptorConverter.class)
	}))
	@Setter
	private String password;

	/** See {@code DeviceCliAccount.usernameVaultInstanceId} for why this isn't named "passwordVaultInstance". Shared with {@code DeviceCliAccount}. */
	@Getter(onMethod = @__({
		@ManyToOne
	}))
	@Setter
	private VaultInstance passwordVaultInstanceId;

	@Getter(onMethod = @__({
		@Column(name = "password_vault_path")
	}))
	@Setter
	private String passwordVaultPath;

	private Long passwordPendingVaultInstanceId;

	/**
	 * Instantiates a new device HTTP account.
	 */
	protected DeviceHttpAccount() {
		// Reserved for Hibernate
	}

	/**
	 * Instantiates a new device HTTP account.
	 *
	 * @param username the username (Basic auth only)
	 * @param password the password, or the Bearer/apiKey token value
	 * @param name the name
	 */
	public DeviceHttpAccount(String username, String password, String name) {
		super(name);
		this.username = username;
		this.password = password;
	}

	/**
	 * Accessor of the Vault instance ID, named differently in Java than the internal JPA getter (which returns the
	 * entity, not the ID).
	 * @return the Vault instance ID, or null if not Vault-backed
	 */
	@Transient
	public Long getResolvedUsernameVaultInstanceId() {
		if (this.usernamePendingVaultInstanceId != null) {
			return this.usernamePendingVaultInstanceId;
		}
		return this.usernameVaultInstanceId == null ? null : this.usernameVaultInstanceId.getId();
	}

	public void setResolvedUsernameVaultInstanceId(Long vaultInstanceId) {
		this.usernamePendingVaultInstanceId = vaultInstanceId;
	}

	/**
	 * Bundles the username's local/Vault state for {@link net.netshot.netshot.vault.VaultManager} and the REST layer. Not JPA-mapped.
	 * @return the bundled username secret
	 */
	@Transient
	public VaultableSecret getUsernameSecret() {
		VaultableSecret secret = new VaultableSecret();
		secret.setLocalValue(this.username);
		secret.setVaultInstance(this.usernameVaultInstanceId);
		if (this.usernamePendingVaultInstanceId != null) {
			secret.setVaultInstanceId(this.usernamePendingVaultInstanceId);
		}
		secret.setVaultPath(this.usernameVaultPath);
		return secret;
	}

	public void setUsernameSecret(VaultableSecret secret) {
		this.username = secret.getLocalValue();
		this.usernameVaultInstanceId = secret.getVaultInstance();
		this.usernameVaultPath = secret.getVaultPath();
		this.usernamePendingVaultInstanceId = null;
	}

	@Transient
	public Long getResolvedPasswordVaultInstanceId() {
		if (this.passwordPendingVaultInstanceId != null) {
			return this.passwordPendingVaultInstanceId;
		}
		return this.passwordVaultInstanceId == null ? null : this.passwordVaultInstanceId.getId();
	}

	public void setResolvedPasswordVaultInstanceId(Long vaultInstanceId) {
		this.passwordPendingVaultInstanceId = vaultInstanceId;
	}

	/**
	 * Bundles the password's local/Vault state for {@link net.netshot.netshot.vault.VaultManager} and the REST layer. Not JPA-mapped.
	 * @return the bundled password secret
	 */
	@Transient
	public VaultableSecret getPasswordSecret() {
		VaultableSecret secret = new VaultableSecret();
		secret.setLocalValue(this.password);
		secret.setVaultInstance(this.passwordVaultInstanceId);
		if (this.passwordPendingVaultInstanceId != null) {
			secret.setVaultInstanceId(this.passwordPendingVaultInstanceId);
		}
		secret.setVaultPath(this.passwordVaultPath);
		return secret;
	}

	public void setPasswordSecret(VaultableSecret secret) {
		this.password = secret.getLocalValue();
		this.passwordVaultInstanceId = secret.getVaultInstance();
		this.passwordVaultPath = secret.getVaultPath();
		this.passwordPendingVaultInstanceId = null;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (name == null ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof DeviceHttpAccount)) {
			return false;
		}
		DeviceHttpAccount other = (DeviceHttpAccount) obj;
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		}
		else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

}
