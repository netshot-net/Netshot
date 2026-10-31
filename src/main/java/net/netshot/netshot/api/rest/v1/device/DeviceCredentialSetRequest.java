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
package net.netshot.netshot.api.rest.v1.device;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.api.rest.v1.Secret;
import net.netshot.netshot.api.rest.v1.credentials.CredentialSetDto;
import net.netshot.netshot.api.rest.v1.credentials.VaultSecretRequest;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.AuthProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.PrivProtocol;

/**
 * The credential set specific to one access of a device: like a global credential set, but with no name and no domain.
 * The secrets left out keep their current value (when the type doesn't change), {@code null} clears them.
 *
 * @param type the type of credential set
 * @param username the user name
 * @param usernameVault where to read the user name from, instead of {@code username}
 * @param password the password
 * @param passwordVault where to read the password from, instead of {@code password}
 * @param superPassword the super password (enable password)
 * @param superPasswordVault where to read the super password from
 * @param privateKey the private key
 * @param privateKeyVault where to read the private key from
 * @param community the SNMP community
 * @param communityVault where to read the SNMP community from
 * @param authType the SNMP v3 authentication protocol
 * @param authKey the SNMP v3 authentication key
 * @param authKeyVault where to read the SNMP v3 authentication key from
 * @param privType the SNMP v3 privacy protocol
 * @param privKey the SNMP v3 privacy key
 * @param privKeyVault where to read the SNMP v3 privacy key from
 */
@Schema(name = "DeviceCredentialSetRequest", description = "Credential set specific to one access of a device")
@ReadOnlyFrom(CredentialSetDto.class)
public record DeviceCredentialSetRequest(
	@JsonProperty(required = true) @Schema(allowableValues = {"SSH", "SSH_KEY", "TELNET", "HTTP", "SNMP_V1", "SNMP_V2C", "SNMP_V3"}) String type,
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
