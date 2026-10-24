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
package net.netshot.netshot.inventory.device;

import java.time.Instant;

import lombok.Builder;

/**
 * A network device line module.
 *
 * @param id the ID (0 for a module which isn't stored yet)
 * @param slot the slot
 * @param partNumber the part number
 * @param serialNumber the serial number
 * @param firstSeenDate when the module was seen for the first time
 * @param lastSeenDate when the module was seen for the last time
 * @param removed whether the module isn't in the device anymore (kept for the history)
 */
@Builder(toBuilder = true)
public record Module(
	long id,
	String slot,
	String partNumber,
	String serialNumber,
	Instant firstSeenDate,
	Instant lastSeenDate,
	boolean removed) {

	/**
	 * Instantiates a module, the missing values being empty.
	 */
	public Module {
		slot = slot == null ? "" : slot;
		partNumber = partNumber == null ? "" : partNumber;
		serialNumber = serialNumber == null ? "" : serialNumber;
	}

	/**
	 * Tells whether this is the same physical module as another one (same slot, part number and serial number).
	 *
	 * @param other the other module
	 * @return true if this is the same module
	 */
	public boolean sameAs(Module other) {
		return this.slot.equals(other.slot) && this.partNumber.equals(other.partNumber) && this.serialNumber.equals(other.serialNumber);
	}
}
