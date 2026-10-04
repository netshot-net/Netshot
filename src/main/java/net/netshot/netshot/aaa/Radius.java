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
package net.netshot.netshot.aaa;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import lombok.extern.slf4j.Slf4j;
import net.jradius.client.RadiusClient;
import net.jradius.client.auth.CHAPAuthenticator;
import net.jradius.client.auth.EAPMD5Authenticator;
import net.jradius.client.auth.EAPMSCHAPv2Authenticator;
import net.jradius.client.auth.MSCHAPv2Authenticator;
import net.jradius.client.auth.PAPAuthenticator;
import net.jradius.client.auth.RadiusAuthenticator;
import net.jradius.dictionary.Attr_CallingStationId;
import net.jradius.dictionary.Attr_NASIdentifier;
import net.jradius.dictionary.Attr_NASPort;
import net.jradius.dictionary.Attr_NASPortType;
import net.jradius.dictionary.Attr_ServiceType;
import net.jradius.dictionary.Attr_UserName;
import net.jradius.dictionary.Attr_UserPassword;
import net.jradius.packet.AccessAccept;
import net.jradius.packet.AccessRequest;
import net.jradius.packet.RadiusResponse;
import net.jradius.packet.attribute.AttributeFactory;
import net.jradius.packet.attribute.AttributeList;
import net.netshot.netshot.Netshot;

/**
 * The Radius class authenticates the users against a RADIUS server.
 */
@Slf4j
public final class Radius {

	/** The AAA logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/** The clients. */
	private static List<RadiusClient> clients = new ArrayList<>();

	/** The authentication method. */
	private static Class<? extends RadiusAuthenticator> authMethod = MSCHAPv2Authenticator.class;

	/** The nasIdentifier. */
	private static String nasIdentifier;

	/**
	 * Load server config.
	 *
	 * @param id the id
	 */
	private static void loadServerConfig(int id) {
		String path = String.format("netshot.aaa.radius%d", id);
		String ip = Netshot.getConfig(path + ".ip");
		if (ip == null) {
			return;
		}
		InetAddress address;
		try {
			address = InetAddress.getByName(ip);
		}
		catch (UnknownHostException e) {
			if (ip != null) {
				log.error("Invalid IP address for RADIUS server {}. Will be ignored.", id);
			}
			return;
		}
		int authPort = Netshot.getConfig(path + ".authport", 1812, 1, 65535);
		int acctPort = Netshot.getConfig(path + ".acctport", 1813, 1, 65535);
		int timeout = Netshot.getConfig(path + ".timeout", 5, 1, 300);
		String key = Netshot.getSecretConfig(path + ".secret");
		if (key == null) {
			log.error("No key configured for RADIUS server {}. Will be ignored.", id);
			return;
		}
		RadiusClient client;
		try {
			client = new RadiusClient(address, key, authPort, acctPort, timeout);
		}
		catch (IOException e) {
			log.error("Unable to create the RADIUS client for server {}.", id, e);
			return;
		}
		log.info("Added RADIUS server {}", address);
		String method = Netshot.getConfig("netshot.aaa.radius.method", "mschapv2");
		switch (method) {
			case "pap":
				authMethod = PAPAuthenticator.class;
				break;
			case "chap":
				authMethod = CHAPAuthenticator.class;
				break;
			case "eap-md5":
				authMethod = EAPMD5Authenticator.class;
				break;
			case "eap-mschapv2":
				authMethod = EAPMSCHAPv2Authenticator.class;
				break;
			case "mschapv2":
				break;
			default:
				log.error("Invalid configured RADIUS method '{}'. Defaulting to MSCHAPv2.", method);
		}
		nasIdentifier = Netshot.getConfig("netshot.aaa.radius.nasidentifier", null);
		clients.add(client);
	}

	/**
	 * Load configuration of all servers (up to 4).
	 */
	private static void loadAllServersConfig() {
		synchronized (Radius.clients) {
			Radius.clients = new ArrayList<>();
		}
		for (int i = 1; i < 4; i++) {
			loadServerConfig(i);
		}
	}

	/**
	 * Load the configuration from Netshot config file.
	 */
	public static void loadConfig() {
		Radius.loadAllServersConfig();
	}

	static {
		AttributeFactory.loadAttributeDictionary("net.jradius.dictionary.AttributeDictionaryImpl");
	}

	public static boolean isAvailable() {
		return clients.size() > 0;
	}

	/**
	 * Authenticates a user.
	 *
	 * @param username the username
	 * @param password the password
	 * @param remoteAddress the remote address
	 * @return the resulting user or null if authentication failed
	 */
	public static UiUser authenticate(String username, String password, String remoteAddress) {
		if (!isAvailable()) {
			return null;
		}
		AttributeList attributeList = new AttributeList();
		attributeList.add(new Attr_UserName(username));
		attributeList.add(new Attr_NASPortType(Attr_NASPortType.Virtual));
		attributeList.add(new Attr_NASPort(1));
		if (nasIdentifier != null) {
			attributeList.add(new Attr_NASIdentifier(nasIdentifier));
		}
		if (remoteAddress != null) {
			attributeList.add(new Attr_CallingStationId(remoteAddress));
		}

		boolean first = true;
		List<RadiusClient> allClients;
		synchronized (Radius.clients) {
			allClients = Radius.clients;
		}
		for (RadiusClient radiusClient : allClients) {
			AccessRequest request = new AccessRequest(radiusClient, attributeList);
			request.addAttribute(new Attr_UserPassword(password));
			RadiusResponse reply;
			try {
				reply = radiusClient.authenticate(request, authMethod.getDeclaredConstructor().newInstance(), 3);
				if (reply == null) {
					log.error("Request to RADIUS server {} timed out.", radiusClient.getRemoteInetAddress().toString());
					AAA_LOG.error(MarkerFactory.getMarker("AAA"), "Request to RADIUS server {} timed out.",
						radiusClient.getRemoteInetAddress().toString());
				}
				else {
					// We got a reply
					if (!first) {
						allClients.remove(radiusClient);
						allClients.add(0, radiusClient);
					}
					if (reply instanceof AccessAccept) {
						int level = UiUser.LEVEL_READONLY;
						try {
							Long serviceType = (Long) reply.getAttributeValue(Attr_ServiceType.TYPE);
							if (Attr_ServiceType.AdministrativeUser.equals(serviceType)) {
								level = UiUser.LEVEL_ADMIN;
							}
							else if (Attr_ServiceType.OutboundUser.equals(serviceType)) {
								level = UiUser.LEVEL_EXECUTEREADWRITE;
							}
							else if (Attr_ServiceType.NASPromptUser.equals(serviceType)) {
								level = UiUser.LEVEL_READWRITE;
							}
							else if (Attr_ServiceType.AuthenticateOnly.equals(serviceType)) {
								level = UiUser.LEVEL_OPERATOR;
							}
						}
						catch (Exception e1) {
						}
						AAA_LOG.info(MarkerFactory.getMarker("AAA"), "The user {} passed authentication on RADIUS server {} (with permission level {}).",
							username, radiusClient.getRemoteInetAddress().toString(), level);
						UiUser user = new UiUser(username, level);
						return user;
					}
					else {
						AAA_LOG.info(MarkerFactory.getMarker("AAA"), "The user {} failed authentication on RADIUS server {}.",
							username, radiusClient.getRemoteInetAddress().toString());
						return null;
					}
				}
			}
			catch (Exception e) {
				log.error("Error while authenticating against RADIUS server {}.",
					radiusClient.getRemoteInetAddress().toString(), e);
			}
			first = false;
		}
		return null;
	}

	private Radius() {
	}

}
