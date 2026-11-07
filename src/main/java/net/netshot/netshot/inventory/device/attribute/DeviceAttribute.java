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
package net.netshot.netshot.inventory.device.attribute;

/**
 * A device level attribute, as defined by the driver and set by the snapshots.
 * Its type is stored as a one letter discriminator.
 */
public sealed interface DeviceAttribute
	permits DeviceTextAttribute, DeviceNumericAttribute, DeviceBinaryAttribute, DeviceLongTextAttribute {

	/**
	 * Gets the name of the attribute.
	 *
	 * @return the name
	 */
	String name();

	/**
	 * Gets the value of the attribute.
	 *
	 * @return the value
	 */
	Object getData();
}
