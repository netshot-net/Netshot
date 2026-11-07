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
package net.netshot.netshot.platform.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * An IP address - abstract, can be either IPv4 or IPv6.
 */
public abstract class NetworkAddress {

	/**
	 * Gets the inet address.
	 *
	 * @return the inet address
	 */
	public abstract InetAddress getInetAddress();

	/**
	 * Gets the network address.
	 *
	 * @param inetAddress the inet address
	 * @param prefixLength the prefix length
	 * @return the network address
	 */
	public static NetworkAddress getNetworkAddress(InetAddress inetAddress, int prefixLength) throws UnknownHostException {
		if (inetAddress instanceof Inet4Address ip4Address) {
			return new Network4Address(ip4Address, prefixLength);
		}
		else if (inetAddress instanceof Inet6Address ip6Address) {
			return new Network6Address(ip6Address, prefixLength);
		}
		return null;
	}

	/**
	 * Gets the network address.
	 *
	 * @param inetAddress the inet address
	 * @return the network address
	 */
	public static NetworkAddress getNetworkAddress(InetAddress inetAddress) throws UnknownHostException {
		if (inetAddress instanceof Inet4Address ip4Address) {
			return new Network4Address(ip4Address, 32);
		}
		else if (inetAddress instanceof Inet6Address ip6Address) {
			return new Network6Address(ip6Address, 128);
		}
		return null;
	}

	/**
	 * Gets the network address.
	 *
	 * @param address the address
	 * @param prefixLength the prefix length
	 * @return the network address
	 * @throws UnknownHostException the unknown host exception
	 */
	public static NetworkAddress getNetworkAddress(String address, int prefixLength) throws UnknownHostException {
		InetAddress inetAddress = InetAddress.getByName(address);
		if (inetAddress instanceof Inet4Address) {
			return new Network4Address((Inet4Address) inetAddress, prefixLength);
		}
		else {
			return new Network6Address((Inet6Address) inetAddress, prefixLength);
		}
	}

	/**
	 * Gets the network address.
	 *
	 * @param address the address
	 * @return the network address
	 * @throws UnknownHostException the unknown host exception
	 */
	public static NetworkAddress getNetworkAddress(String address) throws UnknownHostException {
		NetworkAddress na = getNetworkAddress(address, 0);
		if (na instanceof Network4Address n4a) {
			n4a.setPrefixLength(32);
		}
		else if (na instanceof Network6Address n6a) {
			n6a.setPrefixLength(128);
		}
		return na;
	}

	/**
	 * Gets the ip.
	 *
	 * @return the ip
	 */
	public abstract String getIp();

	public abstract int getPrefixLength();

	public abstract String getPrefix();

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return getPrefix();
	}


	public enum AddressUsage {
		PRIMARY,
		SECONDARY,
		VRRP,
		HSRP,
		SECONDARYVRRP,
		SECONDARYHSRP
	}

	public abstract AddressUsage getAddressUsage();

	public abstract void setAddressUsage(AddressUsage usage);


}
