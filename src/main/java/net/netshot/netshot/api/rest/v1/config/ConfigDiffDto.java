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
package net.netshot.netshot.api.rest.v1.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Ref;
import net.netshot.netshot.inventory.config.ConfigDelta;

/**
 * The differences between two configurations of the same kind of device.
 *
 * @param original the original configuration (null for the empty configuration standing for "no previous
 *        configuration")
 * @param revised the revised configuration
 * @param originalDate the change date of the original configuration
 * @param revisedDate the change date of the revised configuration
 * @param deltas the differences, by title of the compared item of the driver (none for an item which didn't change)
 */
@Schema(name = "ConfigDiff", description = "Differences between two configurations")
public record ConfigDiffDto(
	@Ref ConfigDto original,
	@Ref ConfigDto revised,
	Instant originalDate,
	Instant revisedDate,
	Map<String, List<Delta>> deltas) {

	/**
	 * One difference between two versions of a configuration item.
	 *
	 * @param type the type of difference
	 * @param originalPosition the position (line index) of the difference in the original lines
	 * @param revisedPosition the position (line index) of the difference in the revised lines
	 * @param originalLines the original lines
	 * @param revisedLines the revised lines
	 * @param preContext the (up to 3) original lines before the difference
	 * @param postContext the (up to 3) original lines after the difference
	 * @param hierarchy the (indentation-based) hierarchy of the original lines the difference is in
	 */
	public record Delta(
		ConfigDelta.Type type,
		int originalPosition,
		int revisedPosition,
		List<String> originalLines,
		List<String> revisedLines,
		List<String> preContext,
		List<String> postContext,
		List<LineWithPosition> hierarchy) {
	}

	/**
	 * A line of a configuration with its number (position).
	 *
	 * @param line the line
	 * @param position the position
	 */
	public record LineWithPosition(String line, int position) {
	}
}
