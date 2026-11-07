/**
 * Copyright 2013-present Netshot
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
package net.netshot.netshot.api.rest.v1.credentials;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.api.rest.v1.RefId;
import net.netshot.netshot.api.rest.v1.Secret;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.AuthProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.PrivProtocol;

/**
 * The definition of a global credential set, to create one ({@code POST}) or replace one ({@code PUT}, and the
 * result of a {@code PATCH}). One record serves every type: the properties which don't apply to the type are
 * refused. The read-only properties of the representation ({@link CredentialSetDto}) are ignored.
 *
 * <p>Each vaultable field (username, password, ...) is either given by value ({@code password}, or
 * {@code username}) or read from a Vault instance ({@code passwordVault}), not both. A field which is left out
 * is unchanged (a secret can't be read back, so a {@code PUT} doesn't have to repeat it); a secret set to
 * {@code null} is cleared, which the secrets a type needs refuse.</p>
 *
 * @param name the name
 * @param type the type (it can't change)
 * @param mgmtDomain the domain the credential set is restricted to (its ID, or an object with its ID); none if absent
 * @param username the user name (SSH, SSH_KEY, TELNET, HTTP, SNMP_V3)
 * @param usernameVault where to read the user name from
 * @param password the password (SSH, TELNET, HTTP: required; the optional passphrase of the key for SSH_KEY)
 * @param passwordVault where to read the password from
 * @param superPassword the super (enable) password (SSH, SSH_KEY, TELNET: optional)
 * @param superPasswordVault where to read the super password from
 * @param privateKey the PEM private key (SSH_KEY: required)
 * @param privateKeyVault where to read the private key from
 * @param community the community (SNMP_V1, SNMP_V2C: required)
 * @param communityVault where to read the community from
 * @param authType the authentication protocol (SNMP_V3; NONE if absent)
 * @param authKey the authentication key (SNMP_V3: required unless authType is NONE)
 * @param authKeyVault where to read the authentication key from
 * @param privType the privacy protocol (SNMP_V3; NONE if absent)
 * @param privKey the privacy key (SNMP_V3: required unless privType is NONE)
 * @param privKeyVault where to read the privacy key from
 */
@Schema(name = "CredentialSetRequest", description = "Definition of a global credential set")
@ReadOnlyFrom(CredentialSetDto.class)
public record CredentialSetRequest(
	@JsonProperty(required = true) String name,
	@JsonProperty(required = true) @Schema(allowableValues = {"SSH", "SSH_KEY", "TELNET", "HTTP", "SNMP_V1", "SNMP_V2C", "SNMP_V3"}) String type,
	RefId mgmtDomain,
	String username,
	VaultSecretRequest usernameVault,
	@JsonInclude(JsonInclude.Include.NON_NULL) Secret password,
	VaultSecretRequest passwordVault,
	@JsonInclude(JsonInclude.Include.NON_NULL) Secret superPassword,
	VaultSecretRequest superPasswordVault,
	@JsonInclude(JsonInclude.Include.NON_NULL) Secret privateKey,
	VaultSecretRequest privateKeyVault,
	@JsonInclude(JsonInclude.Include.NON_NULL) Secret community,
	VaultSecretRequest communityVault,
	AuthProtocol authType,
	@JsonInclude(JsonInclude.Include.NON_NULL) Secret authKey,
	VaultSecretRequest authKeyVault,
	PrivProtocol privType,
	@JsonInclude(JsonInclude.Include.NON_NULL) Secret privKey,
	VaultSecretRequest privKeyVault) {
}
