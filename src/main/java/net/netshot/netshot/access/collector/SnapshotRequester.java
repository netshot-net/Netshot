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

import java.util.List;

import net.netshot.netshot.inventory.device.Network4Address;

/**
 * Where the receivers (Syslog server, SNMP trap receiver) report a configuration change signaled by a device:
 * the scheduler of the automatic snapshots, given by the wiring.
 */
@FunctionalInterface
public interface SnapshotRequester {

	/**
	 * Requests an automatic snapshot of the device which sent the message, if needed.
	 *
	 * @param drivers the names of the drivers which recognized a configuration change in the message
	 * @param source the address the message came from
	 */
	void requestSnapshot(List<String> drivers, Network4Address source);
}
