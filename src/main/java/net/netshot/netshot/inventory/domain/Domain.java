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
package net.netshot.netshot.inventory.domain;

import java.time.Instant;

import lombok.Builder;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;

/**
 * A management domain: a group of devices which a given Netshot server address manages
 * (used, for example, as the address the devices push their files to).
 *
 * @param id the ID (0 for a domain which isn't stored yet)
 * @param version the version, incremented on each change
 * @param changeDate the time of the last change
 * @param name the name, unique
 * @param description the description
 * @param server4Address the IPv4 address of the Netshot server for this domain
 * @param server6Address the IPv6 address of the Netshot server for this domain
 */
@Builder(toBuilder = true)
public record Domain(
	long id,
	int version,
	Instant changeDate,
	String name,
	String description,
	Network4Address server4Address,
	Network6Address server6Address) {

	/**
	 * A reference to a domain by its ID, holding nothing else (as an incoming request gives it).
	 *
	 * @param id the domain ID
	 * @return the reference
	 */
	public static Domain reference(long id) {
		return Domain.builder().id(id).build();
	}

	@Override
	public String toString() {
		return "Device Domain " + id + " (name '" + name + "')";
	}
}
