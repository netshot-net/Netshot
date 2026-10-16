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
package net.netshot.netshot.report;

import java.util.Set;

/**
 * What the data export should contain.
 *
 * @param domains the domains to export the devices of (empty: any)
 * @param groups the groups to export the devices of (empty: any)
 * @param interfaces whether to export the interfaces
 * @param inventory whether to export the inventory (modules)
 * @param inventoryHistory whether the inventory includes the removed modules
 * @param locations whether to export the locations and contacts
 * @param compliance whether to export the compliance results
 * @param deviceGroups whether to export the groups and their memberships
 * @param deviceDriverAttributes whether to export the driver-specific attributes
 */
public record ReportExportSelection(
	Set<Long> domains,
	Set<Long> groups,
	boolean interfaces,
	boolean inventory,
	boolean inventoryHistory,
	boolean locations,
	boolean compliance,
	boolean deviceGroups,
	boolean deviceDriverAttributes) {
}
