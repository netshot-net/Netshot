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
package net.netshot.netshot.api.rest.v0.device;

import java.util.Date;



/**
 * A hardware module of a device, as nested in the device representation
 * (the module responsible for the end of life or end of sale).
 *
 * @param id the module ID
 * @param slot the slot
 * @param partNumber the part number
 * @param serialNumber the serial number
 * @param firstSeenDate when the module was first seen
 * @param lastSeenDate when the module was last seen
 * @param removed whether the module was removed
 */
public record ModuleDto(
	long id,
	String slot,
	String partNumber,
	String serialNumber,
	Date firstSeenDate,
	Date lastSeenDate,
	boolean removed) {
}
