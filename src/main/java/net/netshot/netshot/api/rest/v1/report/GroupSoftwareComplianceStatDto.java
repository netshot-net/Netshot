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
 * The software compliance of a device group.
 *
 * @param group the group
 * @param goldDeviceCount the number of devices in production at the gold level
 * @param silverDeviceCount the number of devices in production at the silver level
 * @param bronzeDeviceCount the number of devices in production at the bronze level
 * @param deviceCount the number of devices in production
 */
@Schema(name = "GroupSoftwareComplianceStat", description = "Software compliance of a device group")
public record GroupSoftwareComplianceStatDto(
	ReportObjectDto group,
	long goldDeviceCount,
	long silverDeviceCount,
	long bronzeDeviceCount,
	long deviceCount) {
}
