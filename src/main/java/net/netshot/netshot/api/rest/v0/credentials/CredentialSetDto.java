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
package net.netshot.netshot.api.rest.v0.credentials;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.api.rest.v0.domain.DomainDto;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.AuthProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.PrivProtocol;
import net.netshot.netshot.credentials.HideSecretDeserializer;
import net.netshot.netshot.credentials.HideSecretSerializer;

/**
 * A credential set: the representation of the credential set endpoints, and of the credential sets
 * nested in the device accesses (the global one the access is pinned to, or the one specific to the
 * access). The same shape is written and read.
 *
 * <p>The {@code type} property tells the kind of credential set. The secrets are never written
 * ({@code =} stands for them), and {@code =} read as a secret means "unchanged". A secret
 * can alternatively be held by a Vault instance (ID and path of the secret).</p>
 *
 * <p>Every subtype repeats the common components (a record can't extend another one, and
 * {@code @JsonUnwrapped} doesn't work on the properties which are read): {@code id}, {@code name},
 * {@code mgmtDomain} and {@code deviceSpecific}.</p>
 *
 * <p>When read, the properties of the entities which were never part of the representation
 * (the version and change date, the Vault bundles of the secrets) are ignored if present, like they
 * always were; any other unknown property is refused.</p>
 */
@JsonIgnoreProperties({
	"changeDate", "version", "usernameSecret", "passwordSecret", "superPasswordSecret", "privateKeySecret",
	"communitySecret", "authKeySecret", "privKeySecret"
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = CredentialSetDto.SnmpV1.class, name = "SNMP v1"),
	@JsonSubTypes.Type(value = CredentialSetDto.SnmpV2c.class, name = "SNMP v2"),
	@JsonSubTypes.Type(value = CredentialSetDto.SnmpV3.class, name = "SNMP v3"),
	@JsonSubTypes.Type(value = CredentialSetDto.Ssh.class, name = "SSH"),
	@JsonSubTypes.Type(value = CredentialSetDto.SshKey.class, name = "SSH Key"),
	@JsonSubTypes.Type(value = CredentialSetDto.Telnet.class, name = "Telnet"),
	@JsonSubTypes.Type(value = CredentialSetDto.Http.class, name = "HTTP"),
})
public sealed interface CredentialSetDto {

	/**
	 * Gets the credential set ID.
	 *
	 * @return the ID
	 */
	long id();

	/**
	 * Gets the name of the credential set.
	 *
	 * @return the name
	 */
	String name();

	/**
	 * Gets the domain the credential set is restricted to.
	 *
	 * @return the domain, or null if it is not restricted
	 */
	DomainDto mgmtDomain();

	/**
	 * Tells whether the credential set belongs to a single device access.
	 *
	 * @return true if the credential set is specific to a device access
	 */
	boolean deviceSpecific();

	/**
	 * A SNMP v1 community.
	 *
	 * @param id the credential set ID
	 * @param name the name
	 * @param mgmtDomain the domain
	 * @param deviceSpecific whether it is specific to a device access
	 * @param community the community (masked)
	 * @param communityVaultInstanceId the ID of the Vault instance holding the community
	 * @param communityVaultPath the path of the community in the Vault instance
	 */
	@JacksonXmlRootElement(localName = "DeviceSnmpv1Community")
	record SnmpV1(
		long id,
		String name,
		DomainDto mgmtDomain,
		boolean deviceSpecific,
		@JsonSerialize(using = HideSecretSerializer.class) @JsonDeserialize(using = HideSecretDeserializer.class) String community,
		Long communityVaultInstanceId,
		String communityVaultPath) implements CredentialSetDto {
	}

	/**
	 * A SNMP v2c community.
	 *
	 * @param id the credential set ID
	 * @param name the name
	 * @param mgmtDomain the domain
	 * @param deviceSpecific whether it is specific to a device access
	 * @param community the community (masked)
	 * @param communityVaultInstanceId the ID of the Vault instance holding the community
	 * @param communityVaultPath the path of the community in the Vault instance
	 */
	@JacksonXmlRootElement(localName = "DeviceSnmpv2cCommunity")
	record SnmpV2c(
		long id,
		String name,
		DomainDto mgmtDomain,
		boolean deviceSpecific,
		@JsonSerialize(using = HideSecretSerializer.class) @JsonDeserialize(using = HideSecretDeserializer.class) String community,
		Long communityVaultInstanceId,
		String communityVaultPath) implements CredentialSetDto {
	}

	/**
	 * A SNMP v3 account.
	 *
	 * @param id the credential set ID
	 * @param name the name
	 * @param mgmtDomain the domain
	 * @param deviceSpecific whether it is specific to a device access
	 * @param community the context community (masked)
	 * @param communityVaultInstanceId the ID of the Vault instance holding the community
	 * @param communityVaultPath the path of the community in the Vault instance
	 * @param username the username
	 * @param usernameVaultInstanceId the ID of the Vault instance holding the username
	 * @param usernameVaultPath the path of the username in the Vault instance
	 * @param authType the authentication protocol
	 * @param authKey the authentication key (masked)
	 * @param authKeyVaultInstanceId the ID of the Vault instance holding the authentication key
	 * @param authKeyVaultPath the path of the authentication key in the Vault instance
	 * @param privType the privacy protocol
	 * @param privKey the privacy key (masked)
	 * @param privKeyVaultInstanceId the ID of the Vault instance holding the privacy key
	 * @param privKeyVaultPath the path of the privacy key in the Vault instance
	 */
	@JacksonXmlRootElement(localName = "DeviceSnmpv3Community")
	record SnmpV3(
		long id,
		String name,
		DomainDto mgmtDomain,
		boolean deviceSpecific,
		@JsonSerialize(using = HideSecretSerializer.class) @JsonDeserialize(using = HideSecretDeserializer.class) String community,
		Long communityVaultInstanceId,
		String communityVaultPath,
		String username,
		Long usernameVaultInstanceId,
		String usernameVaultPath,
		AuthProtocol authType,
		@JsonSerialize(using = HideSecretSerializer.class) @JsonDeserialize(using = HideSecretDeserializer.class) String authKey,
		Long authKeyVaultInstanceId,
		String authKeyVaultPath,
		PrivProtocol privType,
		@JsonSerialize(using = HideSecretSerializer.class) @JsonDeserialize(using = HideSecretDeserializer.class) String privKey,
		Long privKeyVaultInstanceId,
		String privKeyVaultPath) implements CredentialSetDto {
	}

	/**
	 * A SSH account.
	 *
	 * @param id the credential set ID
	 * @param name the name
	 * @param mgmtDomain the domain
	 * @param deviceSpecific whether it is specific to a device access
	 * @param username the username
	 * @param usernameVaultInstanceId the ID of the Vault instance holding the username
	 * @param usernameVaultPath the path of the username in the Vault instance
	 * @param password the password (masked)
	 * @param passwordVaultInstanceId the ID of the Vault instance holding the password
	 * @param passwordVaultPath the path of the password in the Vault instance
	 * @param superPassword the super (enable) password (masked)
	 * @param superPasswordVaultInstanceId the ID of the Vault instance holding the super password
	 * @param superPasswordVaultPath the path of the super password in the Vault instance
	 */
	@JacksonXmlRootElement(localName = "DeviceSshAccount")
	record Ssh(
		long id,
		String name,
		DomainDto mgmtDomain,
		boolean deviceSpecific,
		String username,
		Long usernameVaultInstanceId,
		String usernameVaultPath,
		@JsonSerialize(using = HideSecretSerializer.class) @JsonDeserialize(using = HideSecretDeserializer.class) String password,
		Long passwordVaultInstanceId,
		String passwordVaultPath,
		@JsonSerialize(using = HideSecretSerializer.class) @JsonDeserialize(using = HideSecretDeserializer.class) String superPassword,
		Long superPasswordVaultInstanceId,
		String superPasswordVaultPath) implements CredentialSetDto {
	}

	/**
	 * A SSH account using a private key.
	 *
	 * @param id the credential set ID
	 * @param name the name
	 * @param mgmtDomain the domain
	 * @param deviceSpecific whether it is specific to a device access
	 * @param username the username
	 * @param usernameVaultInstanceId the ID of the Vault instance holding the username
	 * @param usernameVaultPath the path of the username in the Vault instance
	 * @param password the password (masked)
	 * @param passwordVaultInstanceId the ID of the Vault instance holding the password
	 * @param passwordVaultPath the path of the password in the Vault instance
	 * @param superPassword the super (enable) password (masked)
	 * @param superPasswordVaultInstanceId the ID of the Vault instance holding the super password
	 * @param superPasswordVaultPath the path of the super password in the Vault instance
	 * @param privateKey the private key (masked)
	 * @param privateKeyVaultInstanceId the ID of the Vault instance holding the private key
	 * @param privateKeyVaultPath the path of the private key in the Vault instance
	 */
	@JacksonXmlRootElement(localName = "DeviceSshKeyAccount")
	record SshKey(
		long id,
		String name,
		DomainDto mgmtDomain,
		boolean deviceSpecific,
		String username,
		Long usernameVaultInstanceId,
		String usernameVaultPath,
		@JsonSerialize(using = HideSecretSerializer.class) @JsonDeserialize(using = HideSecretDeserializer.class) String password,
		Long passwordVaultInstanceId,
		String passwordVaultPath,
		@JsonSerialize(using = HideSecretSerializer.class) @JsonDeserialize(using = HideSecretDeserializer.class) String superPassword,
		Long superPasswordVaultInstanceId,
		String superPasswordVaultPath,
		@JsonSerialize(using = HideSecretSerializer.class) @JsonDeserialize(using = HideSecretDeserializer.class) String privateKey,
		Long privateKeyVaultInstanceId,
		String privateKeyVaultPath) implements CredentialSetDto {
	}

	/**
	 * A Telnet account.
	 *
	 * @param id the credential set ID
	 * @param name the name
	 * @param mgmtDomain the domain
	 * @param deviceSpecific whether it is specific to a device access
	 * @param username the username
	 * @param usernameVaultInstanceId the ID of the Vault instance holding the username
	 * @param usernameVaultPath the path of the username in the Vault instance
	 * @param password the password (masked)
	 * @param passwordVaultInstanceId the ID of the Vault instance holding the password
	 * @param passwordVaultPath the path of the password in the Vault instance
	 * @param superPassword the super (enable) password (masked)
	 * @param superPasswordVaultInstanceId the ID of the Vault instance holding the super password
	 * @param superPasswordVaultPath the path of the super password in the Vault instance
	 */
	@JacksonXmlRootElement(localName = "DeviceTelnetAccount")
	record Telnet(
		long id,
		String name,
		DomainDto mgmtDomain,
		boolean deviceSpecific,
		String username,
		Long usernameVaultInstanceId,
		String usernameVaultPath,
		@JsonSerialize(using = HideSecretSerializer.class) @JsonDeserialize(using = HideSecretDeserializer.class) String password,
		Long passwordVaultInstanceId,
		String passwordVaultPath,
		@JsonSerialize(using = HideSecretSerializer.class) @JsonDeserialize(using = HideSecretDeserializer.class) String superPassword,
		Long superPasswordVaultInstanceId,
		String superPasswordVaultPath) implements CredentialSetDto {
	}

	/**
	 * A HTTP account.
	 *
	 * @param id the credential set ID
	 * @param name the name
	 * @param mgmtDomain the domain
	 * @param deviceSpecific whether it is specific to a device access
	 * @param username the username
	 * @param usernameVaultInstanceId the ID of the Vault instance holding the username
	 * @param usernameVaultPath the path of the username in the Vault instance
	 * @param password the password (masked)
	 * @param passwordVaultInstanceId the ID of the Vault instance holding the password
	 * @param passwordVaultPath the path of the password in the Vault instance
	 */
	@JacksonXmlRootElement(localName = "DeviceHttpAccount")
	record Http(
		long id,
		String name,
		DomainDto mgmtDomain,
		boolean deviceSpecific,
		String username,
		Long usernameVaultInstanceId,
		String usernameVaultPath,
		@JsonSerialize(using = HideSecretSerializer.class) @JsonDeserialize(using = HideSecretDeserializer.class) String password,
		Long passwordVaultInstanceId,
		String passwordVaultPath) implements CredentialSetDto {
	}
}
