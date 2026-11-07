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
package net.netshot.netshot.credentials;

import net.netshot.netshot.credentials.DeviceSnmpv3Community.AuthProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.PrivProtocol;
import net.netshot.netshot.vault.SecretChange;

/**
 * What a client defines of a global credential set (v1 API). Every secret field says what to do with the stored
 * value ({@link SecretChange}); the fields which don't apply to the kind are ignored.
 *
 * @param kind the kind of credential set (it can't change)
 * @param name the name
 * @param mgmtDomainId the ID of the domain the credential set is restricted to, or null for all the domains
 * @param username the username (SSH, Telnet, HTTP, SNMPv3)
 * @param password the password (the passphrase of the private key for SSH with a key)
 * @param superPassword the super (enable) password (SSH, Telnet)
 * @param privateKey the private key (SSH with a key)
 * @param community the community (SNMPv1 and v2c)
 * @param authType the SNMPv3 authentication protocol (none if null)
 * @param authKey the SNMPv3 authentication key
 * @param privType the SNMPv3 privacy protocol (none if null)
 * @param privKey the SNMPv3 privacy key
 */
public record CredentialSetDefinition(Kind kind, String name, Long mgmtDomainId, SecretChange username, SecretChange password,
	SecretChange superPassword, SecretChange privateKey, SecretChange community, AuthProtocol authType, SecretChange authKey,
	PrivProtocol privType, SecretChange privKey) {

	/** The kinds of credential sets. */
	public enum Kind {
		SSH,
		SSH_KEY,
		TELNET,
		HTTP,
		SNMP_V1,
		SNMP_V2C,
		SNMP_V3;

		/**
		 * Gets the kind of a credential set.
		 *
		 * @param credentialSet the credential set
		 * @return its kind
		 */
		public static Kind of(DeviceCredentialSet credentialSet) {
			return switch (credentialSet) {
				case DeviceSshKeyAccount _ -> SSH_KEY;
				case DeviceSshAccount _ -> SSH;
				case DeviceTelnetAccount _ -> TELNET;
				case DeviceHttpAccount _ -> HTTP;
				case DeviceSnmpv1Community _ -> SNMP_V1;
				case DeviceSnmpv2cCommunity _ -> SNMP_V2C;
				case DeviceSnmpv3Community _ -> SNMP_V3;
			};
		}
	}
}
