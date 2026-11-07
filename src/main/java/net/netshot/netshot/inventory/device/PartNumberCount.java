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
package net.netshot.netshot.inventory.device;

/**
 * A part number of modules with the number of modules having it, projected by
 * {@link DeviceInventoryRepository#findPartNumberCounts}.
 *
 * @param partNumber the part number (empty for the modules which have none)
 * @param moduleCount the number of modules (the ones which were removed from their device included)
 */
public record PartNumberCount(String partNumber, long moduleCount) {
}
