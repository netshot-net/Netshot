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

import java.io.IOException;

/**
 * Common contract for anything Netshot can connect to a device with
 * (CLI over SSH/Telnet, SNMP, HTTP, ...).
 */
public interface Client {

	/**
	 * Connect (or otherwise prepare the transport for use).
	 * @throws IOException if the connection could not be established
	 */
	void connect() throws IOException;

	/**
	 * Disconnect / release the transport.
	 */
	void disconnect();

}
