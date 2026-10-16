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
package net.netshot.netshot.api.rest.v0.device;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.inventory.device.NetworkAddress.AddressUsage;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * The representation of a network interface of a device, with its IP addresses.
 *
 * @param id the interface ID
 * @param interfaceName the name of the interface
 * @param ip4Addresses the IPv4 addresses
 * @param ip6Addresses the IPv6 addresses
 * @param vrfInstance the VRF instance
 * @param virtualDevice the virtual device
 * @param description the description
 * @param enabled whether the interface is enabled
 * @param level3 whether the interface is a layer 3 one
 * @param macAddress the MAC address
 */
@JsonView(DefaultView.class)
@JacksonXmlRootElement(localName = "NetworkInterface")
public record NetworkInterfaceDto(
	long id,
	String interfaceName,
	List<Address> ip4Addresses,
	List<Address> ip6Addresses,
	String vrfInstance,
	String virtualDevice,
	String description,
	boolean enabled,
	boolean level3,
	String macAddress) {

	/**
	 * An IP address of an interface.
	 *
	 * @param ip the address
	 * @param prefixLength the prefix length
	 * @param addressUsage the usage of the address
	 */
	@JsonView(DefaultView.class)
	public record Address(String ip, int prefixLength, AddressUsage addressUsage) {
	}
}
