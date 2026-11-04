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

import java.util.ArrayList;
import java.util.List;

import net.netshot.netshot.api.rest.v0.domain.DomainDto;
import net.netshot.netshot.api.rest.v0.domain.DomainMapper;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.platform.secret.LocalSecret;
import net.netshot.netshot.platform.secret.Secret;

/**
 * Maps the credential sets between the records and their REST representation, in both directions.
 * It serves the credential set endpoints and the credential sets nested in the device accesses.
 *
 * <p>The mapping to a record builds the <em>incoming</em> objects of a request: records which
 * hold what the client sent, and which the services validate, then resolve against the stored
 * ones (domain by ID, Vault instance by ID...). The secrets read as {@code =}
 * (unchanged) are already {@code null} in the DTO. The secrets are never written: the DTO masks them.</p>
 */
public final class CredentialSetMapper {

	private CredentialSetMapper() {
		// Static helpers only
	}

	/**
	 * Maps credential sets to their representation.
	 *
	 * @param credentialSets the credential sets
	 * @return the DTOs, in the same order
	 */
	public static List<CredentialSetDto> toDtos(List<DeviceCredentialSet> credentialSets) {
		List<CredentialSetDto> dtos = new ArrayList<>(credentialSets.size());
		credentialSets.forEach(credentialSet -> dtos.add(toDto(credentialSet)));
		return dtos;
	}

	private static Domain toReference(DomainDto dto) {
		if (dto == null) {
			return null;
		}
		// Only a reference to the domain (the service reads it by ID): the addresses of the server are not read
		return Domain.builder().id(dto.id()).name(dto.name()).description(dto.description())
			.changeDate(dto.changeDate() == null ? null : dto.changeDate().toInstant()).build();
	}


	/**
	 * Gives the local value of a secret to the DTO, which masks it: only whether there is one matters (it is not
	 * decrypted).
	 *
	 * @param secret the secret
	 * @return the mask, or null if the secret has no local value
	 */
	private static String masked(Secret secret) {
		return Secret.localValue(secret) == null ? null : "=";
	}

	/**
	 * Gives the local value of a field which isn't a secret (the username).
	 *
	 * @param value the field
	 * @return the value, or null if the field has no local value
	 */
	private static String clear(Secret value) {
		LocalSecret local = Secret.localValue(value);
		return local == null ? null : local.reveal();
	}

	/**
	 * Reads a field of a DTO.
	 *
	 * @param localValue the local value
	 * @param vaultInstanceId the ID of the Vault instance, or null if the field is local
	 * @param vaultPath the Vault path (and key)
	 * @return the field, or null if it is not set
	 */
	private static Secret secret(String localValue, Long vaultInstanceId, String vaultPath) {
		return Secret.of(Secret.local(localValue), vaultInstanceId, vaultPath);
	}

	/**
	 * Maps a credential set to its representation, according to its kind.
	 *
	 * @param credentialSet the credential set (may be null)
	 * @return the DTO, or null if there is no credential set
	 * @throws IllegalArgumentException if the kind of credential set is unknown
	 */
	public static CredentialSetDto toDto(DeviceCredentialSet credentialSet) {
		if (credentialSet == null) {
			return null;
		}
		DomainDto domain = DomainMapper.toDto(credentialSet.mgmtDomain());
		return switch (credentialSet) {
			case DeviceSnmpv3Community c -> new CredentialSetDto.SnmpV3(c.id(), c.name(), domain, c.deviceSpecific(),
				masked(c.community()), Secret.vaultInstanceId(c.community()), Secret.vaultPath(c.community()),
				clear(c.username()), Secret.vaultInstanceId(c.username()), Secret.vaultPath(c.username()),
				c.authType(), masked(c.authKey()), Secret.vaultInstanceId(c.authKey()), Secret.vaultPath(c.authKey()),
				c.privType(), masked(c.privKey()), Secret.vaultInstanceId(c.privKey()), Secret.vaultPath(c.privKey()));
			case DeviceSnmpv2cCommunity c -> new CredentialSetDto.SnmpV2c(c.id(), c.name(), domain, c.deviceSpecific(),
				masked(c.community()), Secret.vaultInstanceId(c.community()), Secret.vaultPath(c.community()));
			case DeviceSnmpv1Community c -> new CredentialSetDto.SnmpV1(c.id(), c.name(), domain, c.deviceSpecific(),
				masked(c.community()), Secret.vaultInstanceId(c.community()), Secret.vaultPath(c.community()));
			case DeviceSshKeyAccount c -> new CredentialSetDto.SshKey(c.id(), c.name(), domain, c.deviceSpecific(),
				clear(c.username()), Secret.vaultInstanceId(c.username()), Secret.vaultPath(c.username()),
				masked(c.password()), Secret.vaultInstanceId(c.password()), Secret.vaultPath(c.password()),
				masked(c.superPassword()), Secret.vaultInstanceId(c.superPassword()), Secret.vaultPath(c.superPassword()),
				masked(c.privateKey()), Secret.vaultInstanceId(c.privateKey()), Secret.vaultPath(c.privateKey()));
			case DeviceSshAccount c -> new CredentialSetDto.Ssh(c.id(), c.name(), domain, c.deviceSpecific(),
				clear(c.username()), Secret.vaultInstanceId(c.username()), Secret.vaultPath(c.username()),
				masked(c.password()), Secret.vaultInstanceId(c.password()), Secret.vaultPath(c.password()),
				masked(c.superPassword()), Secret.vaultInstanceId(c.superPassword()), Secret.vaultPath(c.superPassword()));
			case DeviceTelnetAccount c -> new CredentialSetDto.Telnet(c.id(), c.name(), domain, c.deviceSpecific(),
				clear(c.username()), Secret.vaultInstanceId(c.username()), Secret.vaultPath(c.username()),
				masked(c.password()), Secret.vaultInstanceId(c.password()), Secret.vaultPath(c.password()),
				masked(c.superPassword()), Secret.vaultInstanceId(c.superPassword()), Secret.vaultPath(c.superPassword()));
			case DeviceHttpAccount c -> new CredentialSetDto.Http(c.id(), c.name(), domain, c.deviceSpecific(),
				clear(c.username()), Secret.vaultInstanceId(c.username()), Secret.vaultPath(c.username()),
				masked(c.password()), Secret.vaultInstanceId(c.password()), Secret.vaultPath(c.password()));
		};
	}

	/**
	 * Maps an incoming credential set to a record of the matching kind.
	 *
	 * @param dto the DTO (may be null)
	 * @return the credential set, or null if there is none
	 */
	public static DeviceCredentialSet toEntity(CredentialSetDto dto) {
		if (dto == null) {
			return null;
		}
		Domain domain = toReference(dto.mgmtDomain());
		return switch (dto) {
			case CredentialSetDto.SnmpV1 d -> new DeviceSnmpv1Community(d.id(), 0, null, d.name(), domain, d.deviceSpecific(),
				secret(d.community(), d.communityVaultInstanceId(), d.communityVaultPath()));
			case CredentialSetDto.SnmpV2c d -> new DeviceSnmpv2cCommunity(d.id(), 0, null, d.name(), domain, d.deviceSpecific(),
				secret(d.community(), d.communityVaultInstanceId(), d.communityVaultPath()));
			case CredentialSetDto.SnmpV3 d -> new DeviceSnmpv3Community(d.id(), 0, null, d.name(), domain, d.deviceSpecific(),
				secret(d.community(), d.communityVaultInstanceId(), d.communityVaultPath()),
				secret(d.username(), d.usernameVaultInstanceId(), d.usernameVaultPath()),
				d.authType(), secret(d.authKey(), d.authKeyVaultInstanceId(), d.authKeyVaultPath()),
				d.privType(), secret(d.privKey(), d.privKeyVaultInstanceId(), d.privKeyVaultPath()));
			case CredentialSetDto.Ssh d -> new DeviceSshAccount(d.id(), 0, null, d.name(), domain, d.deviceSpecific(),
				secret(d.username(), d.usernameVaultInstanceId(), d.usernameVaultPath()),
				secret(d.password(), d.passwordVaultInstanceId(), d.passwordVaultPath()),
				secret(d.superPassword(), d.superPasswordVaultInstanceId(), d.superPasswordVaultPath()));
			case CredentialSetDto.SshKey d -> new DeviceSshKeyAccount(d.id(), 0, null, d.name(), domain, d.deviceSpecific(),
				secret(d.username(), d.usernameVaultInstanceId(), d.usernameVaultPath()),
				secret(d.password(), d.passwordVaultInstanceId(), d.passwordVaultPath()),
				secret(d.superPassword(), d.superPasswordVaultInstanceId(), d.superPasswordVaultPath()),
				secret(d.privateKey(), d.privateKeyVaultInstanceId(), d.privateKeyVaultPath()));
			case CredentialSetDto.Telnet d -> new DeviceTelnetAccount(d.id(), 0, null, d.name(), domain, d.deviceSpecific(),
				secret(d.username(), d.usernameVaultInstanceId(), d.usernameVaultPath()),
				secret(d.password(), d.passwordVaultInstanceId(), d.passwordVaultPath()),
				secret(d.superPassword(), d.superPasswordVaultInstanceId(), d.superPasswordVaultPath()));
			case CredentialSetDto.Http d -> new DeviceHttpAccount(d.id(), 0, null, d.name(), domain, d.deviceSpecific(),
				secret(d.username(), d.usernameVaultInstanceId(), d.usernameVaultPath()),
				secret(d.password(), d.passwordVaultInstanceId(), d.passwordVaultPath()));
		};
	}
}
