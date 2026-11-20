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
package net.netshot.netshot.access.collector;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.platform.concurrent.NamedThreadFactory;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.platform.lifecycle.Service;
import net.netshot.netshot.platform.lifecycle.ServiceManager;
import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.platform.net.Network4Address;

/**
 * A Syslog server receives the syslog messages from devices and triggers
 * snapshots if needed.
 *
 * <p>If the UDP port can't be opened (or the socket fails), the server keeps trying to open it again,
 * with an increasing delay (up to a minute), and reports itself as unhealthy in the meantime. The settings are applied
 * live: a change of the port or of the listen address opens the socket again, disabling the server closes it (a
 * disabled server is healthy).</p>
 */
@Slf4j
public final class SyslogServer implements Service {

	/** The name of the service (also while disabled, when it isn't built). */
	public static final String NAME = "Syslog server";

	/** Where the scripts of the drivers log when they analyze a message. */
	private static final TaskContext DRIVER_LOG = new DeviceDriver.ReceiverTaskContext(SyslogServer.class);

	/** Whether the Syslog server is enabled (legacy: {@code netshot.syslog.disabled}, inverted). */
	static final Setting<Boolean> ENABLED = Setting.bool("netshot.syslog.enabled")
		.alias("netshot.syslog.disabled", disabled -> !disabled).defaultValue(true).live();

	/** The UDP port to listen Syslog messages on. */
	static final Setting<Integer> PORT = Setting.integer("netshot.syslog.port").defaultValue(514).range(1, 65535).live();

	/** The IP address to listen Syslog messages on. */
	static final Setting<String> LISTEN_ADDRESS = Setting.text("netshot.syslog.listenaddress").defaultValue("0.0.0.0").live();

	/**
	 * The settings of the Syslog server.
	 *
	 * @param enabled whether the server is enabled ({@code netshot.syslog.enabled})
	 * @param port the UDP port
	 * @param listenAddress the address to listen on
	 */
	record Settings(boolean enabled, int port, InetAddress listenAddress) {

		/**
		 * Reads the settings from a configuration (an invalid listen address is logged and replaced by the loopback
		 * address).
		 *
		 * @param config the configuration
		 * @return the settings
		 */
		static Settings from(Config config) {
			String listenAddress = config.get(LISTEN_ADDRESS);
			InetAddress host;
			try {
				host = InetAddress.getByName(listenAddress);
			}
			catch (UnknownHostException e) {
				log.warn("Unable to parse IP address '{}' for the Syslog listen address, using loopback instead", listenAddress, e);
				host = InetAddress.getLoopbackAddress();
			}
			return new Settings(config.get(ENABLED), config.get(PORT), host);
		}
	}

	/** Max delay (ms) before trying to open the socket again. */
	private static final long MAX_RETRY_DELAY = 60000;

	/** The loaded device drivers. */
	private final DriverRegistry driverRegistry;

	/** Where the configuration changes are reported (the scheduler of the automatic snapshots). */
	private final SnapshotRequester snapshotRequester;

	/** The configuration store. */
	private final ConfigStore config;

	/** Applies the configuration changes (opening the socket again if needed). */
	private final Consumer<Config> listener = this::applySettings;

	/** Signals the receive loop of a configuration change. */
	private final Object lock = new Object();

	/** The number of received messages. */
	private final AtomicLong received = new AtomicLong();

	/** The number of received messages matched by a driver. */
	private final AtomicLong matched = new AtomicLong();

	/** The number of socket errors. */
	private final AtomicLong errors = new AtomicLong();

	/** The settings (null before the first start). */
	private volatile Settings settings;

	/** Incremented on each change of the listening configuration (the socket is opened again). */
	private volatile long generation;

	/** Whether the server is running: a reload which calls the listener after the stop is ignored. */
	private volatile boolean running;

	/** The socket (null if not open). */
	private volatile DatagramSocket socket;

	/** Runs the receive loop. */
	private ExecutorService executor;

	/**
	 * Creates the Syslog server.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param snapshotRequester where the configuration changes are reported (the scheduler of the automatic snapshots)
	 * @param config the configuration store
	 */
	public SyslogServer(DriverRegistry driverRegistry, SnapshotRequester snapshotRequester, ConfigStore config) {
		this.driverRegistry = driverRegistry;
		this.snapshotRequester = snapshotRequester;
		this.config = config;
	}

	@Override
	public String id() {
		return "syslog-server";
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public synchronized void start() {
		this.running = true;
		// Listening first: a reload while starting is applied, at worst twice
		this.config.addListener(this.listener);
		this.applySettings(this.config.current());
		this.executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("NetshotSyslogServer"));
		this.executor.execute(this::run);
	}

	@Override
	public synchronized void stop() throws InterruptedException {
		this.config.removeListener(this.listener);
		this.running = false;
		synchronized (this.lock) {
			this.lock.notifyAll();
		}
		this.closeSocket();
		if (this.executor != null) {
			this.executor.shutdownNow();
			this.executor.awaitTermination(10, TimeUnit.SECONDS);
		}
	}

	@Override
	public boolean isHealthy() {
		Settings current = this.settings;
		return (current != null && !current.enabled()) || (this.running && this.socket != null);
	}

	@Override
	public Map<String, Object> stats() {
		Map<String, Object> stats = new LinkedHashMap<>();
		Settings current = this.settings;
		stats.put("enabled", current != null && current.enabled());
		stats.put("port", current == null ? 0 : current.port());
		stats.put("listening", this.socket != null);
		stats.put("received", this.received.get());
		stats.put("matched", this.matched.get());
		stats.put("errors", this.errors.get());
		return stats;
	}

	/**
	 * Applies the configuration: opens the socket again when the port or the listen address changes, closes it
	 * when the server is disabled.
	 *
	 * @param newConfig the configuration
	 */
	private void applySettings(Config newConfig) {
		if (!this.running) {
			return;
		}
		Settings newSettings = Settings.from(newConfig);
		DatagramSocket formerSocket;
		synchronized (this.lock) {
			if (newSettings.equals(this.settings)) {
				return;
			}
			if (!newSettings.enabled()) {
				ServiceManager.logDisabled(NAME, ENABLED);
			}
			this.settings = newSettings;
			this.generation++;
			// Not listening with the new settings until the loop opens the socket again
			formerSocket = this.socket;
			this.socket = null;
			this.lock.notifyAll();
		}
		// Unblocks the receive call, the loop opens the socket again (or waits while disabled)
		if (formerSocket != null) {
			formerSocket.close();
		}
	}

	private void closeSocket() {
		DatagramSocket current = this.socket;
		if (current != null) {
			current.close();
		}
	}

	/**
	 * The receive loop.
	 */
	private void run() {
		long retryDelay = 1000;
		while (this.running) {
			long currentGeneration;
			Settings current;
			synchronized (this.lock) {
				while (this.running && !this.settings.enabled()) {
					try {
						this.lock.wait();
					}
					catch (InterruptedException e) {
						return;
					}
				}
				currentGeneration = this.generation;
				current = this.settings;
			}
			if (!this.running) {
				break;
			}
			InetSocketAddress bindAddress = new InetSocketAddress(current.listenAddress(), current.port());
			try (DatagramSocket newSocket = new DatagramSocket(bindAddress)) {
				synchronized (this.lock) {
					if (currentGeneration != this.generation) {
						// Changed while opening the socket
						continue;
					}
					this.socket = newSocket;
				}
				log.debug("Now listening for Syslog messages on {}.", bindAddress);
				retryDelay = 1000;
				while (this.running) {
					DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
					newSocket.receive(packet);
					this.received.incrementAndGet();
					this.process(packet);
				}
			}
			catch (IOException e) {
				if (!this.running) {
					break;
				}
				if (currentGeneration != this.generation) {
					// Closed by a configuration change: opened again right away
					continue;
				}
				this.errors.incrementAndGet();
				log.error("Error with the Syslog server socket ({}), retrying in {}ms.", bindAddress, retryDelay, e);
			}
			finally {
				// Only this loop sets a socket: this one, or null already after a configuration change
				this.socket = null;
			}
			try {
				synchronized (this.lock) {
					// Woken up early by a configuration change
					if (currentGeneration == this.generation && this.running) {
						this.lock.wait(retryDelay);
					}
				}
			}
			catch (InterruptedException e) {
				break;
			}
			retryDelay = Math.min(retryDelay * 2, MAX_RETRY_DELAY);
		}
		log.debug("The Syslog server is stopped.");
	}

	/**
	 * Processes a received message: asks the drivers whether a snapshot is needed.
	 *
	 * @param packet the received packet
	 */
	private void process(DatagramPacket packet) {
		try {
			InetAddress address = packet.getAddress();
			if (address instanceof Inet4Address ip4Address) {
				Network4Address source = new Network4Address(ip4Address, 32);
				String message = new String(packet.getData(), 0, packet.getLength());
				log.trace("Received Syslog message: '{}'.", message);
				List<String> matchingDrivers = new ArrayList<>();
				for (DeviceDriver driver : this.driverRegistry.getAllDrivers()) {
					if (driver.analyzeSyslog(message, source, DRIVER_LOG)) {
						matchingDrivers.add(driver.getName());
					}
				}
				if (matchingDrivers.size() > 0) {
					this.matched.incrementAndGet();
					this.snapshotRequester.requestSnapshot(matchingDrivers, source);
				}
			}
		}
		catch (UnknownHostException | RuntimeException e) {
			log.error("Error while processing a Syslog message", e);
		}
	}
}
