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
package net.netshot.netshot.api.rest.v0.compliance;

import java.util.HashSet;
import java.util.Set;

/**
 * The request to add or edit a compliance policy. A property absent from the request has its
 * default value: an empty name and no target group.
 *
 * @param id not used (the policy is given by the path when editing)
 * @param name the name
 * @param targetGroups the IDs of the groups the policy applies to
 */
public record PolicyRequest(long id, String name, Set<Long> targetGroups) {

	/**
	 * Applies the default values.
	 */
	public PolicyRequest {
		if (name == null) {
			name = "";
		}
		if (targetGroups == null) {
			targetGroups = new HashSet<>();
		}
	}
}
