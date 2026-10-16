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

import java.util.Date;

import net.netshot.netshot.inventory.device.DeviceSummary;

/**
 * A device without recent successful snapshot.
 *
 * @param device the device
 * @param lastSuccess the date of the last successful snapshot (null: never)
 * @param lastFailure the date of the last failed snapshot (null: never)
 */
public record AccessFailureDevice(DeviceSummary device, Date lastSuccess, Date lastFailure) {
}
