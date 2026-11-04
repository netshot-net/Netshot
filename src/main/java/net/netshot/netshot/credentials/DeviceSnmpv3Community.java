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
package net.netshot.netshot.credentials;

import java.time.Instant;

import lombok.Builder;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.platform.secret.Secret;

/**
 * SNMPv3 credentials (the community isn't used by SNMPv3, it is kept as it is stored).
 *
 * @param id the ID (0 for a credential set which isn't stored yet)
 * @param version the version, incremented on each change
 * @param changeDate the time of the last change
 * @param name the name, unique
 * @param mgmtDomain the domain the credential set is restricted to, or null for all the domains
 * @param deviceSpecific whether the credential set belongs to one access of one device (rather than being global)
 * @param community the community, local or in a Vault instance (null if not set)
 * @param username the username, local or in a Vault instance (null if not set)
 * @param authType the authentication protocol
 * @param authKey the authentication key, local or in a Vault instance (null if not set)
 * @param privType the privacy protocol
 * @param privKey the privacy key, local or in a Vault instance (null if not set)
 */
@Builder(toBuilder = true)
public record DeviceSnmpv3Community(
	long id,
	int version,
	Instant changeDate,
	String name,
	Domain mgmtDomain,
	boolean deviceSpecific,
	Secret community,
	Secret username,
	AuthProtocol authType,
	Secret authKey,
	PrivProtocol privType,
	Secret privKey) implements DeviceSnmpCommunity {

	/**
	 * SNMPv3 authentication protocol. {@link #NONE} means noAuth (and,
	 * transitively, noPriv - privacy requires authentication). SHA-2 variants
	 * (RFC 7860) are named after their snmp4j class (e.g. {@code HMAC192SHA256}),
	 * matching the naming already used for the SNMP trap receiver's user config.
	 */
	public enum AuthProtocol {
		NONE,
		MD5,
		SHA,
		HMAC128SHA224,
		HMAC192SHA256,
		HMAC256SHA384,
		HMAC384SHA512,
	}

	/**
	 * SNMPv3 privacy (encryption) protocol. {@link #NONE} means noPriv.
	 */
	public enum PrivProtocol {
		NONE,
		DES,
		DES3,
		AES128,
		AES192,
		AES256,
	}

	@Override
	public DeviceSnmpv3Community withIdentity(long newId, String newName, boolean newDeviceSpecific) {
		return this.toBuilder().id(newId).name(newName).deviceSpecific(newDeviceSpecific).build();
	}

	@Override
	public DeviceSnmpv3Community withMgmtDomain(Domain newMgmtDomain) {
		return this.toBuilder().mgmtDomain(newMgmtDomain).build();
	}

	@Override
	public String toString() {
		return DeviceCredentialSet.describe(this);
	}
}
