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
import java.io.PrintStream;

import org.apache.commons.net.telnet.InvalidTelnetOptionException;
import org.apache.commons.net.telnet.TelnetClient;
import org.apache.commons.net.telnet.WindowSizeOptionHandler;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.work.TaskContext;

/**
 * A Telnet CLI access.
 */
@Slf4j
public class Telnet extends Cli {

	/** Default Telnet TCP port. */
	public static final int DEFAULT_PORT = 23;

	/**
	 * Settings/config for the current class.
	 */
	public static final class Settings {

		/** Telnet connection timeout. */
		@Getter
		private int connectionTimeout;

		/** Telnet receive timeout. */
		@Getter
		private int receiveTimeout;

		/** Telnet command timeout. */
		@Getter
		private int commandTimeout;

		/**
		 * Load settings from config.
		 */
		private void load() {
			this.connectionTimeout = Netshot.getConfig("netshot.cli.telnet.connectiontimeout", 5000, 1, Integer.MAX_VALUE);
			log.debug("The default connection timeout value for Telnet sessions is {}s", this.connectionTimeout);

			this.receiveTimeout = Netshot.getConfig("netshot.cli.telnet.receivetimeout", 60000, 1, Integer.MAX_VALUE);
			log.debug("The default receive timeout value for Telnet sessions is {}s", this.receiveTimeout);

			this.commandTimeout = Netshot.getConfig("netshot.cli.telnet.commandtimeout", 120000, 1, Integer.MAX_VALUE);
			log.debug("The default command timeout value for Telnet sessions is {}s", this.commandTimeout);
		}
	}

	/** Settings for this class. */
	public static final Settings SETTINGS = new Settings();

	/**
	 * Initialize some additional static variables from global configuration.
	 */
	public static void loadConfig() {
		Telnet.SETTINGS.load();
	}

	/**
	 * Embedded class to represent Telnet-specific configuration.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class TelnetConfig {

		/** Type of terminal. Null means "not set" (see {@link #withDefaults}). */
		@Setter
		private String terminalType = null;

		/** Number of columns in the terminal (negotiated via the Telnet Window Size option). Null means "not set". */
		@Setter
		private Integer terminalCols = null;

		/** Number of rows in the terminal (negotiated via the Telnet Window Size option). Null means "not set". */
		@Setter
		private Integer terminalRows = null;

		/**
		 * @param withDefaults true to populate every field with its historical default value,
		 *        false to leave every field {@code null} (a "delta", only ever merged onto
		 *        another config via {@link Telnet#applyTelnetConfig})
		 */
		public TelnetConfig(boolean withDefaults) {
			if (withDefaults) {
				this.terminalType = "vt100";
				this.terminalCols = 80;
				this.terminalRows = 24;
			}
		}
	}

	/** The port. */
	private int port = DEFAULT_PORT;

	/** The telnet. */
	private TelnetClient telnet;

	/** The Telnet connection config, seeded with its historical defaults. */
	private TelnetConfig telnetConfig = new TelnetConfig(true);

	/**
	 * Instantiates a new telnet.
	 *
	 * @param host the host
	 * @param taskContext the current task context
	 */
	public Telnet(String host, TaskContext taskContext) {
		super(host, taskContext);
	}

	/**
	 * Instantiates a new telnet.
	 *
	 * @param host the host
	 * @param port the port
	 * @param taskContext the current task context
	 */
	public Telnet(String host, int port, TaskContext taskContext) {
		this(host, taskContext);
		this.port = port;
		this.connectionTimeout = Telnet.SETTINGS.getConnectionTimeout();
		this.commandTimeout = Telnet.SETTINGS.getCommandTimeout();
		this.receiveTimeout = Telnet.SETTINGS.getReceiveTimeout();
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.device.access.Cli#connect()
	 */
	@Override
	public void connect() throws IOException {
		this.telnet = new TelnetClient(this.telnetConfig.terminalType.toUpperCase());
		telnet.setConnectTimeout(this.connectionTimeout);
		try {
			telnet.addOptionHandler(new WindowSizeOptionHandler(
				this.telnetConfig.terminalCols, this.telnetConfig.terminalRows, true, false, false, false));
		}
		catch (InvalidTelnetOptionException e) {
			log.warn("Unable to register the Telnet Window Size option handler.", e);
		}
		telnet.connect(this.host, this.port);
		telnet.setSoTimeout(this.receiveTimeout);
		this.inStream = telnet.getInputStream();
		this.outStream = new PrintStream(telnet.getOutputStream());
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.device.access.Cli#disconnect()
	 */
	@Override
	public void disconnect() {
		try {
			this.telnet.disconnect();
		}
		catch (Exception e) {
			//
		}
	}

	public TelnetConfig getTelnetConfig() {
		return telnetConfig;
	}

	/**
	 * Merges a (possibly partial) {@link TelnetConfig} onto this session's config -
	 * only the fields set (non-null) in {@code other} are applied, the rest are left
	 * untouched. Mirrors {@link Ssh#applySshConfig}, and is called twice in the same
	 * way: once with the driver-declared config, then again with any {@code client.create(...)}
	 * {@code telnetConfig} advanced-option override.
	 * @param other the config delta to merge in
	 */
	public void applyTelnetConfig(TelnetConfig other) {
		if (other.terminalType != null) {
			this.telnetConfig.terminalType = other.terminalType;
		}
		if (other.terminalCols != null) {
			this.telnetConfig.terminalCols = other.terminalCols;
		}
		if (other.terminalRows != null) {
			this.telnetConfig.terminalRows = other.terminalRows;
		}
	}

}
