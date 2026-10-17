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
package net.netshot.netshot.api.rest.v0.config;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.ConfigDelta;
import net.netshot.netshot.inventory.config.ConfigDiff;
import net.netshot.netshot.inventory.config.ConfigFilter;
import net.netshot.netshot.inventory.config.ConfigSummary;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigNumericAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;

/**
 * Maps the configurations between the entities (or the projections) of the inventory and their
 * REST representation. All the methods are pure functions.
 *
 * <p>{@link #toDto(Config)} reads a <em>detached</em> configuration: it needs its attributes to be
 * loaded (see {@code ConfigRepository}).</p>
 */
public final class ConfigMapper {

	private ConfigMapper() {
		// Static helpers only
	}

	/**
	 * Maps a configuration to its representation.
	 *
	 * @param config the configuration, with its attributes loaded
	 * @return the DTO
	 */
	public static ConfigDto toDto(Config config) {
		List<ConfigAttributeDto> attributes = new ArrayList<>();
		config.getAttributes().forEach(attribute -> attributes.add(toDto(attribute)));
		return new ConfigDto(config.getId(), config.getChangeDate(), config.getAuthor(), attributes, config.getCustomHash());
	}

	/**
	 * Maps the configurations to their representation.
	 *
	 * @param configs the configurations, with their attributes loaded
	 * @return the DTOs, in the same order
	 */
	public static List<ConfigDto> toDtos(List<Config> configs) {
		List<ConfigDto> dtos = new ArrayList<>(configs.size());
		configs.forEach(config -> dtos.add(toDto(config)));
		if (configs.getClass() != ArrayList.class) {
			// Quirk kept: a paginated query with a collection fetch is paginated in memory and answers a
			// sub list, which XML publishes as <SubList> instead of <ArrayList>
			return dtos.subList(0, dtos.size());
		}
		return dtos;
	}

	private static ConfigAttributeDto toDto(ConfigAttribute attribute) {
		if (attribute instanceof ConfigTextAttribute text) {
			return new ConfigAttributeDto.Text(text.getName(), text.getText());
		}
		else if (attribute instanceof ConfigNumericAttribute numeric) {
			return new ConfigAttributeDto.Numeric(numeric.getName(), numeric.getNumber());
		}
		else if (attribute instanceof ConfigBinaryAttribute binary) {
			return new ConfigAttributeDto.Binary(binary.getName(), binary.getAssumption());
		}
		else if (attribute instanceof ConfigLongTextAttribute) {
			return new ConfigAttributeDto.LongText(attribute.getName());
		}
		else if (attribute instanceof ConfigBinaryFileAttribute file) {
			return new ConfigAttributeDto.BinaryFile(file.getName(), file.getOriginalName(), file.getFileSize(), file.getChecksum());
		}
		throw new IllegalArgumentException("Unknown kind of configuration attribute: " + attribute.getClass().getName());
	}

	/**
	 * Maps a configuration summary to its representation.
	 *
	 * @param summary the summary
	 * @return the DTO
	 */
	public static ConfigSummaryDto toDto(ConfigSummary summary) {
		return new ConfigSummaryDto(summary.id(), summary.deviceName(), summary.deviceId(), summary.changeDate(),
			summary.author(), summary.deviceNetworkClass());
	}

	/**
	 * Maps the configuration summaries to their representation.
	 *
	 * @param summaries the summaries
	 * @return the DTOs, in the same order
	 */
	public static List<ConfigSummaryDto> toSummaryDtos(List<ConfigSummary> summaries) {
		List<ConfigSummaryDto> dtos = new ArrayList<>(summaries.size());
		summaries.forEach(summary -> dtos.add(toDto(summary)));
		return dtos;
	}

	/**
	 * Maps a comparison of two configurations to its representation.
	 *
	 * @param diff the comparison
	 * @return the DTO
	 */
	public static ConfigDiffDto toDto(ConfigDiff diff) {
		Map<String, List<ConfigDiffDto.Delta>> deltas = null;
		if (diff.deltas() != null) {
			deltas = new LinkedHashMap<>();
			for (Map.Entry<String, List<ConfigDelta>> entry : diff.deltas().entrySet()) {
				List<ConfigDiffDto.Delta> items = new ArrayList<>();
				entry.getValue().forEach(delta -> items.add(toDto(delta)));
				deltas.put(entry.getKey(), items);
			}
		}
		return new ConfigDiffDto(diff.originalDate(), diff.revisedDate(), diff.originalConfigId(), diff.revisedConfigId(),
			diff.originalConfig() == null ? null : toDto(diff.originalConfig()),
			diff.revisedConfig() == null ? null : toDto(diff.revisedConfig()),
			deltas);
	}

	private static ConfigDiffDto.Delta toDto(ConfigDelta delta) {
		List<ConfigDiffDto.LineWithPosition> hierarchy = new ArrayList<>();
		delta.hierarchy().forEach(line -> hierarchy.add(new ConfigDiffDto.LineWithPosition(line.line(), line.position())));
		return new ConfigDiffDto.Delta(null, delta.type(), delta.originalPosition(), delta.revisedPosition(),
			delta.originalLines(), delta.revisedLines(), delta.preContext(), delta.postContext(), hierarchy);
	}

	/**
	 * Builds the criteria of the configuration changes from the query parameters.
	 *
	 * @param after the date (milliseconds since 1970) from which the configurations were gathered, or null
	 * @param before the date (milliseconds since 1970) before which the configurations were gathered, or null
	 * @param domainIds the IDs of the domains
	 * @param groupIds the IDs of the groups
	 * @return the criteria
	 */
	public static ConfigFilter toFilter(Long after, Long before, Set<Long> domainIds, Set<Long> groupIds) {
		return new ConfigFilter(after == null ? null : new Date(after), before == null ? null : new Date(before),
			domainIds, groupIds);
	}
}
