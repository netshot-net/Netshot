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
package net.netshot.netshot.aaa.auth;

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
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;

/**
 * The Radius class authenticates the users against a RADIUS server.
 */
@Slf4j
public final class Radius {

	/** The AAA logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/**
	 * The settings of the RADIUS authentication.
	 *
	 * @param servers the RADIUS servers
	 * @param authMethod the authentication method
	 * @param nasIdentifier the NAS identifier to send (null for none)
	 */
	record Settings(List<Server> servers, Class<? extends RadiusAuthenticator> authMethod, String nasIdentifier) {

		/**
		 * A RADIUS server.
		 *
		 * @param id the number of the server in the configuration
		 * @param address the address
		 * @param authPort the authentication port
		 * @param acctPort the accounting port
		 * @param timeout the timeout (seconds)
		 * @param secret the shared secret
		 */
		record Server(int id, InetAddress address, int authPort, int acctPort, int timeout, String secret) {
		}

		/** Max number of servers. */
		private static final int MAX_SERVERS = 3;

		/** IP address of a server. */
		static final Setting<String> IP = Setting.text("netshot.aaa.radiusN.ip").live();

		/** Authentication port of a server. */
		static final Setting<Integer> AUTH_PORT = Setting.integer("netshot.aaa.radiusN.authport")
			.defaultValue(1812).range(1, 65535).live();

		/** Accounting port of a server. */
		static final Setting<Integer> ACCT_PORT = Setting.integer("netshot.aaa.radiusN.acctport")
			.defaultValue(1813).range(1, 65535).live();

		/** Timeout (seconds) for a server. */
		static final Setting<Integer> TIMEOUT = Setting.integer("netshot.aaa.radiusN.timeout")
			.defaultValue(5).range(1, 300).live();

		/** Shared secret of a server. */
		static final Setting<String> SECRET = Setting.text("netshot.aaa.radiusN.secret").secret().live();

		/** Authentication method. */
		static final Setting<String> METHOD = Setting.text("netshot.aaa.radius.method").defaultValue("mschapv2").live();

		/** NAS identifier. */
		static final Setting<String> NAS_IDENTIFIER = Setting.text("netshot.aaa.radius.nasidentifier").live();

		/**
		 * Reads the settings.
		 * @param config the configuration
		 * @return the settings
		 */
		static Settings from(Config config) {
			List<Server> servers = new ArrayList<>();
			for (int id = 1; id <= MAX_SERVERS; id++) {
				String ip = config.get(IP.at(id));
				if (ip == null) {
					continue;
				}
				InetAddress address;
				try {
					address = InetAddress.getByName(ip);
				}
				catch (UnknownHostException e) {
					log.error("Invalid IP address for RADIUS server {}. Will be ignored.", id);
					continue;
				}
				int authPort = config.get(AUTH_PORT.at(id));
				int acctPort = config.get(ACCT_PORT.at(id));
				int timeout = config.get(TIMEOUT.at(id));
				String secret = config.get(SECRET.at(id));
				if (secret == null) {
					log.error("No key configured for RADIUS server {}. Will be ignored.", id);
					continue;
				}
				servers.add(new Server(id, address, authPort, acctPort, timeout, secret));
			}
			String method = config.get(METHOD);
			Class<? extends RadiusAuthenticator> authMethod = switch (method) {
				case "pap" -> PAPAuthenticator.class;
				case "chap" -> CHAPAuthenticator.class;
				case "eap-md5" -> EAPMD5Authenticator.class;
				case "eap-mschapv2" -> EAPMSCHAPv2Authenticator.class;
				case "mschapv2" -> MSCHAPv2Authenticator.class;
				default -> {
					log.error("Invalid configured RADIUS method '{}'. Defaulting to MSCHAPv2.", method);
					yield MSCHAPv2Authenticator.class;
				}
			};
			return new Settings(List.copyOf(servers), authMethod, config.get(NAS_IDENTIFIER));
		}
	}

	/**
	 * The RADIUS clients built from the settings, in the order to try them.
	 *
	 * @param settings the settings they are built from
	 * @param clients the clients
	 */
	private record Clients(Settings settings, List<RadiusClient> clients) {
	}

	/** The settings, from the configuration. */
	private static final ConfigStore.Binding<Settings> SETTINGS = Netshot.config().bind(Settings::from);

	/** The current clients. */
	private static Clients clients;

	/**
	 * Gets the clients, built again when the settings changed.
	 * @return the clients
	 */
	private static synchronized Clients clients() {
		Settings settings = SETTINGS.get();
		if (clients == null || clients.settings() != settings) {
			List<RadiusClient> newClients = new ArrayList<>();
			for (Settings.Server server : settings.servers()) {
				try {
					newClients.add(new RadiusClient(server.address(), server.secret(), server.authPort(), server.acctPort(),
						server.timeout()));
					log.info("Added RADIUS server {}", server.address());
				}
				catch (IOException e) {
					log.error("Unable to create the RADIUS client for server {}.", server.id(), e);
				}
			}
			clients = new Clients(settings, List.copyOf(newClients));
		}
		return clients;
	}

	/**
	 * Moves a client which answered first in the list, for the next authentications.
	 * @param from the clients the client was taken from
	 * @param client the client
	 */
	private static synchronized void promote(Clients from, RadiusClient client) {
		if (clients != from) {
			// Rebuilt in the meantime
			return;
		}
		List<RadiusClient> ordered = new ArrayList<>(from.clients());
		ordered.remove(client);
		ordered.add(0, client);
		clients = new Clients(from.settings(), List.copyOf(ordered));
	}

	static {
		AttributeFactory.loadAttributeDictionary("net.jradius.dictionary.AttributeDictionaryImpl");
	}

	public static boolean isAvailable() {
		return !clients().clients().isEmpty();
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
		Clients allClients = clients();
		if (allClients.clients().isEmpty()) {
			return null;
		}
		Settings settings = allClients.settings();
		String nasIdentifier = settings.nasIdentifier();
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
		for (RadiusClient radiusClient : allClients.clients()) {
			AccessRequest request = new AccessRequest(radiusClient, attributeList);
			request.addAttribute(new Attr_UserPassword(password));
			RadiusResponse reply;
			try {
				reply = radiusClient.authenticate(request, settings.authMethod().getDeclaredConstructor().newInstance(), 3);
				if (reply == null) {
					log.error("Request to RADIUS server {} timed out.", radiusClient.getRemoteInetAddress().toString());
					AAA_LOG.error(MarkerFactory.getMarker("AAA"), "Request to RADIUS server {} timed out.",
						radiusClient.getRemoteInetAddress().toString());
				}
				else {
					// We got a reply
					if (!first) {
						promote(allClients, radiusClient);
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
						UiUser user = UiUser.remote(username, level);
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
