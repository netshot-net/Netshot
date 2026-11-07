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
package net.netshot.netshot.api.rest.v0.config;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.inventory.config.ConfigDelta;

/**
 * The representation of the differences between two configurations.
 *
 * @param originalDate the change date of the original configuration
 * @param revisedDate the change date of the revised configuration
 * @param originalConfigId the ID of the original configuration
 * @param revisedConfigId the ID of the revised configuration
 * @param originalConfig the original configuration, if it was asked for
 * @param revisedConfig the revised configuration, if it was asked for
 * @param deltas the differences by title of the compared item, if they were asked for
 */
@JacksonXmlRootElement(localName = "RsConfigDiff")
public record ConfigDiffDto(
	Date originalDate,
	Date revisedDate,
	Long originalConfigId,
	Long revisedConfigId,
	ConfigDto originalConfig,
	ConfigDto revisedConfig,
	@JsonInclude(JsonInclude.Include.NON_NULL) Map<String, List<Delta>> deltas) {

	/**
	 * One difference between two versions of a configuration item.
	 *
	 * @param item not used (always null)
	 * @param diffType the type of difference
	 * @param originalPosition the position of the difference in the original lines
	 * @param revisedPosition the position of the difference in the revised lines
	 * @param originalLines the original lines
	 * @param revisedLines the revised lines
	 * @param preContext the (up to 3) original lines before the difference
	 * @param postContext the (up to 3) original lines after the difference
	 * @param hierarchy the (indentation-based) hierarchy of the original lines the difference is in
	 */
	@JacksonXmlRootElement(localName = "RsConfigDelta")
	public record Delta(
		String item,
		ConfigDelta.Type diffType,
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
