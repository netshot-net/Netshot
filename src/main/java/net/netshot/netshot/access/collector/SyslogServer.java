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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.platform.concurrent.NamedThreadFactory;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.platform.lifecycle.Service;
import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.platform.net.Network4Address;

/**
 * A Syslog server receives the syslog messages from devices and triggers
 * snapshots if needed.
 *
 * <p>If the UDP port can't be opened (or the socket fails), the server keeps trying to open it again,
 * with an increasing delay (up to a minute), and reports itself as unhealthy in the meantime.</p>
 */
@Slf4j
public final class SyslogServer implements Service {

	/** Where the scripts of the drivers log when they analyze a message. */
	private static final TaskContext DRIVER_LOG = new DeviceDriver.ReceiverTaskContext(SyslogServer.class);

	/** Whether the Syslog server is disabled. */
	public static final Setting<Boolean> DISABLED = Setting.bool("netshot.syslog.disabled").defaultValue(false).restart();

	/** The UDP port to listen Syslog messages on. */
	static final Setting<Integer> PORT = Setting.integer("netshot.syslog.port").defaultValue(514).range(1, 65535).restart();

	/** Max delay (ms) before trying to open the socket again. */
	private static final long MAX_RETRY_DELAY = 60000;

	/** The loaded device drivers. */
	private final DriverRegistry driverRegistry;

	/** Where the configuration changes are reported (the scheduler of the automatic snapshots). */
	private final SnapshotRequester snapshotRequester;

	/** The configuration store. */
	private final ConfigStore config;

	/** The number of received messages. */
	private final AtomicLong received = new AtomicLong();

	/** The number of received messages matched by a driver. */
	private final AtomicLong matched = new AtomicLong();

	/** The number of socket errors. */
	private final AtomicLong errors = new AtomicLong();

	/** The UDP port (read when the server starts). */
	private int udpPort;

	/** Whether the server is running. */
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
	public String name() {
		return "Syslog server";
	}

	@Override
	public synchronized void start() {
		this.udpPort = this.config.current().get(PORT);
		this.running = true;
		this.executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("NetshotSyslogServer"));
		this.executor.execute(this::run);
	}

	@Override
	public void stop() throws InterruptedException {
		this.running = false;
		DatagramSocket current = this.socket;
		if (current != null) {
			// Unblocks the receive call
			current.close();
		}
		this.executor.shutdownNow();
		this.executor.awaitTermination(10, TimeUnit.SECONDS);
	}

	@Override
	public boolean isHealthy() {
		return this.running && this.socket != null;
	}

	@Override
	public Map<String, Object> stats() {
		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("port", this.udpPort);
		stats.put("listening", this.socket != null);
		stats.put("received", this.received.get());
		stats.put("matched", this.matched.get());
		stats.put("errors", this.errors.get());
		return stats;
	}

	/**
	 * The receive loop.
	 */
	private void run() {
		long retryDelay = 1000;
		while (this.running) {
			try (DatagramSocket newSocket = new DatagramSocket(this.udpPort)) {
				this.socket = newSocket;
				log.debug("Now listening for Syslog messages on UDP port {}.", this.udpPort);
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
				this.errors.incrementAndGet();
				log.error("Error with the Syslog server socket (UDP port {}), retrying in {}ms.", this.udpPort, retryDelay, e);
			}
			finally {
				this.socket = null;
			}
			try {
				Thread.sleep(retryDelay);
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
