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
package net.netshot.netshot.api.hook.payload;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.credentials.DeviceSnmpv3Community.AuthProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.PrivProtocol;
import net.netshot.netshot.platform.rest.RestViews.HookView;

/**
 * A credential set, as nested in the device accesses of the webhook payloads. The {@code type}
 * property tells the kind of credential set.
 *
 * <p>The webhook payloads never carry the secrets: where there is one, it is written as {@code =},
 * and the payload is never given the secret itself. The CLI and HTTP accounts only carry the
 * properties common to every credential set.</p>
 */
@JsonView(HookView.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = CredentialSetPayload.SnmpV1.class, name = "SNMP v1"),
	@JsonSubTypes.Type(value = CredentialSetPayload.SnmpV2c.class, name = "SNMP v2"),
	@JsonSubTypes.Type(value = CredentialSetPayload.SnmpV3.class, name = "SNMP v3"),
	@JsonSubTypes.Type(value = CredentialSetPayload.Ssh.class, name = "SSH"),
	@JsonSubTypes.Type(value = CredentialSetPayload.SshKey.class, name = "SSH Key"),
	@JsonSubTypes.Type(value = CredentialSetPayload.Telnet.class, name = "Telnet"),
	@JsonSubTypes.Type(value = CredentialSetPayload.Http.class, name = "HTTP"),
})
public sealed interface CredentialSetPayload {

	/**
	 * A SNMP v1 community.
	 *
	 * @param id the credential set ID
	 * @param name the name
	 * @param mgmtDomain the domain
	 * @param deviceSpecific whether it is specific to a device access
	 * @param community the community (masked, null if none)
	 * @param communityVaultInstanceId the ID of the Vault instance holding the community
	 * @param communityVaultPath the path of the community in the Vault instance
	 */
	@JsonView(HookView.class)
	@JacksonXmlRootElement(localName = "DeviceSnmpv1Community")
	record SnmpV1(
		long id,
		String name,
		DomainPayload mgmtDomain,
		boolean deviceSpecific,
		String community,
		Long communityVaultInstanceId,
		String communityVaultPath) implements CredentialSetPayload {
	}

	/**
	 * A SNMP v2c community.
	 *
	 * @param id the credential set ID
	 * @param name the name
	 * @param mgmtDomain the domain
	 * @param deviceSpecific whether it is specific to a device access
	 * @param community the community (masked, null if none)
	 * @param communityVaultInstanceId the ID of the Vault instance holding the community
	 * @param communityVaultPath the path of the community in the Vault instance
	 */
	@JsonView(HookView.class)
	@JacksonXmlRootElement(localName = "DeviceSnmpv2cCommunity")
	record SnmpV2c(
		long id,
		String name,
		DomainPayload mgmtDomain,
		boolean deviceSpecific,
		String community,
		Long communityVaultInstanceId,
		String communityVaultPath) implements CredentialSetPayload {
	}

	/**
	 * A SNMP v3 account.
	 *
	 * @param id the credential set ID
	 * @param name the name
	 * @param mgmtDomain the domain
	 * @param deviceSpecific whether it is specific to a device access
	 * @param community the context community (masked, null if none)
	 * @param communityVaultInstanceId the ID of the Vault instance holding the community
	 * @param communityVaultPath the path of the community in the Vault instance
	 * @param username the username
	 * @param usernameVaultInstanceId the ID of the Vault instance holding the username
	 * @param usernameVaultPath the path of the username in the Vault instance
	 * @param authType the authentication protocol
	 * @param authKey the authentication key (masked, null if none)
	 * @param authKeyVaultInstanceId the ID of the Vault instance holding the authentication key
	 * @param authKeyVaultPath the path of the authentication key in the Vault instance
	 * @param privType the privacy protocol
	 * @param privKey the privacy key (masked, null if none)
	 * @param privKeyVaultInstanceId the ID of the Vault instance holding the privacy key
	 * @param privKeyVaultPath the path of the privacy key in the Vault instance
	 */
	@JsonView(HookView.class)
	@JacksonXmlRootElement(localName = "DeviceSnmpv3Community")
	record SnmpV3(
		long id,
		String name,
		DomainPayload mgmtDomain,
		boolean deviceSpecific,
		String community,
		Long communityVaultInstanceId,
		String communityVaultPath,
		String username,
		Long usernameVaultInstanceId,
		String usernameVaultPath,
		AuthProtocol authType,
		String authKey,
		Long authKeyVaultInstanceId,
		String authKeyVaultPath,
		PrivProtocol privType,
		String privKey,
		Long privKeyVaultInstanceId,
		String privKeyVaultPath) implements CredentialSetPayload {
	}

	/**
	 * A SSH account.
	 *
	 * @param id the credential set ID
	 * @param name the name
	 * @param mgmtDomain the domain
	 * @param deviceSpecific whether it is specific to a device access
	 */
	@JsonView(HookView.class)
	@JacksonXmlRootElement(localName = "DeviceSshAccount")
	record Ssh(
		long id,
		String name,
		DomainPayload mgmtDomain,
		boolean deviceSpecific) implements CredentialSetPayload {
	}

	/**
	 * A SSH account using a private key.
	 *
	 * @param id the credential set ID
	 * @param name the name
	 * @param mgmtDomain the domain
	 * @param deviceSpecific whether it is specific to a device access
	 * @param privateKey the private key (masked, null if none)
	 * @param privateKeyVaultInstanceId the ID of the Vault instance holding the private key
	 * @param privateKeyVaultPath the path of the private key in the Vault instance
	 */
	@JsonView(HookView.class)
	@JacksonXmlRootElement(localName = "DeviceSshKeyAccount")
	record SshKey(
		long id,
		String name,
		DomainPayload mgmtDomain,
		boolean deviceSpecific,
		String privateKey,
		Long privateKeyVaultInstanceId,
		String privateKeyVaultPath) implements CredentialSetPayload {
	}

	/**
	 * A Telnet account.
	 *
	 * @param id the credential set ID
	 * @param name the name
	 * @param mgmtDomain the domain
	 * @param deviceSpecific whether it is specific to a device access
	 */
	@JsonView(HookView.class)
	@JacksonXmlRootElement(localName = "DeviceTelnetAccount")
	record Telnet(
		long id,
		String name,
		DomainPayload mgmtDomain,
		boolean deviceSpecific) implements CredentialSetPayload {
	}

	/**
	 * A HTTP account.
	 *
	 * @param id the credential set ID
	 * @param name the name
	 * @param mgmtDomain the domain
	 * @param deviceSpecific whether it is specific to a device access
	 */
	@JsonView(HookView.class)
	@JacksonXmlRootElement(localName = "DeviceHttpAccount")
	record Http(
		long id,
		String name,
		DomainPayload mgmtDomain,
		boolean deviceSpecific) implements CredentialSetPayload {
	}
}
