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
package net.netshot.netshot.api.rest.v0.device;

import java.util.Date;

import net.netshot.netshot.api.rest.v0.credentials.CredentialSetMapper;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * Maps the device accesses (and, through {@link CredentialSetMapper}, their credential sets) between
 * the records and their REST representation, in both directions.
 *
 * <p>The mapping to a record builds the <em>incoming</em> objects of a request: records which
 * hold what the client sent, and which the device service validates, then resolves against the
 * stored ones (global credential set by ID, Vault instance by ID...).
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
			access.accessName(),
			access.address(),
			access.port(),
			CredentialSetMapper.toDto(access.globalCredentialSet()),
			CredentialSetMapper.toDto(access.specificCredentialSet()),
			access.sshHostKeyVerification(),
			access.sshTrustedHostKeys(),
			access.sshTrustedHostKeysSince() == null ? null : Date.from(access.sshTrustedHostKeysSince()),
			access.httpsCaTrustMode(),
			access.httpsCustomCaCertificate());
	}

	/**
	 * Maps an incoming device access to a transient entity.
	 *
	 * @param dto the DTO
	 * @return the access, not attached to any device
	 */
	public static DeviceAccess toEntity(DeviceAccessDto dto) {
		return DeviceAccess.builder()
			.accessName(dto.accessName())
			.address(dto.address())
			.port(dto.port())
			.globalCredentialSet(CredentialSetMapper.toEntity(dto.globalCredentialSet()))
			.specificCredentialSet(CredentialSetMapper.toEntity(dto.specificCredentialSet()))
			.sshHostKeyVerification(dto.sshHostKeyVerification())
			.sshTrustedHostKeys(dto.sshTrustedHostKeys())
			.sshTrustedHostKeysSince(dto.sshTrustedHostKeysSince() == null ? null : dto.sshTrustedHostKeysSince().toInstant())
			// Absent or null: the system trust store
			.httpsCaTrustMode(dto.httpsCaTrustMode() == null ? HttpsCaTrustMode.SYSTEM_TRUSTSTORE : dto.httpsCaTrustMode())
			.httpsCustomCaCertificate(dto.httpsCustomCaCertificate())
			.build();
	}
}
