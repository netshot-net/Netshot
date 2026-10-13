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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * A Syslog server receives the syslog messages from devices and triggers
 * snapshots if needed.
 */
@Slf4j
public class SyslogServer extends Collector {

	/** The socket. */
	private DatagramSocket socket;

	/** The UDP port to listen Syslog messages on. */
	private int udpPort = 514;

	/** The static Syslog server. */
	private static SyslogServer nsSyslogServer;

	private static boolean running;

	public static boolean isRunning() {
		return running;
	}

	/**
	 * Initializes the Syslog server.
	 */
	public static void init() {
		if (Netshot.getConfig("netshot.syslog.disabled", false)) {
			log.warn("The Syslog server is disabled by configuration.");
			return;
		}
		nsSyslogServer = new SyslogServer();
		nsSyslogServer.start();
	}

	/**
	 * Instantiates a new syslog server.
	 */
	public SyslogServer() {
		this.setName("Syslog Receiver");
		this.setDaemon(true);
		this.udpPort = Netshot.getConfig("netshot.syslog.port", 514, 1, 65535);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {

		try {
			socket = new DatagramSocket(udpPort);
			log.debug("Now listening for Syslog messages on UDP port {}.",
				udpPort);
			running = true;
			while (true) {
				DatagramPacket dato = new DatagramPacket(new byte[4096], 4096);
				socket.receive(dato);
				InetAddress address = dato.getAddress();
				List<String> matchingDrivers = new ArrayList<>();
				if (address instanceof Inet4Address ip4Address) {
					Network4Address source = new Network4Address(ip4Address, 32);
					String message = new String(dato.getData(), 0, dato.getLength());
					log.trace("Received Syslog message: '{}'.", message);

					for (DeviceDriver driver : DeviceDriver.getAllDrivers()) {
						if (driver.analyzeSyslog(message, source)) {
							matchingDrivers.add(driver.getName());
						}
					}
					if (matchingDrivers.size() > 0) {
						TakeSnapshotTask.scheduleSnapshotIfNeeded(matchingDrivers, source);
					}
				}
			}
		}
		catch (SocketException e) {
			log.error("Error while starting the Syslog server.", e);
		}
		catch (IOException e) {
			log.error("Error while receivng Syslog server datagram.", e);
		}
		catch (Exception e) {
			log.error("Error with the Syslog server", e);
		}
		finally {
			running = false;
			socket.close();
		}
		log.error("The Syslog server is stopping due to an error.");
	}

}
