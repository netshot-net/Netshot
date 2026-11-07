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
package net.netshot.netshot.api.rest.v1.device;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;
import net.netshot.netshot.platform.net.NetworkAddress.AddressUsage;

/**
 * A network interface of a device. It has an {@code id} but no {@code url}: it is only reachable through its
 * device ({@code /devices/{id}/interfaces}).
 *
 * @param id the ID
 * @param display the display name (the name of the interface)
 * @param name the name of the interface
 * @param description the description
 * @param enabled whether the interface is enabled
 * @param level3 whether the interface is a level 3 one
 * @param vrf the VRF the interface belongs to (empty for the default one)
 * @param virtualDevice the virtual device the interface belongs to (empty if none)
 * @param macAddress the MAC address ({@code 00:11:22:33:44:55})
 * @param ipAddresses the IP addresses (IPv4 first)
 */
@Schema(name = "DeviceInterface", description = "Network interface of a device")
public record DeviceInterfaceDto(
	long id,
	String display,
	@Brief String name,
	@Brief String description,
	@Brief boolean enabled,
	boolean level3,
	String vrf,
	String virtualDevice,
	String macAddress,
	List<IpAddress> ipAddresses) {

	/**
	 * An IP address of an interface.
	 *
	 * @param ip the address
	 * @param prefixLength the prefix length
	 * @param version the IP version (4 or 6)
	 * @param usage the usage of the address
	 */
	public record IpAddress(String ip, int prefixLength, int version, AddressUsage usage) {
	}
}
