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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import com.augur.tacacs.Argument;
import com.augur.tacacs.AuthenReply;
import com.augur.tacacs.AuthorReply;
import com.augur.tacacs.SessionClient;
import com.augur.tacacs.TAC_PLUS.ACCT;
import com.augur.tacacs.TAC_PLUS.AUTHEN.METH;
import com.augur.tacacs.TAC_PLUS.AUTHEN.SVC;
import com.augur.tacacs.TAC_PLUS.AUTHEN.TYPE;
import com.augur.tacacs.TAC_PLUS.PRIV_LVL;
import com.augur.tacacs.TacacsClient;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;

/**
 * The Tacacs class authenticates the users against a TACACS+ server.
 */
@Slf4j
public final class Tacacs {

	/** The AAA logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/**
	 * The settings of the TACACS+ authentication.
	 *
	 * @param servers the servers, as {@code address:port}
	 * @param keys the keys of the servers (same order)
	 * @param timeout the timeout (seconds)
	 * @param accounting whether the accounting is enabled
	 * @param roleAttribute the name of the TACACS+ attribute carrying the role
	 * @param roles the levels by role name (value of the role attribute)
	 */
	record Settings(List<String> servers, List<String> keys, int timeout, boolean accounting,
		String roleAttribute, Map<String, Integer> roles) {

		/** Max number of servers. */
		private static final int MAX_SERVERS = 3;

		/** IP address of a server. */
		static final Setting<String> IP = Setting.text("netshot.aaa.tacacsN.ip").live();

		/** Port of a server. */
		static final Setting<Integer> PORT = Setting.integer("netshot.aaa.tacacsN.port").defaultValue(49).range(1, 65535).live();

		/** Key of a server. */
		static final Setting<String> SECRET = Setting.text("netshot.aaa.tacacsN.secret").secret().live();

		/** Timeout (seconds). */
		static final Setting<Integer> TIMEOUT = Setting.integer("netshot.aaa.tacacs.timeout").defaultValue(5).range(1, 300).live();

		/** Whether the accounting is enabled. */
		static final Setting<Boolean> ACCOUNTING = Setting.bool("netshot.aaa.tacacs.accounting").defaultValue(false).live();

		/** Name of the attribute carrying the role. */
		static final Setting<String> ROLE_ATTRIBUTE = Setting.text("netshot.aaa.tacacs.role.attributename").defaultValue("role").live();

		/** Value of the role attribute for the read-only role. */
		static final Setting<String> READONLY_ROLE = roleSetting(User.Role.READONLY);

		/** Value of the role attribute for the operator role. */
		static final Setting<String> OPERATOR_ROLE = roleSetting(User.Role.OPERATOR);

		/** Value of the role attribute for the read-write role. */
		static final Setting<String> READWRITE_ROLE = roleSetting(User.Role.READWRITE);

		/** Value of the role attribute for the execute-read-write role. */
		static final Setting<String> EXECUTEREADWRITE_ROLE = roleSetting(User.Role.EXECUTEREADWRITE);

		/** Value of the role attribute for the admin role. */
		static final Setting<String> ADMIN_ROLE = roleSetting(User.Role.ADMIN);

		private static Setting<String> roleSetting(User.Role role) {
			return Setting.text("netshot.aaa.tacacs.role.%srole".formatted(role.getName().replaceAll("-", "")))
				.defaultValue(role.getName()).live();
		}

		/**
		 * Reads the settings.
		 * @param config the configuration
		 * @return the settings
		 */
		static Settings from(Config config) {
			List<String> hosts = new ArrayList<>();
			List<String> keys = new ArrayList<>();
			for (int id = 1; id <= MAX_SERVERS; id++) {
				String ip = config.get(IP.at(id));
				if (ip == null) {
					continue;
				}
				InetAddress address;
				try {
					address = InetAddress.getByName(ip);
				}
				catch (Exception e) {
					log.error("Invalid IP address for TACACS+ server {}. Will be ignored.", id);
					continue;
				}
				int port = config.get(PORT.at(id));
				String key = config.get(SECRET.at(id));
				if (key == null) {
					log.error("No key configured for TACACS+ server {}. Will be ignored.", id);
					continue;
				}
				hosts.add(String.format("%s:%d", address.getHostAddress(), port));
				keys.add(key);
			}
			boolean accounting = config.get(ACCOUNTING);
			if (accounting) {
				log.info("TACACS+ accounting is enabled");
			}
			Map<String, Integer> roles = new HashMap<>();
			for (User.Role role : User.Role.values()) {
				Setting<String> roleSetting = switch (role) {
					case READONLY -> READONLY_ROLE;
					case OPERATOR -> OPERATOR_ROLE;
					case READWRITE -> READWRITE_ROLE;
					case EXECUTEREADWRITE -> EXECUTEREADWRITE_ROLE;
					case ADMIN -> ADMIN_ROLE;
				};
				roles.put(config.get(roleSetting), role.getLevel());
			}
			return new Settings(List.copyOf(hosts), List.copyOf(keys), config.get(TIMEOUT), accounting,
				config.get(ROLE_ATTRIBUTE), Map.copyOf(roles));
		}
	}

	/**
	 * The TACACS+ client built from the settings.
	 *
	 * @param settings the settings it is built from
	 * @param client the client, null if no server is configured
	 */
	private record Client(Settings settings, TacacsClient client) {
	}

	/** The settings, from the configuration. */
	private final ConfigStore.Binding<Settings> settings;

	/** The current client. */
	private Client currentClient;

	/**
	 * Gets the client, built again when the settings changed.
	 * @return the client
	 */
	private synchronized Client client() {
		Settings current = this.settings.get();
		if (this.currentClient == null || this.currentClient.settings() != current) {
			TacacsClient tacacsClient = null;
			if (!current.servers().isEmpty()) {
				tacacsClient = new TacacsClient(String.join(", ", current.servers()), String.join(", ", current.keys()),
					current.timeout() * 1000, false);
				log.info("Using TACACS+ servers {}", current.servers());
			}
			this.currentClient = new Client(current, tacacsClient);
		}
		return this.currentClient;
	}

	public boolean isAvailable() {
		return client().client() != null;
	}

	/**
	 * Authenticates a user using TACACS+.
	 *
	 * @param username = the username
	 * @param password = the password
	 * @param remoteAddress = the remote address of the user
	 * @return the resulting user or null if authentication failed
	 */
	public UiUser authenticate(String username, String password, String remoteAddress) {
		Client current = client();
		TacacsClient client = current.client();
		if (client == null) {
			return null;
		}
		String roleAttribute = current.settings().roleAttribute();
		Map<String, Integer> roles = current.settings().roles();

		try {
			SessionClient authenSession = client.newSession(SVC.LOGIN, "rest", remoteAddress, PRIV_LVL.USER.code());
			AuthenReply authenReply = authenSession.authenticate_ASCII(username, password);

			if (authenReply.isOK()) {
				SessionClient authoSession = client.newSession(SVC.LOGIN, "rest", remoteAddress, PRIV_LVL.USER.code());
				AuthorReply authoReply = authoSession.authorize(
					username,
					METH.TACACSPLUS,
					TYPE.ASCII,
					SVC.LOGIN,
					new Argument[] { new Argument("service=netshot-rest") }
				);
				if (authoReply.isOK()) {
					int level = UiUser.LEVEL_READONLY;
					String passedRole = authoReply.getValue(roleAttribute);
					AAA_LOG.debug(MarkerFactory.getMarker("AAA"), "The TACACS+ server returned role: {}", passedRole);

					if (passedRole == null) {
						AAA_LOG.warn("No role returned from the TACACS+ server for user {} using the {} attribute, user will be read only", username, roleAttribute);
					}
					for (Map.Entry<String, Integer> role : roles.entrySet()) {
						if (role.getKey().equals(passedRole)) {
							level = role.getValue();
						}
					}

					AAA_LOG.info(MarkerFactory.getMarker("AAA"),
						"The user {} passed TACACS+ authentication (with permission level {}).",
						username, level);
					UiUser user = UiUser.remote(username, level);
					return user;
				}
				else {
					// Authorization failed
					if (authoReply.getData() != null) {
						AAA_LOG.info(MarkerFactory.getMarker("AAA"),
							"The user {} failed TACACS+ authorization. Server data: {}.",
							username, authoReply.getData());
					}
					else if (authoReply.getServerMsg() != null) {
						AAA_LOG.info(MarkerFactory.getMarker("AAA"),
							"The user {} failed TACACS+ authorization. Server message: {}.",
							username, authoReply.getServerMsg());
					}
					else {
						AAA_LOG.info(MarkerFactory.getMarker("AAA"),
							"The user {} failed TACACS+ authorization.", username);
					}
				}
			}
			else {
				// Authentication failed
				if (authenReply.getData() != null) {
					AAA_LOG.info(MarkerFactory.getMarker("AAA"),
						"The user {} failed TACACS+ authentication. Server data: {}.",
						username, authenReply.getData());
				}
				else if (authenReply.getServerMsg() != null) {
					AAA_LOG.info(MarkerFactory.getMarker("AAA"),
						"The user {} failed TACACS+ authentication. Server message: {}.",
						username, authenReply.getServerMsg());
				}
				else {
					AAA_LOG.info(MarkerFactory.getMarker("AAA"),
						"The user {} failed TACACS+ authentication.", username);
				}
			}
		}
		catch (Exception e) {
			log.error("Error while authenticating against RADIUS server.", e);
		}
		finally {
			client.shutdown();
		}
		return null;
	}

	/**
	 * Log a message with TACACS+ accounting.
	 * @param method = the called http method
	 * @param path = the called http path
	 * @param username = the username
	 * @param response = the response
	 * @param remoteAddress = the user IP address
	 */
	public void account(String method, String path, String username, String response, String remoteAddress) {
		Client current = client();
		TacacsClient client = current.client();
		if (!current.settings().accounting() || client == null) {
			return;
		}
		SessionClient acctSession = client.newSession(SVC.LOGIN, "rest", remoteAddress, PRIV_LVL.USER.code());
		try {
			acctSession.account(ACCT.FLAG.STOP.code(), username, METH.TACACSPLUS, TYPE.ASCII, SVC.LOGIN, new Argument[] {
				new Argument(String.format("%s %s => %s", method, path, response))
			});
		}
		catch (TimeoutException | IOException e) {
			log.warn("Error while sending TACACS+ accounting message", e);
		}
	}

	/**
	 * Creates the TACACS+ authentication.
	 *
	 * @param config the configuration store, to read the settings from
	 */
	public Tacacs(ConfigStore config) {
		this.settings = config.bind(Settings::from);
	}
}
