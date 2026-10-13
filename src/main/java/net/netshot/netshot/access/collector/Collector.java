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
package net.netshot.netshot.access.collector;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.inventory.device.NetworkAddress;
import net.netshot.netshot.inventory.domain.Domain;

/**
 * A collector is a service running in background to collect data from devices.
 */
@Slf4j
public abstract class Collector extends Thread {

	/**
	 * Settings/config for the current class.
	 */
	public static final class Settings {
		/** The max idle time of user (in seconds). */
		@Getter
		private Map<NetworkAddress, NetworkAddress> clusterIpOverrides;

		/**
		 * Load settings from config.
		 */
		private void load() {
			Map<NetworkAddress, NetworkAddress> ipOverrides = new HashMap<>();
			String overrides = Netshot.getConfig("netshot.cluster.domainipoverride");
			if (overrides != null) {
				for (String override : overrides.split(" +")) {
					String[] parts = override.split("\\|");
					if (parts.length != 2) {
						log.warn("Invalid netshot.cluster.domainipoverride format (check '{}')... ignoring", override);
						continue;
					}
					try {
						NetworkAddress global = NetworkAddress.getNetworkAddress(parts[0]);
						NetworkAddress local = NetworkAddress.getNetworkAddress(parts[1]);
						ipOverrides.put(global, local);
					}
					catch (UnknownHostException e) {
						log.warn("Unable to parse IP address from netshot.cluster.domainipoverride", e);
					}
				}
			}
			synchronized (this) {
				this.clusterIpOverrides = ipOverrides;
			}
		}
	}

	/** Settings for this class. */
	public static final Settings SETTINGS = new Settings();
	
	public static void loadConfig() {
		Collector.SETTINGS.load();
	}


	/**
	 * Find the collector IP address to use for the given domain.
	 * @param domain to look for
	 * @return the collector IP address to upload data to
	 * @throws IllegalArgumentException
	 */
	public static NetworkAddress getCollectorAddress(Domain domain) throws IllegalArgumentException {
		if (domain == null) {
			throw new IllegalArgumentException("Unable to find collector address be cause domain is null");
		}
		NetworkAddress ip = domain.getServer4Address();
		if (ip == null) {
			throw new IllegalArgumentException("Domain %s (%d) has no server address".formatted(domain.getName(), domain.getId()));
		}
		ip = Collector.SETTINGS.clusterIpOverrides.getOrDefault(ip, ip);
		return ip;
	}

}
