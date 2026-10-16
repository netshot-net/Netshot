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
package net.netshot.netshot.api.rest.v0.device;

import org.hibernate.Hibernate;

import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.api.rest.v0.domain.DomainDto;
import net.netshot.netshot.api.rest.v0.domain.DomainMapper;
import net.netshot.netshot.credentials.DeviceCliAccount;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;
import net.netshot.netshot.inventory.domain.Domain;

/**
 * Maps the device accesses and their credential sets between the entities and their REST
 * representation, in both directions.
 *
 * <p>The mapping to an entity builds the <em>incoming</em> objects of a request: transient
 * entities which hold what the client sent, and which the device service validates, then
 * resolves against the persistent ones (global credential set by ID, Vault instance by ID...).
 * The secrets read as {@code =} (unchanged) are already {@code null} in the DTO.</p>
 */
public final class DeviceAccessMapper {

	private DeviceAccessMapper() {
		// Static helpers only
	}

	/**
	 * Maps a device access to its representation.
	 *
	 * @param access the access
	 * @return the DTO
	 */
	public static DeviceAccessDto toDto(DeviceAccess access) {
		return new DeviceAccessDto(
			access.getAccessName(),
			access.getAddress(),
			access.getPort(),
			toDto(access.getGlobalCredentialSet()),
			toDto(access.getSpecificCredentialSet()),
			access.getSshHostKeyVerification(),
			access.getSshTrustedHostKeys(),
			access.getSshTrustedHostKeysSince(),
			access.getHttpsCaTrustMode(),
			access.getHttpsCustomCaCertificate());
	}

	/**
	 * Maps an incoming device access to a transient entity.
	 *
	 * @param dto the DTO
	 * @return the access, not attached to any device
	 */
	public static DeviceAccess toEntity(DeviceAccessDto dto) {
		DeviceAccess access = new DeviceAccess(null, dto.accessName());
		access.setAddress(dto.address());
		access.setPort(dto.port());
		access.setGlobalCredentialSet(toEntity(dto.globalCredentialSet()));
		access.setSpecificCredentialSet(toEntity(dto.specificCredentialSet()));
		access.setSshHostKeyVerification(dto.sshHostKeyVerification());
		access.setSshTrustedHostKeys(dto.sshTrustedHostKeys());
		access.setSshTrustedHostKeysSince(dto.sshTrustedHostKeysSince());
		// Absent or null: the system trust store, which is the default of the entity as well
		if (dto.httpsCaTrustMode() != null) {
			access.setHttpsCaTrustMode(dto.httpsCaTrustMode());
		}
		access.setHttpsCustomCaCertificate(dto.httpsCustomCaCertificate());
		return access;
	}

	private static Domain toEntity(DomainDto dto) {
		if (dto == null) {
			return null;
		}
		// Only a reference to the domain: the addresses of the server are not read
		Domain domain = new Domain(dto.name(), dto.description(), null, null);
		domain.setId(dto.id());
		domain.setChangeDate(dto.changeDate());
		return domain;
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
		DeviceCredentialSet cs = (DeviceCredentialSet) Hibernate.unproxy(credentialSet);
		DomainDto domain = DomainMapper.toDto(cs.getMgmtDomain());
		if (cs instanceof DeviceSnmpv3Community c) {
			return new CredentialSetDto.SnmpV3(c.getId(), c.getName(), domain, c.isDeviceSpecific(),
				c.getCommunity(), c.getResolvedCommunityVaultInstanceId(), c.getCommunityVaultPath(),
				c.getUsername(), c.getResolvedUsernameVaultInstanceId(), c.getUsernameVaultPath(),
				c.getAuthType(), c.getAuthKey(), c.getResolvedAuthKeyVaultInstanceId(), c.getAuthKeyVaultPath(),
				c.getPrivType(), c.getPrivKey(), c.getResolvedPrivKeyVaultInstanceId(), c.getPrivKeyVaultPath());
		}
		else if (cs instanceof DeviceSnmpv2cCommunity c) {
			return new CredentialSetDto.SnmpV2c(c.getId(), c.getName(), domain, c.isDeviceSpecific(),
				c.getCommunity(), c.getResolvedCommunityVaultInstanceId(), c.getCommunityVaultPath());
		}
		else if (cs instanceof DeviceSnmpv1Community c) {
			return new CredentialSetDto.SnmpV1(c.getId(), c.getName(), domain, c.isDeviceSpecific(),
				c.getCommunity(), c.getResolvedCommunityVaultInstanceId(), c.getCommunityVaultPath());
		}
		else if (cs instanceof DeviceSshKeyAccount c) {
			return new CredentialSetDto.SshKey(c.getId(), c.getName(), domain, c.isDeviceSpecific(),
				c.getUsername(), c.getResolvedUsernameVaultInstanceId(), c.getUsernameVaultPath(),
				c.getPassword(), c.getResolvedPasswordVaultInstanceId(), c.getPasswordVaultPath(),
				c.getSuperPassword(), c.getResolvedSuperPasswordVaultInstanceId(), c.getSuperPasswordVaultPath(),
				c.getPrivateKey(), c.getResolvedPrivateKeyVaultInstanceId(), c.getPrivateKeyVaultPath());
		}
		else if (cs instanceof DeviceSshAccount c) {
			return new CredentialSetDto.Ssh(c.getId(), c.getName(), domain, c.isDeviceSpecific(),
				c.getUsername(), c.getResolvedUsernameVaultInstanceId(), c.getUsernameVaultPath(),
				c.getPassword(), c.getResolvedPasswordVaultInstanceId(), c.getPasswordVaultPath(),
				c.getSuperPassword(), c.getResolvedSuperPasswordVaultInstanceId(), c.getSuperPasswordVaultPath());
		}
		else if (cs instanceof DeviceTelnetAccount c) {
			return new CredentialSetDto.Telnet(c.getId(), c.getName(), domain, c.isDeviceSpecific(),
				c.getUsername(), c.getResolvedUsernameVaultInstanceId(), c.getUsernameVaultPath(),
				c.getPassword(), c.getResolvedPasswordVaultInstanceId(), c.getPasswordVaultPath(),
				c.getSuperPassword(), c.getResolvedSuperPasswordVaultInstanceId(), c.getSuperPasswordVaultPath());
		}
		else if (cs instanceof DeviceHttpAccount c) {
			return new CredentialSetDto.Http(c.getId(), c.getName(), domain, c.isDeviceSpecific(),
				c.getUsername(), c.getResolvedUsernameVaultInstanceId(), c.getUsernameVaultPath(),
				c.getPassword(), c.getResolvedPasswordVaultInstanceId(), c.getPasswordVaultPath());
		}
		throw new IllegalArgumentException("Unknown kind of credential set: " + cs.getClass().getName());
	}

	/**
	 * Maps an incoming credential set to a transient entity of the matching kind.
	 *
	 * @param dto the DTO (may be null)
	 * @return the credential set, or null if there is none
	 */
	public static DeviceCredentialSet toEntity(CredentialSetDto dto) {
		if (dto == null) {
			return null;
		}
		DeviceCredentialSet credentialSet = switch (dto) {
			case CredentialSetDto.SnmpV1 d -> {
				DeviceSnmpv1Community c = new DeviceSnmpv1Community(d.community(), d.name());
				c.setResolvedCommunityVaultInstanceId(d.communityVaultInstanceId());
				c.setCommunityVaultPath(d.communityVaultPath());
				yield c;
			}
			case CredentialSetDto.SnmpV2c d -> {
				DeviceSnmpv2cCommunity c = new DeviceSnmpv2cCommunity(d.community(), d.name());
				c.setResolvedCommunityVaultInstanceId(d.communityVaultInstanceId());
				c.setCommunityVaultPath(d.communityVaultPath());
				yield c;
			}
			case CredentialSetDto.SnmpV3 d -> {
				DeviceSnmpv3Community c = new DeviceSnmpv3Community(d.community(), d.name(), d.username(), d.authType(),
					d.authKey(), d.privType(), d.privKey());
				c.setResolvedCommunityVaultInstanceId(d.communityVaultInstanceId());
				c.setCommunityVaultPath(d.communityVaultPath());
				c.setResolvedUsernameVaultInstanceId(d.usernameVaultInstanceId());
				c.setUsernameVaultPath(d.usernameVaultPath());
				c.setResolvedAuthKeyVaultInstanceId(d.authKeyVaultInstanceId());
				c.setAuthKeyVaultPath(d.authKeyVaultPath());
				c.setResolvedPrivKeyVaultInstanceId(d.privKeyVaultInstanceId());
				c.setPrivKeyVaultPath(d.privKeyVaultPath());
				yield c;
			}
			case CredentialSetDto.Ssh d -> {
				DeviceSshAccount c = new DeviceSshAccount(d.username(), d.password(), d.superPassword(), d.name());
				setCliVaultReferences(c, d.usernameVaultInstanceId(), d.usernameVaultPath(), d.passwordVaultInstanceId(),
					d.passwordVaultPath(), d.superPasswordVaultInstanceId(), d.superPasswordVaultPath());
				yield c;
			}
			case CredentialSetDto.SshKey d -> {
				DeviceSshKeyAccount c = new DeviceSshKeyAccount(d.username(), d.privateKey(), d.password(), d.superPassword(), d.name());
				setCliVaultReferences(c, d.usernameVaultInstanceId(), d.usernameVaultPath(), d.passwordVaultInstanceId(),
					d.passwordVaultPath(), d.superPasswordVaultInstanceId(), d.superPasswordVaultPath());
				c.setResolvedPrivateKeyVaultInstanceId(d.privateKeyVaultInstanceId());
				c.setPrivateKeyVaultPath(d.privateKeyVaultPath());
				yield c;
			}
			case CredentialSetDto.Telnet d -> {
				DeviceTelnetAccount c = new DeviceTelnetAccount(d.username(), d.password(), d.superPassword(), d.name());
				setCliVaultReferences(c, d.usernameVaultInstanceId(), d.usernameVaultPath(), d.passwordVaultInstanceId(),
					d.passwordVaultPath(), d.superPasswordVaultInstanceId(), d.superPasswordVaultPath());
				yield c;
			}
			case CredentialSetDto.Http d -> {
				DeviceHttpAccount c = new DeviceHttpAccount(d.username(), d.password(), d.name());
				c.setResolvedUsernameVaultInstanceId(d.usernameVaultInstanceId());
				c.setUsernameVaultPath(d.usernameVaultPath());
				c.setResolvedPasswordVaultInstanceId(d.passwordVaultInstanceId());
				c.setPasswordVaultPath(d.passwordVaultPath());
				yield c;
			}
		};
		credentialSet.setId(dto.id());
		credentialSet.setDeviceSpecific(dto.deviceSpecific());
		credentialSet.setMgmtDomain(toEntity(dto.mgmtDomain()));
		return credentialSet;
	}

	private static void setCliVaultReferences(DeviceCliAccount account,
			Long usernameInstanceId, String usernamePath, Long passwordInstanceId, String passwordPath,
			Long superPasswordInstanceId, String superPasswordPath) {
		account.setResolvedUsernameVaultInstanceId(usernameInstanceId);
		account.setUsernameVaultPath(usernamePath);
		account.setResolvedPasswordVaultInstanceId(passwordInstanceId);
		account.setPasswordVaultPath(passwordPath);
		account.setResolvedSuperPasswordVaultInstanceId(superPasswordInstanceId);
		account.setSuperPasswordVaultPath(superPasswordPath);
	}
}
