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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import lombok.Builder;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;
import net.netshot.netshot.platform.net.NetworkAddress;
import net.netshot.netshot.platform.net.NetworkAddress.AddressUsage;
import net.netshot.netshot.platform.net.PhysicalAddress;

/**
 * A network interface is attached to a device, has a physical
 * address, and one or several IP addresses.
 *
 * @param id the ID (0 for an interface which isn't stored yet)
 * @param interfaceName the name of the interface
 * @param ip4Addresses the IPv4 addresses
 * @param ip6Addresses the IPv6 addresses
 * @param physicalAddress the MAC address
 * @param vrfInstance the VRF the interface belongs to
 * @param virtualDevice the virtual device the interface belongs to
 * @param description the description
 * @param enabled whether the interface is enabled
 * @param level3 whether the interface is a level 3 one
 */
@Builder(toBuilder = true)
public record NetworkInterface(
	long id,
	String interfaceName,
	Set<Network4Address> ip4Addresses,
	Set<Network6Address> ip6Addresses,
	PhysicalAddress physicalAddress,
	String vrfInstance,
	String virtualDevice,
	String description,
	boolean enabled,
	boolean level3) {

	/**
	 * Instantiates an interface, the missing values being empty.
	 */
	public NetworkInterface {
		interfaceName = interfaceName == null ? "" : interfaceName;
		vrfInstance = vrfInstance == null ? "" : vrfInstance;
		virtualDevice = virtualDevice == null ? "" : virtualDevice;
		ip4Addresses = ip4Addresses == null ? Set.of() : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(ip4Addresses));
		ip6Addresses = ip6Addresses == null ? Set.of() : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(ip6Addresses));
		physicalAddress = physicalAddress == null ? new PhysicalAddress(0) : physicalAddress;
	}

	/**
	 * Gets all the IP addresses of the interface.
	 *
	 * @return the IPv4 then the IPv6 addresses
	 */
	public List<NetworkAddress> getIpAddresses() {
		List<NetworkAddress> ipAddresses = new ArrayList<>();
		ipAddresses.addAll(this.ip4Addresses);
		ipAddresses.addAll(this.ip6Addresses);
		return ipAddresses;
	}

	/**
	 * Gets the MAC address, as text.
	 *
	 * @return the MAC address
	 */
	public String getMacAddress() {
		return this.physicalAddress.toString();
	}

	/**
	 * Adds an IP address to the addresses being collected for an interface. A host address
	 * (/32 or /128) within the subnet of an address already there gets the prefix length of it
	 * (and a VRRP or HSRP address in a secondary subnet becomes a secondary one).
	 *
	 * @param ip4Addresses the IPv4 addresses collected so far
	 * @param ip6Addresses the IPv6 addresses collected so far
	 * @param address the address to add
	 */
	public static void addIpAddress(Set<Network4Address> ip4Addresses, Set<Network6Address> ip6Addresses, NetworkAddress address) {
		if (address.getPrefixLength() == 32 && address instanceof Network4Address n4Address) {
			for (Network4Address ip : ip4Addresses) {
				if (ip.contains(n4Address)) {
					n4Address.setPrefixLength(ip.getPrefixLength());
					if (ip.getAddressUsage() == AddressUsage.SECONDARY) {
						if (address.getAddressUsage() == AddressUsage.HSRP) {
							address.setAddressUsage(AddressUsage.SECONDARYHSRP);
						}
						else if (address.getAddressUsage() == AddressUsage.VRRP) {
							address.setAddressUsage(AddressUsage.SECONDARYVRRP);
						}
					}
					break;
				}
			}
		}
		else if (address.getPrefixLength() == 128 && address instanceof Network6Address n6Address) {
			for (Network6Address ip : ip6Addresses) {
				if (ip.contains(n6Address)) {
					n6Address.setPrefixLength(ip.getPrefixLength());
					break;
				}
			}
		}
		if (address instanceof Network4Address n4Address) {
			ip4Addresses.add(n4Address);
		}
		else if (address instanceof Network6Address n6Address) {
			ip6Addresses.add(n6Address);
		}
	}
}
