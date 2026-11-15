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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.ConfigDelta;
import net.netshot.netshot.inventory.config.ConfigDiff;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigNumericAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.Lookup;

/**
 * Maps the configurations between the records (or the projections) of the inventory and their
 * REST representation. All the methods are pure functions.
 *
 * <p>{@link #toDto(Config)} reads the attributes of the configuration (not their long texts).</p>
 */
public final class ConfigMapper {

	private ConfigMapper() {
		// Static helpers only
	}

	private static Date date(Instant instant) {
		return instant == null ? null : Date.from(instant);
	}

	/**
	 * Maps a configuration to its representation.
	 *
	 * @param config the configuration
	 * @return the DTO
	 */
	public static ConfigDto toDto(Config config) {
		List<ConfigAttributeDto> attributes = new ArrayList<>();
		// The attributes are a set: they are listed by name
		config.attributes().stream().sorted(Comparator.comparing(ConfigAttribute::name, Comparator.nullsFirst(Comparator.naturalOrder())))
			.forEach(attribute -> attributes.add(toDto(attribute)));
		return new ConfigDto(config.id(), date(config.changeDate()), config.author(), attributes, config.customHash());
	}

	/**
	 * Maps the configurations to their representation.
	 *
	 * @param configs the configurations
	 * @param paged whether the list is a page (an offset or a limit was given)
	 * @return the DTOs, in the same order
	 */
	public static List<ConfigDto> toDtos(List<Config> configs, boolean paged) {
		List<ConfigDto> dtos = new ArrayList<>(configs.size());
		configs.forEach(config -> dtos.add(toDto(config)));
		if (paged) {
			// Quirk kept: the former (Hibernate) query paginated the configurations of a device in memory and
			// answered a sub list, which XML publishes as <SubList> instead of <ArrayList>
			return dtos.subList(0, dtos.size());
		}
		return dtos;
	}

	private static ConfigAttributeDto toDto(ConfigAttribute attribute) {
		return switch (attribute) {
			case ConfigTextAttribute text -> new ConfigAttributeDto.Text(text.name(), text.text());
			case ConfigNumericAttribute numeric -> new ConfigAttributeDto.Numeric(numeric.name(), numeric.number());
			case ConfigBinaryAttribute binary -> new ConfigAttributeDto.Binary(binary.name(), binary.assumption());
			case ConfigLongTextAttribute longText -> new ConfigAttributeDto.LongText(longText.name());
			case ConfigBinaryFileAttribute file -> new ConfigAttributeDto.BinaryFile(file.name(), file.originalName(), file.fileSize(), file.checksum());
		};
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
	 * Gives the filters of the list of configuration changes.
	 *
	 * @param after the date (epoch milliseconds) the changes are at or after, or null
	 * @param before the date (epoch milliseconds) the changes are before, or null
	 * @param domainIds the domains of the devices (any if empty)
	 * @param groupIds the groups of the devices (any if empty)
	 * @return the filters
	 */
	public static Criterion[] toCriteria(Long after, Long before, Set<Long> domainIds, Set<Long> groupIds) {
		List<Criterion> criteria = new ArrayList<>();
		if (after != null) {
			criteria.add(new Criterion("changeDate", Lookup.GTE, List.of(Instant.ofEpochMilli(after))));
		}
		if (before != null) {
			criteria.add(new Criterion("changeDate", Lookup.LT, List.of(Instant.ofEpochMilli(before))));
		}
		if (!domainIds.isEmpty()) {
			criteria.add(new Criterion("domain", Lookup.EXACT, List.copyOf(domainIds)));
		}
		if (!groupIds.isEmpty()) {
			criteria.add(new Criterion("group", Lookup.EXACT, List.copyOf(groupIds)));
		}
		return criteria.toArray(Criterion[]::new);
	}
}
