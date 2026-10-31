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
package net.netshot.netshot.api.rest.v1.credentials;

import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.domain.DomainMapper;
import net.netshot.netshot.credentials.DeviceCliAccount;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;

/**
 * Maps the credential sets to their v1 representation.
 */
public final class CredentialSetMapper {

	/** The path of the credential sets. */
	public static final String PATH = "credential-sets";

	private CredentialSetMapper() {
	}

	/**
	 * Gets the v1 type name of a credential set.
	 *
	 * @param credentialSet the credential set
	 * @return the type name
	 */
	public static String typeOf(DeviceCredentialSet credentialSet) {
		return switch (credentialSet) {
			case DeviceSshKeyAccount _ -> "SSH_KEY";
			case DeviceSshAccount _ -> "SSH";
			case DeviceTelnetAccount _ -> "TELNET";
			case DeviceHttpAccount _ -> "HTTP";
			case DeviceSnmpv3Community _ -> "SNMP_V3";
			case DeviceSnmpv2cCommunity _ -> "SNMP_V2C";
			case DeviceSnmpv1Community _ -> "SNMP_V1";
			default -> credentialSet.getClass().getSimpleName();
		};
	}

	/**
	 * Maps a credential set (without its secrets).
	 *
	 * @param credentialSet the credential set (null gives null)
	 * @return the representation
	 */
	public static CredentialSetDto toDto(DeviceCredentialSet credentialSet) {
		if (credentialSet == null) {
			return null;
		}
		String username = switch (credentialSet) {
			case DeviceCliAccount c -> c.username();
			case DeviceHttpAccount c -> c.username();
			case DeviceSnmpv3Community c -> c.username();
			default -> null;
		};
		return new CredentialSetDto(credentialSet.id(),
			credentialSet.deviceSpecific() ? null : V1Links.of(PATH, credentialSet.id()),
			credentialSet.name(), credentialSet.version(), credentialSet.changeDate(), credentialSet.name(),
			typeOf(credentialSet), DomainMapper.toDto(credentialSet.mgmtDomain()), credentialSet.deviceSpecific(), username);
	}
}
