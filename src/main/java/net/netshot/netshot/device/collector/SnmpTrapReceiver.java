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
package net.netshot.netshot.device.collector;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.Network4Address;
import net.netshot.netshot.work.tasks.TakeSnapshotTask;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.AuthenticationFailureEvent;
import org.snmp4j.event.AuthenticationFailureListener;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
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
import org.snmp4j.security.SecurityModel;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.ThreadPool;

/**
 * A SNMP trap receiver listens for SNMP traps and triggers snapshots if needed.
 */
@Slf4j
public class SnmpTrapReceiver implements CommandResponder, AuthenticationFailureListener {

	/** The static SNMP trap receiver instance. */
	private static SnmpTrapReceiver nsSnmpTrapReceiver;

	public static boolean isRunning() {
		return nsSnmpTrapReceiver != null && nsSnmpTrapReceiver.snmp != null;
	}

	/**
	 * Initializes the trap receiver.
	 */
	public static void init() {
		nsSnmpTrapReceiver = new SnmpTrapReceiver();
		nsSnmpTrapReceiver.loadConfig();
	}

	/**
	 * Reload the configuration, restart the receiver if needed.
	 */
	public static void reload() {
		nsSnmpTrapReceiver.loadConfig();
	}

	/** The dispatcher. */
	private MultiThreadedMessageDispatcher dispatcher;

	/** The SNMP object. */
	private Snmp snmp;

	/** The UDP port (e.g. 162)  */
	private int udpPort;

	/** The listen address (e.g. 0.0.0.0 or 127.0.0.1) */
	private InetAddress listenHost;

	/** The UDP address. */
	private UdpAddress udpAddress;

	/** Number of threads. */
	private int threadCount;

	/** The thread pool. */
	private ThreadPool threadPool;

	/** The communities. */
	private Set<String> communities = ConcurrentHashMap.newKeySet();

	/** The SNMPv3 engine ID. */
	private OctetString engineId;

	/** The USM users. */
	private List<UsmUser> usmUsers;

	/**
	 * Instantiates a new snmp trap receiver.
	 */
	public SnmpTrapReceiver() {
	}

	private void loadConfig() {
		if (Netshot.getConfig("netshot.snmptrap.disabled", false)) {
			log.warn("The SNMP trap receiver is disabled by configuration.");
			this.stop();
			return;
		}

		boolean restartNeeded = false;

		int port = Netshot.getConfig("netshot.snmptrap.port", 162, 1, 65535);
		if (port != this.udpPort) {
			this.udpPort = port;
			restartNeeded = true;
		}

		String listenAddress = Netshot.getConfig("netshot.snmptrap.listenaddress", "0.0.0.0");
		InetAddress address;
		try {
			address = InetAddress.getByName(listenAddress);
		}
		catch (UnknownHostException e) {
			log.warn("Unable to parse IP address '{}' for SNMP listen address, using loopback instead",
				listenAddress, e);
			address = InetAddress.getLoopbackAddress();
		}
		if (!address.equals(this.listenHost)) {
			this.listenHost = address;
			restartNeeded = true;
		}

		String communityConfig = Netshot.getSecretConfig("netshot.snmptrap.community", "");
		communities.clear();
		try {
			for (String community : communityConfig.split("\\s+")) {
				if (!community.isBlank()) {
					communities.add(community);
				}
			}
		}
		catch (Exception e) {
			log.warn("Error while parsing SNMP trap community option", e);
		}

		int count = Netshot.getConfig("netshot.snmptrap.threadcount", 2, 1, 32);
		if (count != this.threadCount) {
			this.threadCount = count;
			restartNeeded = true;
		}

		this.usmUsers = new ArrayList<>();
		String userConfig = Netshot.getSecretConfig("netshot.snmptrap.user", "");
		// Example:   user1 AES128|SHA authpass1 privpass1 user2 AES192|AES256|HMAC128SHA224 authpass2 privpass2
		// i.e. {user} {list of auth/priv protocols with pipe separators} {auth pass if needed} {priv pass if needed}
		try {
			String userName = null;
			Iterator<String> part = Arrays.asList(userConfig.split(" +")).iterator();
			while (part.hasNext()) {
				userName = part.next();
				Set<OID> authProtocols = new HashSet<>();
				Set<OID> privProtocols = new HashSet<>();
				String authKey = null;
				String privKey = null;
				if (part.hasNext()) {
					String protocols = part.next();
					for (String protocol : protocols.split("\\|")) {
						if ("DES".equals(protocol)) {
							privProtocols.add(PrivDES.ID);
						}
						else if ("3DES".equals(protocol)) {
							privProtocols.add(Priv3DES.ID);
						}
						else if ("AES128".equals(protocol)) {
							privProtocols.add(PrivAES128.ID);
						}
						else if ("AES192".equals(protocol)) {
							privProtocols.add(PrivAES192.ID);
						}
						else if ("AES256".equals(protocol)) {
							privProtocols.add(PrivAES256.ID);
						}
						else if ("MD5".equals(protocol)) {
							authProtocols.add(AuthMD5.ID);
						}
						else if ("SHA".equals(protocol) || "SHA1".equals(protocol)) {
							authProtocols.add(AuthSHA.ID);
						}
						else if ("HMAC128SHA224".equals(protocol)) {
							authProtocols.add(AuthHMAC128SHA224.ID);
						}
						else if ("HMAC192SHA256".equals(protocol)) {
							authProtocols.add(AuthHMAC192SHA256.ID);
						}
						else if ("HMAC256SHA384".equals(protocol)) {
							authProtocols.add(AuthHMAC256SHA384.ID);
						}
						else if ("HMAC384SHA512".equals(protocol)) {
							authProtocols.add(AuthHMAC384SHA512.ID);
						}
						else {
							throw new InvalidParameterException(
								String.format("Invalid SNMPv3 protocol '%s'... ignoring SNMPv3 user config", protocol));
						}
					}
				}
				if (authProtocols.size() >= 1) {
					if (part.hasNext()) {
						authKey = part.next();
					}
					else {
						throw new InvalidParameterException("No authentication key provided for user " + userName
							+ "... ignoring SNMPv3 user config");
					}
				}
				else {
					authProtocols.add(null);
				}
				if (privProtocols.size() >= 1) {
					if (part.hasNext()) {
						privKey = part.next();
					}
					else {
						throw new InvalidParameterException("No privacy key provided for user " + userName
							+ "... ignoring SNMPv3 user config");
					}
				}
				else {
					privProtocols.add(null);
				}
				for (OID authProtocol : authProtocols) {
					for (OID privProtocol : privProtocols) {
						UsmUser usmUser = new UsmUser(new OctetString(userName),
							authProtocol, authKey == null ? null : new OctetString(authKey),
							privProtocol, privKey == null ? null : new OctetString(privKey));
						this.usmUsers.add(usmUser);
					}
				}
			}
		}
		catch (Exception e) {
			log.warn("Error while parsing SNMP trap user option", e);
		}

		if (this.usmUsers.size() > 0) {
			OctetString newEngineId = null;
			if (this.engineId != null) {
				newEngineId = OctetString.fromByteArray(this.engineId.toByteArray());
			}
			try {
				newEngineId = OctetString.fromHexString(Netshot.getConfig("netshot.snmptrap.engineid"));
			}
			catch (Exception e) {
				// Ignore
			}
			if (newEngineId == null) {
				newEngineId = OctetString.fromByteArray(MPv3.createLocalEngineID());
				log.warn(
					"Couldn't read/parse SNMP engine ID, a random one {} will be used... please set a static one in config file",
					newEngineId.toHexString());
			}
			if (!newEngineId.equals(this.engineId)) {
				this.engineId = newEngineId;
				restartNeeded = true;
				log.info("SNMP local engine ID is {}", this.engineId.toHexString());
			}
		}

		if (restartNeeded) {
			this.stop();
		}
		this.start();
		if (this.snmp.getUSM() != null) {
			this.snmp.getUSM().setUsers(this.usmUsers.toArray(UsmUser[]::new));
		}
	}

	/**
	 * Stop the trap receiver.
	 */
	private void stop() {
		if (this.snmp == null) {
			return;
		}

		try {
			this.snmp.close();
			this.snmp = null;
		}
		catch (IOException e) {
			log.warn("Cannot close SNMP trap receiver", e);
		}
	}

	/**
	 * Start the trap receiver.
	 */
	private void start() {
		if (this.snmp != null) {
			// Already running
			return;
		}
		try {
			threadPool = ThreadPool.create("SNMP Receiver Pool", this.threadCount);
			MessageDispatcherImpl effDispatcher = new MessageDispatcherImpl();
			effDispatcher.addAuthenticationFailureListener(this);
			dispatcher = new MultiThreadedMessageDispatcher(threadPool, effDispatcher);
			dispatcher.addMessageProcessingModel(new MPv1());
			dispatcher.addMessageProcessingModel(new MPv2c());
			if (this.engineId != null) {
				dispatcher.addMessageProcessingModel(new MPv3(this.engineId.getValue()));
			}

			SecurityProtocols securityProtocols = SecurityProtocols.getInstance();
			securityProtocols.addPrivacyProtocol(new PrivDES());
			securityProtocols.addPrivacyProtocol(new Priv3DES());
			securityProtocols.addPrivacyProtocol(new PrivAES128());
			securityProtocols.addPrivacyProtocol(new PrivAES192());
			securityProtocols.addPrivacyProtocol(new PrivAES256());
			securityProtocols.addPrivacyProtocol(new PrivAES256());
			securityProtocols.addAuthenticationProtocol(new AuthMD5());
			securityProtocols.addAuthenticationProtocol(new AuthSHA());
			securityProtocols.addAuthenticationProtocol(new AuthHMAC128SHA224());
			securityProtocols.addAuthenticationProtocol(new AuthHMAC192SHA256());
			securityProtocols.addAuthenticationProtocol(new AuthHMAC256SHA384());
			securityProtocols.addAuthenticationProtocol(new AuthHMAC384SHA512());

			USM usm = new USM(securityProtocols, this.engineId, 0);
			usm.setUsers(this.usmUsers.toArray(UsmUser[]::new));
			SecurityModels securityModels = SecurityModels.getInstance();

			securityModels.addSecurityModel(usm);

			udpAddress = new UdpAddress(this.listenHost, this.udpPort);
			TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping(
				udpAddress);


			snmp = new Snmp(dispatcher, transport);
			snmp.addCommandResponder(this);
			snmp.listen();
			log.info("Now listening for SNMP traps on UDP port {}.", this.udpPort);
		}
		catch (IOException e) {
			log.error("I/O error with the SNMP trap receiver.", e);
			this.stop();
		}
	}

	/**
	 * Process a trap that is already validated (community or user based).
	 * @param <A> = type of transport address
	 * @param event = the received event
	 */
	private <A extends Address> void processAuthenticatedTrap(CommandResponderEvent<A> event) {
		Address address = event.getPeerAddress();
		if (address instanceof IpAddress ipAddress) {
			InetAddress inetAddress = ipAddress.getInetAddress();
			if (inetAddress instanceof Inet4Address ip4Address) {
				try {
					Network4Address source = new Network4Address(ip4Address, 32);
					Map<String, Object> data = new HashMap<>();
					for (VariableBinding var : event.getPDU().getVariableBindings()) {
						data.put(var.getOid().toDottedString(), var.getVariable().toString());
					}
					switch (event.getSecurityModel()) {
						case SecurityModel.SECURITY_MODEL_SNMPv1:
							data.put("version", "1");
							break;
						case SecurityModel.SECURITY_MODEL_SNMPv2c:
							data.put("version", "2c");
							break;
						case SecurityModel.SECURITY_MODEL_USM:
							data.put("version", "3");
							break;
						default:
							data.put("version", "Unknown");
					}
					List<String> matchingDrivers = new ArrayList<>();
					for (DeviceDriver driver : DeviceDriver.getAllDrivers()) {
						if (driver.analyzeTrap(data, source)) {
							matchingDrivers.add(driver.getName());
						}
					}
					if (matchingDrivers.size() > 0) {
						TakeSnapshotTask.scheduleSnapshotIfNeeded(matchingDrivers, source);
					}
				}
				catch (Exception e) {
					log.warn("Error on trap received from {}.", address, e);
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.snmp4j.CommandResponder#processPdu(org.snmp4j.CommandResponderEvent)
	 */
	public <A extends Address> void processPdu(CommandResponderEvent<A> event) {
		log.trace("Incoming SNMP message from {}.", event.getPeerAddress());
		if (event.getSecurityLevel() == SecurityLevel.NOAUTH_NOPRIV
			&& (event.getSecurityModel() == SecurityModel.SECURITY_MODEL_SNMPv1 || event
			.getSecurityModel() == SecurityModel.SECURITY_MODEL_SNMPv2c)) {

			String receivedCommunity = new String(event.getSecurityName());

			if (communities.contains(receivedCommunity)) {
				log.trace("SNMPv1/v2c community '{}'' is valid", receivedCommunity);
				this.processAuthenticatedTrap(event);
			}
			else {
				log.warn("Invalid community {} (vs {}) received from {}.", receivedCommunity,
					String.join(" ", communities), event.getPeerAddress());
			}
		}
		else if (event.getSecurityModel() == SecurityModel.SECURITY_MODEL_USM) {
			String userName = OctetString.fromByteArray(event.getSecurityName()).toString();
			log.trace("Accepted trap from user {}", userName);
			this.processAuthenticatedTrap(event);
		}
		else {
			log.warn("Invalid SNMP trap security model {}", event.getSecurityModel());
		}
	}

	@Override
	public <A extends Address> void authenticationFailure(AuthenticationFailureEvent<A> event) {
		log.warn("Authentication failure on SNMP trap received from {}. {}.",
			event.getAddress(), SnmpConstants.mpErrorMessage(event.getError()));
	}

}
