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
package net.netshot.netshot.inventory.device;

import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/**
 * A MAC address.
 */
@Embeddable
public final class PhysicalAddress {

	/** The address. */
	@Getter(onMethod = @__({
		@Column(name = "physicalAddress")
	}))
	@Setter
	private long address;

	/**
	 * Instantiates a new physical address.
	 */
	protected PhysicalAddress() {

	}

	/**
	 * Instantiates a new physical address.
	 *
	 * @param address the address
	 */
	public PhysicalAddress(long address) {
		this.address = address;
	}

	/**
	 * Instantiates a new physical address.
	 *
	 * @param address the address
	 * @throws ParseException the parse exception
	 */
	public PhysicalAddress(String address) throws ParseException {
		long[] result = new long[6];
		String[] patterns = {
			"^(?<b0>[0-9A-F]{2})(?<b1>[0-9A-F]{2})[\\-\\.](?<b2>[0-9A-F]{2})(?<b3>[0-9A-F]{2})[\\.\\-](?<b4>[0-9A-F]{2})(?<b5>[0-9A-F]{2})$",
			"^(?<b0>[0-9A-F]{1,2})[\\-\\.:](?<b1>[0-9A-F]{1,2})[\\-\\.:](?<b2>[0-9A-F]{1,2})[\\-\\.:]"
				+ "(?<b3>[0-9A-F]{1,2})[\\-\\.:](?<b4>[0-9A-F]{1,2})[\\-\\.:](?<b5>[0-9A-F]{1,2})$"
		};
		for (String pattern : patterns) {
			Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
			Matcher m = p.matcher(address);
			if (m.find()) {
				try {
					result[0] = Integer.parseInt(m.group("b0"), 16);
					result[1] = Integer.parseInt(m.group("b1"), 16);
					result[2] = Integer.parseInt(m.group("b2"), 16);
					result[3] = Integer.parseInt(m.group("b3"), 16);
					result[4] = Integer.parseInt(m.group("b4"), 16);
					result[5] = Integer.parseInt(m.group("b5"), 16);
					this.address = result[0] << 40 | result[1] << 32 | result[2] << 24
						| result[3] << 16 | result[4] << 8 | result[5];
					return;
				}
				catch (Exception e) {

				}
			}
		}
		throw new ParseException("Couldn't parse the MAC address", 0);
	}

	/*(non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (address ^ (address >>> 32));
		return result;
	}

	/*(non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		PhysicalAddress other = (PhysicalAddress) obj;
		return address == other.address;
	}

	/**
	 * Parses the mac address.
	 *
	 * @param address the address
	 * @return the physical address
	 * @throws ParseException the parse exception
	 */
	public static PhysicalAddress parseMacAddress(String address) throws ParseException {
		return new PhysicalAddress(address);
	}

	/**
	 * To bytes.
	 *
	 * @return the byte[]
	 */
	private byte[] toBytes() {
		byte[] chunks = new byte[6];
		chunks[0] = (byte) ((address >> 40) & 0xFF);
		chunks[1] = (byte) ((address >> 32) & 0xFF);
		chunks[2] = (byte) ((address >> 24) & 0xFF);
		chunks[3] = (byte) ((address >> 16) & 0xFF);
		chunks[4] = (byte) ((address >> 8) & 0xFF);
		chunks[5] = (byte) (address & 0xFF);
		return chunks;
	}

	/*(non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		byte[] macAddress = toBytes();
		return String.format("%02x%02x.%02x%02x.%02x%02x",
			macAddress[0], macAddress[1], macAddress[2],
			macAddress[3], macAddress[4], macAddress[5]);
	}

	/**
	 * Gets the long address.
	 *
	 * @return the long address
	 */
	@Transient
	public long getLongAddress() {
		return this.address;
	}
}
