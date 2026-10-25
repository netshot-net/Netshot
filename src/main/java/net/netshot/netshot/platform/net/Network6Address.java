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
package net.netshot.netshot.platform.net;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * An IPv6 address.
 */
public final class Network6Address extends NetworkAddress {

	/**
	 * Int to ip.
	 *
	 * @param address1 the address1
	 * @param address2 the address2
	 * @return the string
	 */
	public static String intToIP(long address1, long address2) {
		InetAddress address = longToInetAddress(address1, address2);
		return address.getHostAddress();
	}

	/**
	 * Long to inet address.
	 *
	 * @param address1 the address1
	 * @param address2 the address2
	 * @return the inet address
	 */
	public static InetAddress longToInetAddress(long address1, long address2) {
		ByteBuffer buffer = ByteBuffer.allocate(16);
		buffer.putLong(address1);
		buffer.putLong(address2);
		try {
			return InetAddress.getByAddress(buffer.array());
		}
		catch (UnknownHostException e) {
			return null;
		}
	}

	/** The address, stored as a native PostgreSQL "inet" column. */
	private InetAddress address;

	/** The prefix length. */
	private int prefixLength;

	/**
	 * Instantiates a new network6 address.
	 */
	protected Network6Address() {

	}

	/**
	 * Builds an address from the columns it is stored in.
	 *
	 * @param address the address (inet column), or null
	 * @param prefixLength the prefix length, or null for a host address
	 * @param usage the name of the usage, or null for the default one
	 * @return the address, or null if there is no address
	 */
	public static Network6Address fromColumns(InetAddress address, Integer prefixLength, String usage) {
		if (address == null) {
			return null;
		}
		try {
			Network6Address result = new Network6Address((Inet6Address) address, prefixLength == null ? 128 : prefixLength);
			if (usage != null) {
				result.setAddressUsage(AddressUsage.valueOf(usage));
			}
			return result;
		}
		catch (ClassCastException e) {
			throw new IllegalStateException("Invalid stored address " + address, e);
		}
	}

	/**
	 * Instantiates a new network6 address.
	 *
	 * @param address the address
	 * @param prefixLength the prefix length
	 */
	public Network6Address(Inet6Address address, int prefixLength) {
		this.address = address;
		this.prefixLength = prefixLength;
	}

	/**
	 * Instantiates a new network6 address.
	 *
	 * @param address the address
	 * @throws UnknownHostException the unknown host exception
	 */
	public Network6Address(String address) throws UnknownHostException {
		this(address, 128);
	}

	/**
	 * Instantiates a new network6 address.
	 *
	 * @param address the address
	 * @param prefixLength the prefix length
	 * @throws UnknownHostException the unknown host exception
	 */
	public Network6Address(String address, int prefixLength) throws UnknownHostException {
		this.prefixLength = prefixLength;
		try {
			InetAddress inetAddress = InetAddress.getByName(address);
			if (inetAddress instanceof Inet6Address) {
				this.address = inetAddress;
				return;
			}
		}
		catch (UnknownHostException e) {
		}
		throw new UnknownHostException("Unable to parse the IPv6 address.");
	}

	/**
	 * Instantiates a new network6 address.
	 *
	 * @param address the address
	 * @param length the length
	 * @throws UnknownHostException the unknown host exception
	 */
	public Network6Address(String address, String length) throws UnknownHostException {
		this(address, Integer.parseInt(length));
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
		if (!(obj instanceof Network6Address)) {
			return false;
		}
		Network6Address other = (Network6Address) obj;
		return Objects.equals(address, other.address) && (prefixLength == other.prefixLength);
	}

	/**
	 * Gets the address, mapped to a native "inet" column.
	 *
	 * @return the address
	 */
	public InetAddress getAddress() {
		return address;
	}

	/**
	 * Sets the address.
	 *
	 * @param address the new address
	 */
	public void setAddress(InetAddress address) {
		this.address = address;
	}

	/**
	 * Gets the high-order 64 bits of the address.
	 *
	 * @return the address1
	 */
	public long getAddress1() {
		return this.address == null ? 0 : ByteBuffer.wrap(this.address.getAddress()).getLong(0);
	}

	/**
	 * Gets the low-order 64 bits of the address.
	 *
	 * @return the address2
	 */
	public long getAddress2() {
		return this.address == null ? 0 : ByteBuffer.wrap(this.address.getAddress()).getLong(8);
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.platform.net.NetworkAddress#getInetAddress()
	 */
	@Override
	public InetAddress getInetAddress() {
		return this.address;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.platform.net.NetworkAddress#getIP()
	 */
	@Override
	public String getIp() {
		return Network6Address.intToIP(this.getAddress1(), this.getAddress2());
	}

	/**
	 * Gets the prefix.
	 *
	 * @return the prefix
	 */
	@Override
	public String getPrefix() {
		return getIp() + "/" + prefixLength;
	}

	/**
	 * Gets the prefix length.
	 *
	 * @return the prefix length
	 */
	@Override
	public int getPrefixLength() {
		return prefixLength;
	}

	/*(non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Objects.hashCode(address);
		result = prime * result + prefixLength;
		return result;
	}

	/**
	 * Sets the prefix length.
	 *
	 * @param prefixLength the new prefix length
	 */
	public void setPrefixLength(int prefixLength) {
		this.prefixLength = prefixLength;
	}

	/*(non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return this.getPrefix();
	}

	private AddressUsage addressUsage = AddressUsage.PRIMARY;

	@Override
	public AddressUsage getAddressUsage() {
		return addressUsage;
	}

	@Override
	public void setAddressUsage(AddressUsage usage) {
		this.addressUsage = usage;
	}

	public boolean contains(Network6Address other) {
		if (prefixLength <= 64) {
			return (this.getAddress1() >>> (64 - this.prefixLength)) == (other
				.getAddress1() >>> (64 - this.prefixLength));
		}
		else {
			return (this.getAddress1() == other.getAddress1()) && (this.getAddress2() >>> (64 - this.prefixLength))
				== (other.getAddress2() >>> (64 - this.prefixLength));
		}
	}

	/**
	 * Checks if is multicast.
	 *
	 * @return true, if is multicast
	 */
	public boolean isMulticast() {
		return ((this.getAddress1() >>> 56) & 0xFF) == 0xFF;
	}

	/**
	 * Checks if is multicast.
	 *
	 * @return true, if is multicast
	 */
	public boolean isLinkLocal() {
		return ((this.getAddress1() >>> 48) & 0xFE80) == 0xFE80;
	}


	/**
	 * Checks if is normal unicast.
	 *
	 * @return true, if is normal unicast
	 */
	public boolean isGlobalUnicast() {
		return ((this.getAddress1() >>> 61) & 0b111) == 0b001;
	}

}
