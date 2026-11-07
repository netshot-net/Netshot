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
package net.netshot.netshot.api.rest.v1.device;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;

/**
 * A hardware module (line card, power supply...) of a device, current or removed. It has an {@code id} but no
 * {@code url}: it is only reachable through its device ({@code /devices/{id}/modules}).
 *
 * @param id the ID
 * @param display the display name (the slot and the part number)
 * @param slot the slot
 * @param partNumber the part number
 * @param serialNumber the serial number
 * @param firstSeenDate when the module was first seen
 * @param lastSeenDate when the module was last seen
 * @param removed whether the module isn't in the device anymore (kept for the history)
 */
@Schema(name = "DeviceModule", description = "Hardware module of a device")
public record DeviceModuleDto(
	long id,
	String display,
	@Brief String slot,
	@Brief String partNumber,
	@Brief String serialNumber,
	Instant firstSeenDate,
	Instant lastSeenDate,
	boolean removed) {
}
