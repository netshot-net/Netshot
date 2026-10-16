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
package net.netshot.netshot.inventory.config;

import java.util.Date;
import java.util.Set;

/**
 * The criteria to list configuration changes. A null date or an empty set means no restriction.
 *
 * @param after only the configurations gathered at or after this date
 * @param before only the configurations gathered before this date
 * @param domainIds only the configurations of the devices of these domains
 * @param groupIds only the configurations of the devices of these groups
 */
public record ConfigFilter(Date after, Date before, Set<Long> domainIds, Set<Long> groupIds) {
}
