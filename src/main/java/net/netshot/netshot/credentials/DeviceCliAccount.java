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
 * A CLI account.
 */
@Entity
public abstract class DeviceCliAccount extends DeviceCredentialSet {

	/** The username. Not encrypted locally (matches historical behavior); may be Vault-backed. */
	@Getter
	@Setter
	private String username;

	/**
	 * Vault instance the username is read from, if Vault-backed.
	 * Field name deliberately ends in "VaultInstanceId" (not the cleaner
	 * "VaultInstance") so Hibernate's implicit naming (property name -> physical
	 * column, see {@code ImprovedImplicitNamingStrategy}/{@code ImprovedPhysicalNamingStrategy})
	 * derives "username_vault_instance_id", matching the migration - same
	 * pattern already relied on by {@code DeviceCredentialSet.mgmtDomain}. An
	 * explicit {@code @JoinColumn} here triggers a Hibernate 6 second-pass FK
	 * binding bug (duplicate logical/physical column registration between the
	 * explicit name and the implicit default it computes anyway). Shared with
	 * {@code DeviceSnmpv3Community}/{@code DeviceHttpAccount} (SINGLE_TABLE) -
	 * must stay named identically there too.
	 */
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

	/** Staged Vault instance ID from an incoming REST request, not yet resolved into {@link #usernameVaultInstanceId}. */
	private Long usernamePendingVaultInstanceId;

	/** The password. */
	@Getter(onMethod = @__({
		@Convert(converter = StringEncryptorConverter.class)
	}))
	@Setter
	private String password;

	/** See {@link #usernameVaultInstanceId} for why this isn't named "passwordVaultInstance". Shared with {@code DeviceHttpAccount}. */
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

	/** The super password. */
	@Getter(onMethod = @__({
		@Convert(converter = StringEncryptorConverter.class)
	}))
	@Setter
	private String superPassword;

	/** See {@link #usernameVaultInstanceId} for why this isn't named "superPasswordVaultInstance". */
	@Getter(onMethod = @__({
		@ManyToOne
	}))
	@Setter
	private VaultInstance superPasswordVaultInstanceId;

	@Getter(onMethod = @__({
		@Column(name = "super_password_vault_path")
	}))
	@Setter
	private String superPasswordVaultPath;

	private Long superPasswordPendingVaultInstanceId;

	/**
	 * Instantiates a new device cli account.
	 */
	protected DeviceCliAccount() {
		// Reserved for Hibernate
	}

	/**
	 * Instantiates a new device cli account.
	 *
	 * @param username the username
	 * @param password the password
	 * @param superPassword the super password
	 * @param name the name
	 */
	public DeviceCliAccount(String username, String password,
		String superPassword, String name) {
		super(name);
		this.username = username;
		this.password = password;
		this.superPassword = superPassword;
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

	@Transient
	public Long getResolvedSuperPasswordVaultInstanceId() {
		if (this.superPasswordPendingVaultInstanceId != null) {
			return this.superPasswordPendingVaultInstanceId;
		}
		return this.superPasswordVaultInstanceId == null ? null : this.superPasswordVaultInstanceId.getId();
	}

	public void setResolvedSuperPasswordVaultInstanceId(Long vaultInstanceId) {
		this.superPasswordPendingVaultInstanceId = vaultInstanceId;
	}

	/**
	 * Bundles the super password's local/Vault state for {@link net.netshot.netshot.vault.VaultManager} and the REST layer. Not JPA-mapped.
	 * @return the bundled super password secret
	 */
	@Transient
	public VaultableSecret getSuperPasswordSecret() {
		VaultableSecret secret = new VaultableSecret();
		secret.setLocalValue(this.superPassword);
		secret.setVaultInstance(this.superPasswordVaultInstanceId);
		if (this.superPasswordPendingVaultInstanceId != null) {
			secret.setVaultInstanceId(this.superPasswordPendingVaultInstanceId);
		}
		secret.setVaultPath(this.superPasswordVaultPath);
		return secret;
	}

	public void setSuperPasswordSecret(VaultableSecret secret) {
		this.superPassword = secret.getLocalValue();
		this.superPasswordVaultInstanceId = secret.getVaultInstance();
		this.superPasswordVaultPath = secret.getVaultPath();
		this.superPasswordPendingVaultInstanceId = null;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.credentials.DeviceCredentialSet#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (name == null ? 0 : name.hashCode());
		return result;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.credentials.DeviceCredentialSet#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof DeviceCliAccount)) {
			return false;
		}
		DeviceCliAccount other = (DeviceCliAccount) obj;
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
