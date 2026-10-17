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

import java.util.Date;

import org.hibernate.annotations.NaturalId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * A named external Vault instance, used to resolve device credential fields
 * that are configured to be Vault-backed rather than stored locally.
 * <p>
 * Modeled as a discriminated hierarchy (same pattern as
 * {@link net.netshot.netshot.credentials.DeviceCredentialSet}), with
 * a single concrete type implemented today ({@link HashicorpVaultKv2Instance}),
 * so other Vault backend types can be added later without redesign.
 */
@Entity
@Table(name = "vault_instance")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class VaultInstance {

	/** The change date. */
	@Getter
	@Setter
	protected Date changeDate;

	@Getter(onMethod = @__({
		@Version
	}))
	@Setter
	private int version;

	/** The id. */
	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)
	}))
	@Setter
	protected long id;

	/** The name. */
	@Getter(onMethod = @__({
		@NaturalId(mutable = true)
	}))
	@Setter
	protected String name;

	/** The Vault instance base URL (e.g. https://vault.example.com:8200). */
	@Getter(onMethod = @__({
		@Column(name = "base_url", nullable = false)
	}))
	@Setter
	protected String baseUrl;

	/** Optional Vault Enterprise namespace. */
	@Getter
	@Setter
	protected String namespace;

	/** HTTPS certificate CA trust mode for the connection to this Vault instance. */
	@Getter(onMethod = @__({
		@Enumerated(EnumType.STRING),
		@Column(name = "https_ca_trust_mode", nullable = false)
	}))
	@Setter
	protected HttpsCaTrustMode httpsCaTrustMode = HttpsCaTrustMode.SYSTEM_TRUSTSTORE;

	/**
	 * PEM-encoded trust anchor certificate (optionally a chain of several
	 * concatenated PEM certificates) used when {@link #httpsCaTrustMode} is
	 * {@link HttpsCaTrustMode#CUSTOM_CA}.
	 */
	@Getter(onMethod = @__({
		@Column(name = "https_custom_ca_certificate", columnDefinition = "TEXT")
	}))
	@Setter
	protected String httpsCustomCaCertificate;

	/**
	 * Instantiates a new Vault instance.
	 */
	protected VaultInstance() {
		// Reserved for Hibernate
	}

	/**
	 * Instantiates a new Vault instance.
	 * @param name the name
	 */
	public VaultInstance(String name) {
		this.name = name;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof VaultInstance)) {
			return false;
		}
		VaultInstance other = (VaultInstance) obj;
		if (name == null) {
			return other.name == null;
		}
		return name.equals(other.name);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (name == null ? 0 : name.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return "Vault Instance " + id + " (name '" + name + "', type " + this.getClass().getSimpleName() + ")";
	}

}
