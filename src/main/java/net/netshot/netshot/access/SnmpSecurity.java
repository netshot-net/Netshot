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
package net.netshot.netshot.access;

import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.AuthHMAC128SHA224;
import org.snmp4j.security.AuthHMAC192SHA256;
import org.snmp4j.security.AuthHMAC256SHA384;
import org.snmp4j.security.AuthHMAC384SHA512;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.Priv3DES;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.PrivAES192;
import org.snmp4j.security.PrivAES256;
import org.snmp4j.security.PrivDES;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.smi.OctetString;

/**
 * The SNMP security of the SNMP objects (the device accesses, the trap receiver): the authentication and privacy
 * protocols are registered once for the JVM (SecurityProtocols is a global singleton of SNMP4J, and the protocols
 * don't change), but each SNMP object has its own USM (its own SNMPv3 users), in its own message dispatcher: the
 * global security models of SNMP4J, which the default dispatcher uses, are shared, so the USM of one object would
 * replace the one of the others (another device access, the trap receiver).
 */
public final class SnmpSecurity {

	static {
		SecurityProtocols protocols = SecurityProtocols.getInstance();
		protocols.addAuthenticationProtocol(new AuthMD5());
		protocols.addAuthenticationProtocol(new AuthSHA());
		protocols.addAuthenticationProtocol(new AuthHMAC128SHA224());
		protocols.addAuthenticationProtocol(new AuthHMAC192SHA256());
		protocols.addAuthenticationProtocol(new AuthHMAC256SHA384());
		protocols.addAuthenticationProtocol(new AuthHMAC384SHA512());
		protocols.addPrivacyProtocol(new PrivDES());
		protocols.addPrivacyProtocol(new Priv3DES());
		protocols.addPrivacyProtocol(new PrivAES128());
		protocols.addPrivacyProtocol(new PrivAES192());
		protocols.addPrivacyProtocol(new PrivAES256());
	}

	private SnmpSecurity() {
	}

	/**
	 * Creates a USM (the SNMPv3 users of an SNMP object), with the registered protocols.
	 *
	 * @param localEngineId the local engine ID
	 * @return the USM, without user
	 */
	public static USM usm(OctetString localEngineId) {
		return new USM(SecurityProtocols.getInstance(), localEngineId, 0);
	}

	/**
	 * Creates the message dispatcher of an SNMP object: SNMPv1, SNMPv2c and, with a USM, SNMPv3 with only this USM.
	 *
	 * @param usm the USM of the SNMP object, null for no SNMPv3
	 * @return the dispatcher
	 */
	public static MessageDispatcherImpl dispatcher(USM usm) {
		MessageDispatcherImpl dispatcher = new MessageDispatcherImpl();
		dispatcher.addMessageProcessingModel(new MPv1());
		dispatcher.addMessageProcessingModel(new MPv2c());
		if (usm != null) {
			dispatcher.addMessageProcessingModel(new MPv3(usm));
		}
		return dispatcher;
	}
}
