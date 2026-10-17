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

import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.api.rest.v0.credentials.CredentialSetMapper;

/**
 * Maps the device accesses (and, through {@link CredentialSetMapper}, their credential sets) between
 * the entities and their REST representation, in both directions.
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
			CredentialSetMapper.toDto(access.getGlobalCredentialSet()),
			CredentialSetMapper.toDto(access.getSpecificCredentialSet()),
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
		access.setGlobalCredentialSet(CredentialSetMapper.toEntity(dto.globalCredentialSet()));
		access.setSpecificCredentialSet(CredentialSetMapper.toEntity(dto.specificCredentialSet()));
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
}
