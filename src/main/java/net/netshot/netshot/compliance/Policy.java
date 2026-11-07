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
package net.netshot.netshot.compliance;

import java.time.Instant;
import java.util.Set;

/**
 * A compliance policy: a set of rules, checked on the devices of the target groups.
 *
 * @param id the ID (0 for a policy not stored yet)
 * @param version the version, incremented on each change
 * @param changeDate the date of the last change
 * @param name the name
 * @param targetGroupIds the IDs of the groups the policy applies to
 */
public record Policy(long id, int version, Instant changeDate, String name, Set<Long> targetGroupIds) {

	/**
	 * Canonical constructor: the target groups are copied (none means an empty set).
	 */
	public Policy {
		targetGroupIds = targetGroupIds == null ? Set.of() : Set.copyOf(targetGroupIds);
	}

	/**
	 * Gets a copy of the policy with another ID.
	 *
	 * @param newId the ID
	 * @return the policy
	 */
	public Policy withId(long newId) {
		return new Policy(newId, this.version, this.changeDate, this.name, this.targetGroupIds);
	}

	@Override
	public String toString() {
		return "Compliance policy " + id + " (name '" + name + "')";
	}
}
