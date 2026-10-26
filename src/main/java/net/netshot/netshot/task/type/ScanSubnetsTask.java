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
package net.netshot.netshot.task.type;

import java.util.Set;

import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.task.Task;

/**
 * Scans subnets, to discover the devices (one discovery task per unknown address).
 *
 * @param domainId the ID of the domain of the new devices
 * @param subnets the subnets to scan
 */
public record ScanSubnetsTask(Long domainId, Set<Network4Address> subnets) implements TaskSpec, DomainBasedTask, ChildOrchestratingTask {

	/**
	 * Canonical constructor: a null set of subnets is replaced by an empty one.
	 */
	public ScanSubnetsTask {
		subnets = subnets == null ? Set.of() : Set.copyOf(subnets);
	}

	/**
	 * Builds a new scan task.
	 *
	 * @param subnets the subnets
	 * @param domainId the ID of the domain
	 * @param comments the comments
	 * @param target the description of the target
	 * @param author the author
	 * @return the new task
	 */
	public static Task of(Set<Network4Address> subnets, Long domainId, String comments, String target, String author) {
		return Task.of(new ScanSubnetsTask(domainId, subnets), comments, target, author);
	}

	@Override
	public String description() {
		return "Subnet scan";
	}

	@Override
	public String identityGroup() {
		return "ScanSubnets";
	}
}
