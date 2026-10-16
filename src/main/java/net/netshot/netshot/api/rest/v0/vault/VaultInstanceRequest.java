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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.credentials.HideSecretDeserializer;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;

/**
 * The request to add, edit or test a Vault instance, according to its kind (the {@code type} property).
 * The secrets read as {@code =} (unchanged) are {@code null}.
 *
 * <p>Kept as beans rather than records: several properties have a default value (the CA trust mode,
 * the mount paths) which applies when the property is absent, whereas an explicit {@code null} stays
 * {@code null}, which a bean with initialized fields does for free. The properties of the
 * entity which were never part of the representation (version, change date) are ignored.</p>
 */
// Transitional: with @XmlRootElement, Jersey reads a XML body through its JAXB provider, which fails
// (there is no JAXB runtime), as it always did for this request (see the API contract goldens).
@XmlRootElement(name = "vaultInstance")
@XmlAccessorType(XmlAccessType.NONE)
@JsonIgnoreProperties({"changeDate", "version"})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = VaultInstanceRequest.HashicorpVaultKv2.class, name = "Vault KV v2"),
})
public abstract class VaultInstanceRequest {

	/** The id (a Vault instance which has an ID can't be added). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private long id;

	/** The name. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String name;

	/** The base URL of the Vault server. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String baseUrl;

	/** The namespace (Vault Enterprise). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String namespace;

	/** How the certificate of the server is trusted. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private HttpsCaTrustMode httpsCaTrustMode = HttpsCaTrustMode.SYSTEM_TRUSTSTORE;

	/** The PEM trust anchors, for the custom CA mode. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String httpsCustomCaCertificate;

	/**
	 * A HashiCorp Vault instance using the KV v2 secrets engine.
	 */
	public static class HashicorpVaultKv2 extends VaultInstanceRequest {

		/** The mount path of the KV v2 secrets engine. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String kvMountPath = "secret";

		/** The authentication method. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private AuthMethod authMethod;

		/** The mount path of the AppRole authentication method. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String appRoleMountPath = "approle";

		/** The AppRole role ID. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String appRoleId;

		/** The AppRole secret ID ({@code null} when masked). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class),
			@JsonDeserialize(using = HideSecretDeserializer.class)
		}))
		@Setter
		private String appRoleSecretId;

		/** The mount path of the JWT authentication method. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String jwtMountPath = "jwt";

		/** The token endpoint of the identity provider. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String jwtIdpTokenEndpoint;

		/** The OAuth2 client ID. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String jwtClientId;

		/** The OAuth2 client secret ({@code null} when masked). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class),
			@JsonDeserialize(using = HideSecretDeserializer.class)
		}))
		@Setter
		private String jwtClientSecret;

		/** The Vault role to log in as. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String jwtVaultRole;

		/** The OAuth2 scope. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String jwtScope;
	}
}
