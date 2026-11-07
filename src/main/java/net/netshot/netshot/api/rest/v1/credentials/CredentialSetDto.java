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

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;
import net.netshot.netshot.api.rest.v1.Ref;
import net.netshot.netshot.api.rest.v1.domain.DomainDto;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.AuthProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.PrivProtocol;

/**
 * A credential set. The secrets are never returned, not even masked: the {@code hasXxx} properties tell whether
 * they are set (locally or in a Vault instance), and the {@code xxxVault} ones where a Vault-backed value is read
 * from. The properties which don't apply to the type are null.
 *
 * @param id the ID
 * @param url the path of the credential set (null for a set specific to a device)
 * @param display the display name
 * @param version the version
 * @param changeDate the date of the last change
 * @param name the name
 * @param type the type (SSH, SSH_KEY, TELNET, HTTP, SNMP_V1, SNMP_V2C, SNMP_V3)
 * @param mgmtDomain the domain the set is restricted to (null for all)
 * @param deviceSpecific whether the set is specific to a device access
 * @param username the local user name (CLI, HTTP and SNMPv3 sets; null if read from a Vault instance)
 * @param usernameVault where the user name is read from, if it is Vault-backed
 * @param hasPassword whether a password is set (SSH, Telnet, HTTP; the passphrase of the key for SSH_KEY)
 * @param passwordVault where the password is read from, if it is Vault-backed
 * @param hasSuperPassword whether a super (enable) password is set (SSH, SSH_KEY, Telnet)
 * @param superPasswordVault where the super password is read from, if it is Vault-backed
 * @param hasPrivateKey whether a private key is set (SSH_KEY)
 * @param privateKeyVault where the private key is read from, if it is Vault-backed
 * @param hasCommunity whether a community is set (SNMP_V1, SNMP_V2C)
 * @param communityVault where the community is read from, if it is Vault-backed
 * @param authType the SNMPv3 authentication protocol
 * @param hasAuthKey whether an SNMPv3 authentication key is set
 * @param authKeyVault where the authentication key is read from, if it is Vault-backed
 * @param privType the SNMPv3 privacy protocol
 * @param hasPrivKey whether an SNMPv3 privacy key is set
 * @param privKeyVault where the privacy key is read from, if it is Vault-backed
 */
@Schema(name = "CredentialSet", description = "Device credential set (secrets are never returned)")
public record CredentialSetDto(
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) long id,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String url,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String display,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) long version,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) Instant changeDate,
	@Brief String name,
	@Brief @Schema(allowableValues = {"SSH", "SSH_KEY", "TELNET", "HTTP", "SNMP_V1", "SNMP_V2C", "SNMP_V3"}) String type,
	@Brief @Ref DomainDto mgmtDomain,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) boolean deviceSpecific,
	String username,
	VaultSecretDto usernameVault,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) Boolean hasPassword,
	VaultSecretDto passwordVault,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) Boolean hasSuperPassword,
	VaultSecretDto superPasswordVault,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) Boolean hasPrivateKey,
	VaultSecretDto privateKeyVault,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) Boolean hasCommunity,
	VaultSecretDto communityVault,
	AuthProtocol authType,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) Boolean hasAuthKey,
	VaultSecretDto authKeyVault,
	PrivProtocol privType,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) Boolean hasPrivKey,
	VaultSecretDto privKeyVault) {
}
