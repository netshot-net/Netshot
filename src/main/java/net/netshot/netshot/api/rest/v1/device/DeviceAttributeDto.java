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
package net.netshot.netshot.api.rest.v1.device;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An attribute of a device, collected by its driver.
 *
 * @param name the name of the attribute (as defined by the driver)
 * @param type TEXT, LONGTEXT, NUMERIC or BINARY
 * @param value the value (a string, a number or a boolean)
 */
@Schema(name = "DeviceAttribute", description = "Device attribute collected by the driver")
public record DeviceAttributeDto(String name, String type, Object value) {
}
