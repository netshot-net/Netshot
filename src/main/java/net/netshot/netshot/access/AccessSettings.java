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

import net.netshot.netshot.access.collector.Collector;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;

/**
 * The settings of the device accesses (SSH, Telnet, HTTP, the collector address the devices upload to), bound to the
 * configuration: each getter gives the current settings.
 */
public final class AccessSettings {

	/** The SSH settings. */
	private final ConfigStore.Binding<Ssh.Settings> ssh;

	/** The Telnet settings. */
	private final ConfigStore.Binding<Telnet.Settings> telnet;

	/** The HTTP settings. */
	private final ConfigStore.Binding<Http.Settings> http;

	/** The SNMP settings. */
	private final ConfigStore.Binding<Snmp.Settings> snmp;

	/** The collector settings. */
	private final ConfigStore.Binding<Collector.Settings> collector;

	/**
	 * Binds the settings to the configuration (once: a binding lives as long as the configuration store).
	 *
	 * @param config the configuration store
	 */
	public AccessSettings(ConfigStore config) {
		this.ssh = config.bind(Ssh.Settings::from);
		this.telnet = config.bind(Telnet.Settings::from);
		this.http = config.bind(Http.Settings::from);
		this.snmp = config.bind(Snmp.Settings::from);
		this.collector = config.bind(Collector.Settings::from);
	}

	/**
	 * The settings of the default configuration (for the code which contacts no device).
	 *
	 * @return the settings
	 */
	public static AccessSettings defaults() {
		return new AccessSettings(new ConfigStore(Config.empty()));
	}

	/**
	 * Gets the SSH settings.
	 *
	 * @return the settings
	 */
	public Ssh.Settings ssh() {
		return this.ssh.get();
	}

	/**
	 * Gets the Telnet settings.
	 *
	 * @return the settings
	 */
	public Telnet.Settings telnet() {
		return this.telnet.get();
	}

	/**
	 * Gets the HTTP settings.
	 *
	 * @return the settings
	 */
	public Http.Settings http() {
		return this.http.get();
	}

	/**
	 * Gets the SNMP settings.
	 *
	 * @return the settings
	 */
	public Snmp.Settings snmp() {
		return this.snmp.get();
	}

	/**
	 * Gets the collector settings (the IP addresses overridden for the cluster members).
	 *
	 * @return the settings
	 */
	public Collector.Settings collector() {
		return this.collector.get();
	}
}
