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
package net.netshot.netshot.platform.database.migrator;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 * Conversion of the numeric IP address representations stored by old schema
 * versions, to text. A frozen copy of what the inventory address classes did
 * at the time: a migrator must not follow the evolutions of the application
 * code (and the platform must not depend on the features).
 */
final class AddressConverter {

	/**
	 * Converts an IPv4 address stored as an integer to its dotted representation.
	 *
	 * @param address the address
	 * @return the text form
	 */
	static String intToIP(int address) {
		int b1 = address & 0xFF;
		int b2 = (address >> 8) & 0xFF;
		int b3 = (address >> 16) & 0xFF;
		int b4 = (address >> 24) & 0xFF;
		return String.format("%d.%d.%d.%d", b4, b3, b2, b1);
	}

	/**
	 * Converts an IPv6 address stored as two longs to its text representation.
	 *
	 * @param address1 the first 64 bits
	 * @param address2 the last 64 bits
	 * @return the text form
	 */
	static String intToIP(long address1, long address2) {
		ByteBuffer buffer = ByteBuffer.allocate(16);
		buffer.putLong(address1);
		buffer.putLong(address2);
		try {
			return InetAddress.getByAddress(buffer.array()).getHostAddress();
		}
		catch (UnknownHostException e) {
			// Cannot happen with 16 bytes
			throw new IllegalStateException(e);
		}
	}

	private AddressConverter() {
	}
}
