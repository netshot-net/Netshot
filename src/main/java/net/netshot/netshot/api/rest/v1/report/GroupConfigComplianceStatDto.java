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
package net.netshot.netshot.api.rest.v1.report;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The configuration compliance of a device group.
 *
 * @param group the group
 * @param folder the folder of the group
 * @param compliantDeviceCount the number of devices in production without non-conforming result
 * @param deviceCount the number of devices in production
 */
@Schema(name = "GroupConfigComplianceStat", description = "Configuration compliance of a device group")
public record GroupConfigComplianceStatDto(
	ReportObjectDto group,
	String folder,
	long compliantDeviceCount,
	long deviceCount) {
}
