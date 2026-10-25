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
package net.netshot.netshot.access;

import java.io.IOException;
import java.io.PrintStream;

import org.apache.commons.net.telnet.InvalidTelnetOptionException;
import org.apache.commons.net.telnet.TelnetClient;
import org.apache.commons.net.telnet.WindowSizeOptionHandler;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.task.engine.TaskContext;

/**
 * A Telnet CLI access.
 */
@Slf4j
public class Telnet extends Cli {

	/** Default Telnet TCP port. */
	public static final int DEFAULT_PORT = 23;

	/**
	 * The settings of the Telnet client.
	 *
	 * @param connectionTimeout the connection timeout (ms)
	 * @param receiveTimeout the receive timeout (ms)
	 * @param commandTimeout the command timeout (ms)
	 */
	public record Settings(int connectionTimeout, int receiveTimeout, int commandTimeout) {

		/** Connection timeout (ms). */
		static final Setting<Integer> CONNECTION_TIMEOUT = Setting.integer("netshot.cli.telnet.connectiontimeout")
			.defaultValue(5000).range(1, Integer.MAX_VALUE).live();

		/** Receive timeout (ms). */
		static final Setting<Integer> RECEIVE_TIMEOUT = Setting.integer("netshot.cli.telnet.receivetimeout")
			.defaultValue(60000).range(1, Integer.MAX_VALUE).live();

		/** Command timeout (ms). */
		static final Setting<Integer> COMMAND_TIMEOUT = Setting.integer("netshot.cli.telnet.commandtimeout")
			.defaultValue(120000).range(1, Integer.MAX_VALUE).live();

		/**
		 * Reads the settings.
		 * @param config the configuration
		 * @return the settings
		 */
		static Settings from(Config config) {
			Settings settings = new Settings(config.get(CONNECTION_TIMEOUT), config.get(RECEIVE_TIMEOUT), config.get(COMMAND_TIMEOUT));
			log.debug("The default Telnet settings are {}", settings);
			return settings;
		}
	}

	/** The settings, from the configuration. */
	private static final ConfigStore.Binding<Settings> SETTINGS = Netshot.config().bind(Settings::from);

	/**
	 * Gets the settings of the Telnet client.
	 * @return the settings
	 */
	public static Settings settings() {
		return SETTINGS.get();
	}

	/**
	 * Embedded class to represent Telnet-specific configuration.
	 */
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
		this.connectionTimeout = Telnet.settings().connectionTimeout();
		this.commandTimeout = Telnet.settings().commandTimeout();
		this.receiveTimeout = Telnet.settings().receiveTimeout();
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.access.Cli#connect()
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
	 * @see net.netshot.netshot.access.Cli#disconnect()
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
