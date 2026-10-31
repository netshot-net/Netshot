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

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Ref;
import net.netshot.netshot.api.rest.v1.device.DeviceDto;

/**
 * A device without recent successful snapshot.
 *
 * @param device the device
 * @param lastSuccess the date of the last successful snapshot (null if there is none)
 * @param lastFailure the date of the last failed snapshot (null if there is none)
 */
@Schema(name = "AccessFailureDevice", description = "Device without recent successful snapshot")
public record AccessFailureDeviceDto(
	@Ref DeviceDto device,
	Instant lastSuccess,
	Instant lastFailure) {
}
