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
package net.netshot.netshot.inventory.config;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * The differences between two configurations of the same kind of device.
 *
 * @param originalDate the change date of the original configuration
 * @param revisedDate the change date of the revised configuration
 * @param originalConfigId the ID of the original configuration (0 for the empty configuration standing for "no previous configuration")
 * @param revisedConfigId the ID of the revised configuration
 * @param originalConfig the original configuration with its attributes, if it was asked for, otherwise null
 * @param revisedConfig the revised configuration with its attributes, if it was asked for, otherwise null
 * @param deltas the differences, by title of the compared item of the driver, or null if they were not asked for
 */
public record ConfigDiff(
	Date originalDate,
	Date revisedDate,
	long originalConfigId,
	long revisedConfigId,
	Config originalConfig,
	Config revisedConfig,
	Map<String, List<ConfigDelta>> deltas) {
}
