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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.platform.database.StringEncryptorConverter;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.vault.VaultInstance;
import net.netshot.netshot.vault.VaultableSecret;


/**
 * The Class DeviceSnmpv3Community.
 */
@Entity
@XmlRootElement
public final class DeviceSnmpv3Community extends DeviceSnmpCommunity {

	/**
	 * SNMPv3 authentication protocol. {@link #NONE} means noAuth (and,
	 * transitively, noPriv - privacy requires authentication). SHA-2 variants
	 * (RFC 7860) are named after their snmp4j class (e.g. {@code HMAC192SHA256}),
	 * matching the naming already used for the SNMP trap receiver's user config.
	 */
	public enum AuthProtocol {
		NONE,
		MD5,
		SHA,
		HMAC128SHA224,
		HMAC192SHA256,
		HMAC256SHA384,
		HMAC384SHA512,
	}

	/**
	 * SNMPv3 privacy (encryption) protocol. {@link #NONE} means noPriv.
	 */
	public enum PrivProtocol {
		NONE,
		DES,
		DES3,
		AES128,
		AES192,
		AES256,
	}

	/** The username. Shares the "username" physical column with DeviceCliAccount (SINGLE_TABLE). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String username;

	/**
	 * Shares the "username_vault_instance_id" physical column with DeviceCliAccount.
	 * Field name deliberately ends in "VaultInstanceId" (not the cleaner
	 * "VaultInstance") so Hibernate's implicit naming (property name -> physical
	 * column) derives that exact column name, matching the migration - same
	 * pattern already relied on by {@code DeviceCredentialSet.mgmtDomain}. An
	 * explicit {@code @JoinColumn} here triggers a Hibernate 6 second-pass FK
	 * binding bug (duplicate logical/physical column registration between the
	 * explicit name and the implicit default it computes anyway).
	 */
	@Getter(onMethod = @__({
		@ManyToOne,
		@JsonIgnore
	}))
	@Setter
	private VaultInstance usernameVaultInstanceId;

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@Column(name = "username_vault_path")
	}))
	@Setter
	private String usernameVaultPath;

	private Long usernamePendingVaultInstanceId;

	/** The auth type. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@Enumerated(EnumType.STRING)
	}))
	@Setter
	private AuthProtocol authType;

	/** The auth key. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@JsonSerialize(using = HideSecretSerializer.class),
		@JsonDeserialize(using = HideSecretDeserializer.class),
		@Convert(converter = StringEncryptorConverter.class)
	}))
	@Setter
	private String authKey;

	/** See {@link #usernameVaultInstanceId} for why this isn't named "authKeyVaultInstance". */
	@Getter(onMethod = @__({
		@ManyToOne,
		@JsonIgnore
	}))
	@Setter
	private VaultInstance authKeyVaultInstanceId;

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@Column(name = "auth_key_vault_path")
	}))
	@Setter
	private String authKeyVaultPath;

	private Long authKeyPendingVaultInstanceId;

	/** The priv type. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@Enumerated(EnumType.STRING)
	}))
	@Setter
	private PrivProtocol privType;

	/** The priv key. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@JsonSerialize(using = HideSecretSerializer.class),
		@JsonDeserialize(using = HideSecretDeserializer.class),
		@Convert(converter = StringEncryptorConverter.class)
	}))
	@Setter
	private String privKey;

	/** See {@link #usernameVaultInstanceId} for why this isn't named "privKeyVaultInstance". */
	@Getter(onMethod = @__({
		@ManyToOne,
		@JsonIgnore
	}))
	@Setter
	private VaultInstance privKeyVaultInstanceId;

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@Column(name = "priv_key_vault_path")
	}))
	@Setter
	private String privKeyVaultPath;

	private Long privKeyPendingVaultInstanceId;

	/**
	 * Instantiates a new device snmpv 3 community.
	 */
	protected DeviceSnmpv3Community() {

	}

	/**
	 * Instantiates a new device snmpv 3 community.
	 *
	 * @param community the community
	 * @param name the name
	 * @param username the username
	 * @param authType the auth type
	 * @param authKey the auth key
	 * @param privType the priv type
	 * @param privKey the priv key
	 */
	public DeviceSnmpv3Community(String community, String name, String username, AuthProtocol authType,
		String authKey, PrivProtocol privType, String privKey) {
		super(community, name);
		this.username = username;
		this.authType = authType;
		this.authKey = authKey;
		this.privType = privType;
		this.privKey = privKey;
	}

	/**
	 * JSON/XML-facing accessor - see {@link DeviceSnmpCommunity#getResolvedCommunityVaultInstanceId()} for why this is named differently from the internal JPA getter.
	 * @return the Vault instance ID, or null if not Vault-backed
	 */
	@XmlElement(name = "usernameVaultInstanceId")
	@JsonView(DefaultView.class)
	@JsonProperty("usernameVaultInstanceId")
	@Transient
	public Long getResolvedUsernameVaultInstanceId() {
		if (this.usernamePendingVaultInstanceId != null) {
			return this.usernamePendingVaultInstanceId;
		}
		return this.usernameVaultInstanceId == null ? null : this.usernameVaultInstanceId.getId();
	}

	@JsonProperty("usernameVaultInstanceId")
	public void setResolvedUsernameVaultInstanceId(Long vaultInstanceId) {
		this.usernamePendingVaultInstanceId = vaultInstanceId;
	}

	/**
	 * Bundles the username's local/Vault state for {@link net.netshot.netshot.vault.VaultManager} and the REST layer. Not JPA-mapped, not Jackson-visible.
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

	@XmlElement(name = "authKeyVaultInstanceId")
	@JsonView(DefaultView.class)
	@JsonProperty("authKeyVaultInstanceId")
	@Transient
	public Long getResolvedAuthKeyVaultInstanceId() {
		if (this.authKeyPendingVaultInstanceId != null) {
			return this.authKeyPendingVaultInstanceId;
		}
		return this.authKeyVaultInstanceId == null ? null : this.authKeyVaultInstanceId.getId();
	}

	@JsonProperty("authKeyVaultInstanceId")
	public void setResolvedAuthKeyVaultInstanceId(Long vaultInstanceId) {
		this.authKeyPendingVaultInstanceId = vaultInstanceId;
	}

	/**
	 * Bundles the auth key's local/Vault state for {@link net.netshot.netshot.vault.VaultManager} and the REST layer. Not JPA-mapped, not Jackson-visible.
	 * @return the bundled auth key secret
	 */
	@Transient
	public VaultableSecret getAuthKeySecret() {
		VaultableSecret secret = new VaultableSecret();
		secret.setLocalValue(this.authKey);
		secret.setVaultInstance(this.authKeyVaultInstanceId);
		if (this.authKeyPendingVaultInstanceId != null) {
			secret.setVaultInstanceId(this.authKeyPendingVaultInstanceId);
		}
		secret.setVaultPath(this.authKeyVaultPath);
		return secret;
	}

	public void setAuthKeySecret(VaultableSecret secret) {
		this.authKey = secret.getLocalValue();
		this.authKeyVaultInstanceId = secret.getVaultInstance();
		this.authKeyVaultPath = secret.getVaultPath();
		this.authKeyPendingVaultInstanceId = null;
	}

	@XmlElement(name = "privKeyVaultInstanceId")
	@JsonView(DefaultView.class)
	@JsonProperty("privKeyVaultInstanceId")
	@Transient
	public Long getResolvedPrivKeyVaultInstanceId() {
		if (this.privKeyPendingVaultInstanceId != null) {
			return this.privKeyPendingVaultInstanceId;
		}
		return this.privKeyVaultInstanceId == null ? null : this.privKeyVaultInstanceId.getId();
	}

	@JsonProperty("privKeyVaultInstanceId")
	public void setResolvedPrivKeyVaultInstanceId(Long vaultInstanceId) {
		this.privKeyPendingVaultInstanceId = vaultInstanceId;
	}

	/**
	 * Bundles the priv key's local/Vault state for {@link net.netshot.netshot.vault.VaultManager} and the REST layer. Not JPA-mapped, not Jackson-visible.
	 * @return the bundled priv key secret
	 */
	@Transient
	public VaultableSecret getPrivKeySecret() {
		VaultableSecret secret = new VaultableSecret();
		secret.setLocalValue(this.privKey);
		secret.setVaultInstance(this.privKeyVaultInstanceId);
		if (this.privKeyPendingVaultInstanceId != null) {
			secret.setVaultInstanceId(this.privKeyPendingVaultInstanceId);
		}
		secret.setVaultPath(this.privKeyVaultPath);
		return secret;
	}

	public void setPrivKeySecret(VaultableSecret secret) {
		this.privKey = secret.getLocalValue();
		this.privKeyVaultInstanceId = secret.getVaultInstance();
		this.privKeyVaultPath = secret.getVaultPath();
		this.privKeyPendingVaultInstanceId = null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see net.netshot.netshot.credentials.DeviceSnmpCommunity#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (name == null ? 0 : name.hashCode());
		return result;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * net.netshot.netshot.credentials.DeviceSnmpCommunity#equals(java.
	 * lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof DeviceSnmpv3Community)) {
			return false;
		}
		DeviceSnmpv3Community other = (DeviceSnmpv3Community) obj;
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
