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
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.inventory.device.NetworkAddress;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;

/**
 * The common settings and helpers of the collectors (services receiving data from devices: Syslog server,
 * SNMP trap receiver, SSH server).
 */
@Slf4j
public final class Collector {

	private Collector() {
	}

	/**
	 * The settings of the collectors.
	 *
	 * @param clusterIpOverrides the local collector address to use instead of the global address of a domain
	 */
	public record Settings(Map<NetworkAddress, NetworkAddress> clusterIpOverrides) {

		/** The overrides of the domain server addresses, as {@code global|local} pairs separated by spaces. */
		static final Setting<List<String>> DOMAIN_IP_OVERRIDE = Setting.list("netshot.cluster.domainipoverride", " +")
			.defaultValue(List.of()).live();

		/**
		 * Reads the settings.
		 * @param config the configuration
		 * @return the settings
		 */
		static Settings from(Config config) {
			Map<NetworkAddress, NetworkAddress> ipOverrides = new HashMap<>();
			for (String override : config.get(DOMAIN_IP_OVERRIDE)) {
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
			return new Settings(Map.copyOf(ipOverrides));
		}
	}

	/** The settings, from the configuration. */
	private static final ConfigStore.Binding<Settings> SETTINGS = Netshot.config().bind(Settings::from);

	/**
	 * Gets the settings of the collectors.
	 * @return the settings
	 */
	public static Settings settings() {
		return SETTINGS.get();
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
		NetworkAddress ip = domain.server4Address();
		if (ip == null) {
			throw new IllegalArgumentException("Domain %s (%d) has no server address".formatted(domain.name(), domain.id()));
		}
		ip = Collector.settings().clusterIpOverrides().getOrDefault(ip, ip);
		return ip;
	}

}
