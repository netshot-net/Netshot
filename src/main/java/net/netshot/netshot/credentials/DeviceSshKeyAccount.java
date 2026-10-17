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
 * SSH credentials with a private key.
 * The inherited password is actually the passphrase for the key.
 * @author sylv
 *
 */
@Entity
public class DeviceSshKeyAccount extends DeviceSshAccount {

	@Getter(onMethod = @__({
		@Column(length = 5000),
		@Convert(converter = StringEncryptorConverter.class)
	}))
	@Setter
	private String privateKey;

	/** See {@code DeviceCliAccount.usernameVaultInstanceId} for why this isn't named "privateKeyVaultInstance". */
	@Getter(onMethod = @__({
		@ManyToOne
	}))
	@Setter
	private VaultInstance privateKeyVaultInstanceId;

	@Getter(onMethod = @__({
		@Column(name = "private_key_vault_path")
	}))
	@Setter
	private String privateKeyVaultPath;

	private Long privateKeyPendingVaultInstanceId;

	protected DeviceSshKeyAccount() {

	}

	public DeviceSshKeyAccount(String username, String privateKey, String passphrase,
		String superPassword, String name) {
		super(username, passphrase, superPassword, name);
		this.privateKey = privateKey;
	}

	/**
	 * Accessor of the Vault instance ID, named differently in Java than the internal JPA getter (which returns the
	 * entity, not the ID).
	 * @return the Vault instance ID, or null if not Vault-backed
	 */
	@Transient
	public Long getResolvedPrivateKeyVaultInstanceId() {
		if (this.privateKeyPendingVaultInstanceId != null) {
			return this.privateKeyPendingVaultInstanceId;
		}
		return this.privateKeyVaultInstanceId == null ? null : this.privateKeyVaultInstanceId.getId();
	}

	public void setResolvedPrivateKeyVaultInstanceId(Long vaultInstanceId) {
		this.privateKeyPendingVaultInstanceId = vaultInstanceId;
	}

	/**
	 * Bundles the private key's local/Vault state for {@link net.netshot.netshot.vault.VaultManager} and the REST layer. Not JPA-mapped.
	 * @return the bundled private key secret
	 */
	@Transient
	public VaultableSecret getPrivateKeySecret() {
		VaultableSecret secret = new VaultableSecret();
		secret.setLocalValue(this.privateKey);
		secret.setVaultInstance(this.privateKeyVaultInstanceId);
		if (this.privateKeyPendingVaultInstanceId != null) {
			secret.setVaultInstanceId(this.privateKeyPendingVaultInstanceId);
		}
		secret.setVaultPath(this.privateKeyVaultPath);
		return secret;
	}

	public void setPrivateKeySecret(VaultableSecret secret) {
		this.privateKey = secret.getLocalValue();
		this.privateKeyVaultInstanceId = secret.getVaultInstance();
		this.privateKeyVaultPath = secret.getVaultPath();
		this.privateKeyPendingVaultInstanceId = null;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
			+ (privateKey == null ? 0 : privateKey.hashCode());
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
		if (getClass() != obj.getClass()) {
			return false;
		}
		DeviceSshKeyAccount other = (DeviceSshKeyAccount) obj;
		if (privateKey == null) {
			if (other.privateKey != null) {
				return false;
			}
		}
		else if (!privateKey.equals(other.privateKey)) {
			return false;
		}
		return true;
	}


}
