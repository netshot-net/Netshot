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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.platform.database.StringEncryptorConverter;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.vault.VaultInstance;
import net.netshot.netshot.vault.VaultableSecret;


/**
 * A SNMP community to poll a device.
 */
@Entity
public abstract class DeviceSnmpCommunity extends DeviceCredentialSet {

	/** The community. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@JsonSerialize(using = HideSecretSerializer.class),
		@JsonDeserialize(using = HideSecretDeserializer.class),
		@Convert(converter = StringEncryptorConverter.class)
	}))
	@Setter
	private String community;

	/**
	 * The Vault instance the community is read from, if Vault-backed.
	 * Field name deliberately ends in "VaultInstanceId" (not the cleaner
	 * "VaultInstance") so Hibernate's implicit naming (property name ->
	 * physical column, see {@code ImprovedImplicitNamingStrategy}/
	 * {@code ImprovedPhysicalNamingStrategy}) derives "community_vault_instance_id",
	 * matching the migration - same pattern already relied on by
	 * {@code DeviceCredentialSet.mgmtDomain}. An explicit {@code @JoinColumn}
	 * here triggers a Hibernate 6 second-pass FK binding bug (duplicate
	 * logical/physical column registration between the explicit name and the
	 * implicit default it computes anyway).
	 */
	@Getter(onMethod = @__({
		@ManyToOne,
		@JsonIgnore
	}))
	@Setter
	private VaultInstance communityVaultInstanceId;

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@Column(name = "community_vault_path")
	}))
	@Setter
	private String communityVaultPath;

	private Long communityPendingVaultInstanceId;

	/**
	 * Instantiates a new device snmp community.
	 */
	protected DeviceSnmpCommunity() {

	}

	/**
	 * Instantiates a new device snmp community.
	 *
	 * @param community the community
	 * @param name the name
	 */
	public DeviceSnmpCommunity(String community, String name) {
		super(name);
		this.community = community;
	}

	/**
	 * JSON/XML-facing accessor - named differently in Java than the internal
	 * {@link #getCommunityVaultInstanceId()} JPA getter (which returns the
	 * entity, not the ID), but mapped to the same wire property name via
	 * {@code @JsonProperty} so the API contract is unaffected.
	 * @return the Vault instance ID, or null if not Vault-backed
	 */
	@XmlElement(name = "communityVaultInstanceId")
	@JsonView(DefaultView.class)
	@JsonProperty("communityVaultInstanceId")
	@Transient
	public Long getResolvedCommunityVaultInstanceId() {
		if (this.communityPendingVaultInstanceId != null) {
			return this.communityPendingVaultInstanceId;
		}
		return this.communityVaultInstanceId == null ? null : this.communityVaultInstanceId.getId();
	}

	@JsonProperty("communityVaultInstanceId")
	public void setResolvedCommunityVaultInstanceId(Long vaultInstanceId) {
		this.communityPendingVaultInstanceId = vaultInstanceId;
	}

	/**
	 * Bundles the community's local/Vault state for {@link net.netshot.netshot.vault.VaultManager} and the REST layer. Not JPA-mapped, not Jackson-visible.
	 * @return the bundled community secret
	 */
	@Transient
	public VaultableSecret getCommunitySecret() {
		VaultableSecret secret = new VaultableSecret();
		secret.setLocalValue(this.community);
		secret.setVaultInstance(this.communityVaultInstanceId);
		if (this.communityPendingVaultInstanceId != null) {
			secret.setVaultInstanceId(this.communityPendingVaultInstanceId);
		}
		secret.setVaultPath(this.communityVaultPath);
		return secret;
	}

	public void setCommunitySecret(VaultableSecret secret) {
		this.community = secret.getLocalValue();
		this.communityVaultInstanceId = secret.getVaultInstance();
		this.communityVaultPath = secret.getVaultPath();
		this.communityPendingVaultInstanceId = null;
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
		if (!(obj instanceof DeviceSnmpCommunity)) {
			return false;
		}
		DeviceSnmpCommunity other = (DeviceSnmpCommunity) obj;
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
