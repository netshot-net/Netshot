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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.inventory.device.NetworkAddress.AddressUsage;

/**
 * A network interface is attached to a device, has a physical
 * address, and one or several IP addresses.
 */
@Entity
public final class NetworkInterface {

	/** The id. */
	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)
	}))
	@Setter
	private long id;

	/** The device. */
	@Getter(onMethod = @__({
		@ManyToOne,
		@OnDelete(action = OnDeleteAction.CASCADE)
	}))
	@Setter
	protected Device device;

	/** The interface name. */
	@Getter
	@Setter
	protected String interfaceName;

	/** The ip4 addresses. */
	@Getter(onMethod = @__({
		@ElementCollection, @Fetch(FetchMode.SUBSELECT)
	}))
	@Setter
	protected Set<Network4Address> ip4Addresses = new HashSet<>();

	/** The ip6 addresses. */
	@Getter(onMethod = @__({
		@ElementCollection, @Fetch(FetchMode.SUBSELECT)
	}))
	@Setter
	protected Set<Network6Address> ip6Addresses = new HashSet<>();

	/** The physical address. */
	@Getter(onMethod = @__({
		@Embedded
	}))
	@Setter
	protected PhysicalAddress physicalAddress = new PhysicalAddress(0);

	/** The vrf instance. */
	@Getter
	@Setter
	protected String vrfInstance;

	/** The virtual device. */
	@Getter
	@Setter
	protected String virtualDevice;

	/** The description. */
	@Getter
	@Setter
	protected String description;

	/** The enabled. */
	@Getter
	@Setter
	protected boolean enabled;

	/** The level3. */
	@Getter
	@Setter
	protected boolean level3;

	/**
	 * Instantiates a new network interface.
	 */
	protected NetworkInterface() {

	}

	/**
	 * Instantiates a new network interface.
	 *
	 * @param device the device
	 * @param interfaceName the interface name
	 * @param virtualDevice the virtual device
	 * @param vrfInstance the vrf instance
	 * @param enabled the enabled
	 * @param level3 the level3
	 * @param description the description
	 */
	public NetworkInterface(Device device, String interfaceName, String virtualDevice,
		String vrfInstance, boolean enabled, boolean level3, String description) {
		super();
		this.device = device;
		this.interfaceName = interfaceName == null ? "" : interfaceName;
		this.vrfInstance = vrfInstance == null ? "" : vrfInstance;
		this.virtualDevice = virtualDevice == null ? "" : virtualDevice;
		this.enabled = enabled;
		this.level3 = level3;
		this.description = description;
	}

	/**
	 * Gets the ip addresses.
	 *
	 * @return the ip addresses
	 */
	@Transient
	public List<NetworkAddress> getIpAddresses() {
		List<NetworkAddress> ipAddresses = new ArrayList<>();
		ipAddresses.addAll(this.getIp4Addresses());
		ipAddresses.addAll(this.getIp6Addresses());
		return ipAddresses;
	}

	/**
	 * Adds the ip address.
	 *
	 * @param address the address
	 */
	public void addIpAddress(NetworkAddress address) {
		if (address.getPrefixLength() == 32 && address instanceof Network4Address n4Address) {
			for (Network4Address ip : this.ip4Addresses) {
				if (ip.contains(n4Address)) {
					((Network4Address) address).setPrefixLength(ip.getPrefixLength());
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
			for (Network6Address ip : this.ip6Addresses) {
				if (ip.contains(n6Address)) {
					((Network6Address) address).setPrefixLength(ip.getPrefixLength());
					break;
				}
			}
		}
		if (address instanceof Network4Address n4Address) {
			this.ip4Addresses.add(n4Address);
		}
		else if (address instanceof Network6Address n6Address) {
			this.ip6Addresses.add(n6Address);
		}
	}

	/**
	 * Gets the mac address.
	 *
	 * @return the mac address
	 */
	@Transient
	public String getMacAddress() {
		return this.physicalAddress.toString();
	}

	/*(non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (this.id ^ (this.id >>> 32));
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
		if (!(obj instanceof NetworkInterface)) {
			return false;
		}
		NetworkInterface other = (NetworkInterface) obj;
		return this.id == other.id;
	}

}
