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
package net.netshot.netshot.device.access;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.device.NetworkAddress;
import net.netshot.netshot.device.credentials.DeviceSnmpCommunity;
import net.netshot.netshot.device.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.device.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.device.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.vault.VaultException;
import net.netshot.netshot.vault.VaultManager;
import net.netshot.netshot.vault.VaultableSecret;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.ScopedPDU;
import org.snmp4j.Target;
import org.snmp4j.TransportMapping;
import org.snmp4j.UserTarget;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
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
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeUtils;


/**
 * A SNMP poller class, to poll data from a device via SNMP.
 */
@Slf4j
public class Snmp extends Poller implements Client {

	/** The port. */
	public static final int DEFAULT_PORT = 161;

	/** The snmp. */
	private org.snmp4j.Snmp snmp;

	/** The target. */
	private Target<UdpAddress> target;

	/** SNMPv3 auth protocol. */
	private OID authProtocol;

	/** SNMPv3 priv protocol. */
	private OID privProtocol;

	static {
		// Registered once for the whole JVM (SecurityProtocols is a global
		// singleton) - not relying on SnmpTrapReceiver having done it first,
		// since the trap receiver can be disabled by configuration.
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

	/**
	 * Instantiates a new SNMP object based on a target address and a Netshot community,
	 * using the default SNMP port ({@link #DEFAULT_PORT}).
	 * @param address The target
	 * @param community The SNMP credentials
	 * @throws IOException it can happen
	 */
	public Snmp(NetworkAddress address, DeviceSnmpCommunity community) throws IOException {
		this(address, DEFAULT_PORT, community);
	}

	/**
	 * Instantiates a new SNMP object based on a target address/port and a Netshot community.
	 * @param address The target
	 * @param port The target UDP port
	 * @param community The SNMP credentials
	 * @throws IOException it can happen
	 */
	public Snmp(NetworkAddress address, int port, DeviceSnmpCommunity community) throws IOException {
		if (community instanceof DeviceSnmpv1Community) {
			this.target = new CommunityTarget<>(new UdpAddress(address.getInetAddress(), port),
				new OctetString(resolveSecret(community.getCommunitySecret())));
			this.target.setVersion(SnmpConstants.version1);
			start();
		}
		else if (community instanceof DeviceSnmpv2cCommunity) {
			this.target = new CommunityTarget<>(new UdpAddress(address.getInetAddress(), port),
				new OctetString(resolveSecret(community.getCommunitySecret())));
			this.target.setVersion(SnmpConstants.version2c);
			start();
		}
		else if (community instanceof DeviceSnmpv3Community v3Credentials) {
			// Prepare target
			log.debug("Prepare SNMPv3 context");
			this.target = new UserTarget<>();
			this.target.setTimeout(5000);
			this.target.setVersion(SnmpConstants.version3);
			this.target.setAddress(new UdpAddress(address.getInetAddress(), port));

			final DeviceSnmpv3Community.AuthProtocol authType = v3Credentials.getAuthType();
			final DeviceSnmpv3Community.PrivProtocol privType = v3Credentials.getPrivType();
			final boolean hasAuth = authType != null && authType != DeviceSnmpv3Community.AuthProtocol.NONE;
			final boolean hasPriv = hasAuth && privType != null && privType != DeviceSnmpv3Community.PrivProtocol.NONE;

			if (!hasAuth) {
				this.target.setSecurityLevel(SecurityLevel.NOAUTH_NOPRIV);
			}
			else if (!hasPriv) {
				this.target.setSecurityLevel(SecurityLevel.AUTH_NOPRIV);
			}
			else {
				this.target.setSecurityLevel(SecurityLevel.AUTH_PRIV);
			}
			String v3Username = resolveSecret(v3Credentials.getUsernameSecret());
			this.target.setSecurityName(new OctetString(v3Username));

			// Prepare transport
			log.debug("Auth Protocol called: {}", authType);
			if (hasAuth) {
				this.authProtocol = switch (authType) {
					case SHA -> AuthSHA.ID;
					case HMAC128SHA224 -> AuthHMAC128SHA224.ID;
					case HMAC192SHA256 -> AuthHMAC192SHA256.ID;
					case HMAC256SHA384 -> AuthHMAC256SHA384.ID;
					case HMAC384SHA512 -> AuthHMAC384SHA512.ID;
					default -> AuthMD5.ID;
				};
			}

			if (hasPriv) {
				this.privProtocol = switch (privType) {
					case DES -> PrivDES.ID;
					case DES3 -> Priv3DES.ID;
					case AES128 -> PrivAES128.ID;
					case AES192 -> PrivAES192.ID;
					case AES256 -> PrivAES256.ID;
					default -> Priv3DES.ID;
				};
			}

			USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
			usm.addUser(
				new UsmUser(
					new OctetString(v3Username),
					hasAuth ? this.authProtocol : null,
					hasAuth ? new OctetString(resolveSecret(v3Credentials.getAuthKeySecret())) : null,
					hasPriv ? this.privProtocol : null,
					hasPriv ? new OctetString(resolveSecret(v3Credentials.getPrivKeySecret())) : null));
			SecurityModels.getInstance().addSecurityModel(usm);

			start();
		}
	}

	/**
	 * Resolves a credential field's actual value (local, or from Vault),
	 * wrapping a Vault failure as an {@link IOException} so it's handled the
	 * same way as any other connection failure by callers.
	 * @param secret the vaultable secret to resolve
	 * @return the resolved value
	 */
	private static String resolveSecret(VaultableSecret secret) throws IOException {
		try {
			return VaultManager.resolve(secret);
		}
		catch (VaultException e) {
			throw new IOException("Unable to resolve a Vault-backed credential: " + e.getMessage(), e);
		}
	}

	/**
	 * Instantiates a new snmp.
	 *
	 * @param address the address
	 * @param community the community
	 * @param v1 the v1
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public Snmp(NetworkAddress address, String community, boolean v1) throws IOException {
		this.target = new CommunityTarget<>(new UdpAddress(address.getInetAddress(), DEFAULT_PORT), new OctetString(community));
		this.target.setVersion(v1 ? SnmpConstants.version1 : SnmpConstants.version2c);
		start();
	}

	/**
	 * Connect. A no-op: the UDP transport is already opened by the constructor
	 * (via {@link #start()}), so there is nothing left to do lazily here.
	 */
	@Override
	public void connect() throws IOException {
		// Nothing to do: the transport is already listening.
	}

	/**
	 * Disconnect, i.e. release the underlying UDP transport.
	 */
	@Override
	public void disconnect() {
		try {
			this.stop();
		}
		catch (IOException e) {
			log.warn("Error while closing the SNMP transport.", e);
		}
	}

	/**
	 * Start.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void start() throws IOException {
		TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
		this.snmp = new org.snmp4j.Snmp(transport);
		transport.listen();
	}

	/**
	 * Stop.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void stop() throws IOException {
		snmp.close();
	}

	/**
	 * Converts a variable to string.
	 * By default, snmp4j renders an OctetString as text when all its bytes are printable,
	 * and as colon-separated hex otherwise; with hex = true, OctetStrings are always
	 * rendered as hex (useful for binary values such as MAC addresses).
	 *
	 * @param variable the variable to convert
	 * @param hex whether to always render OctetStrings as hex
	 * @return the string value
	 */
	private static String variableToString(Variable variable, boolean hex) {
		if (hex && variable instanceof OctetString octetString) {
			return octetString.toHexString();
		}
		return variable.toString();
	}

	/**
	 * SNMP GET of a single OID, returning the value as a string
	 * (OctetStrings are rendered as text if printable, as hex otherwise).
	 *
	 * @param oid the OID to get
	 * @return the value, as a string
	 * @throws IOException in case of SNMP error or missing response
	 */
	public String getAsString(OID oid) throws IOException {
		return getAsString(oid, false);
	}

	/**
	 * SNMP GET of a single OID, returning the value as a string.
	 *
	 * @param oid the OID to get
	 * @param hex whether to always render OctetStrings as hex
	 * @return the value, as a string
	 * @throws IOException in case of SNMP error or missing response
	 */
	public String getAsString(OID oid, boolean hex) throws IOException {
		ResponseEvent<UdpAddress> event = this.get(new OID[] { oid });
		PDU response = event.getResponse();
		if (response == null || response.size() == 0) {
			throw new IOException("No SNMP response.");
		}
		if (response.size() < 1) {
			throw new IOException("Empty SNMP response");
		}
		if (response.get(0).isException()) {
			throw new IOException("SNMP error: " + response.get(0).toValueString());
		}
		if ("1.3.6.1.6.3.15.1.1.3.0".equals(response.get(0).getOid().toString())) {
			throw new IOException("SNMP error: invalid username");
		}
		return variableToString(response.get(0).getVariable(), hex);
	}

	/**
	 * SNMP GET of a single OID, returning the value as a string
	 * (OctetStrings are rendered as text if printable, as hex otherwise).
	 *
	 * @param oid the OID to get, in dotted notation
	 * @return the value, as a string
	 * @throws IOException in case of SNMP error or missing response
	 */
	public String getAsString(String oid) throws IOException {
		return getAsString(new OID(oid), false);
	}

	/**
	 * SNMP GET of a single OID, returning the value as a string.
	 *
	 * @param oid the OID to get, in dotted notation
	 * @param hex whether to always render OctetStrings as hex
	 * @return the value, as a string
	 * @throws IOException in case of SNMP error or missing response
	 */
	public String getAsString(String oid, boolean hex) throws IOException {
		return getAsString(new OID(oid), hex);
	}


	/**
	 * Gets the pdu.
	 *
	 * @param oids the oids
	 * @return the pdu
	 */
	private PDU getPDU(OID[] oids) {
		PDU pdu = new PDU();
		for (OID oid : oids) {
			pdu.add(new VariableBinding(oid));
		}
		pdu.setType(PDU.GET);
		return pdu;
	}

	/**
	 * Gets the scoped pdu.
	 * 
	 * @param oids the oids
	 * @return the scoped pdu
	 */

	private ScopedPDU getScopedPDU(OID[] oids) {
		ScopedPDU scopedPdu = new ScopedPDU();
		for (OID oid : oids) {
			scopedPdu.add(new VariableBinding(oid));
		}
		scopedPdu.setType(PDU.GET);
		return scopedPdu;
	}

	/**
	 * Gets the a response.
	 *
	 * @param oids the oids
	 * @return the response event
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public ResponseEvent<UdpAddress> get(OID[] oids) throws IOException {
		ResponseEvent<UdpAddress> event;
		if (this.target.getVersion() == SnmpConstants.version3) {
			event = snmp.send(getScopedPDU(oids), target, null);
		}
		else {
			event = snmp.send(getPDU(oids), target, null);
		}
		if (event != null) {
			return event;
		}
		throw new RuntimeException("SNMP Get timed out");
	}


	/**
	 * Walk over a subtree
	 * (OctetStrings are rendered as text if printable, as hex otherwise).
	 * @param oid The base OID
	 * @return a map of OIDs -> values
	 */
	public Map<String, String> walkAsString(String oid) throws IOException {
		return walkAsString(oid, false);
	}

	/**
	 * Walk over a subtree.
	 * @param oid The base OID
	 * @param hex whether to always render OctetStrings as hex
	 * @return a map of OIDs -> values
	 */
	public Map<String, String> walkAsString(String oid, boolean hex) throws IOException {
		Map<String, String> results = new TreeMap<String, String>();
		TreeUtils treeUtils = new TreeUtils(snmp, new DefaultPDUFactory(PDU.GETBULK));
		List<TreeEvent> events = treeUtils.getSubtree(target, new OID(oid));
		if (events != null) {
			for (TreeEvent event : events) {
				if (event == null || event.isError()) {
					continue;
				}
				VariableBinding[] varBindings = event.getVariableBindings();
				if (varBindings != null) {
					for (VariableBinding varBinding : varBindings) {
						if (varBinding != null) {
							results.put(varBinding.getOid().toString(), variableToString(varBinding.getVariable(), hex));
						}
					}
				}
			}
		}
		return results;
	}

}
