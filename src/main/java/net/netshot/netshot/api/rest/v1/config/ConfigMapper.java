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
package net.netshot.netshot.api.rest.v1.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.device.DeviceDto;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.ConfigDelta;
import net.netshot.netshot.inventory.config.ConfigDiff;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigNumericAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;

/**
 * Maps the configurations to their v1 representation.
 */
public final class ConfigMapper {

	/** The path of the configurations. */
	public static final String PATH = "configs";

	private ConfigMapper() {
	}

	private static String display(Config config) {
		return config.changeDate() == null ? "Configuration %d".formatted(config.id())
			: "Configuration %d (%s)".formatted(config.id(), config.changeDate());
	}

	/**
	 * Gets the path to download an item of a configuration.
	 *
	 * @param configId the configuration ID
	 * @param item the name of the item
	 * @return the path
	 */
	public static String itemUrl(long configId, String item) {
		return V1Links.of(PATH, configId, "items", URLEncoder.encode(item, StandardCharsets.UTF_8).replace("+", "%20"));
	}

	/**
	 * Maps a configuration.
	 *
	 * @param config the configuration
	 * @param devices resolves the device by its ID
	 * @param withAttributes whether the configuration holds its attributes (it doesn't when listed without them)
	 * @return the representation
	 */
	public static ConfigDto toDto(Config config, LongFunction<DeviceDto> devices, boolean withAttributes) {
		List<ConfigAttributeDto> attributes = null;
		if (withAttributes) {
			// The attributes are a set: they are listed by name
			attributes = config.attributes().stream().sorted(Comparator.comparing(ConfigAttribute::name))
				.map(attribute -> toDto(config.id(), attribute)).toList();
		}
		return new ConfigDto(config.id(), V1Links.of(PATH, config.id()), display(config), (long) config.version(),
			devices.apply(config.deviceId()), config.changeDate(), config.author(), config.customHash(), attributes);
	}

	private static ConfigAttributeDto toDto(long configId, ConfigAttribute attribute) {
		return switch (attribute) {
			case ConfigTextAttribute a -> new ConfigAttributeDto.Text(a.name(), a.text());
			case ConfigNumericAttribute a -> new ConfigAttributeDto.Numeric(a.name(), a.number());
			case ConfigBinaryAttribute a -> new ConfigAttributeDto.Binary(a.name(), a.assumption());
			case ConfigLongTextAttribute a -> new ConfigAttributeDto.LongText(a.name(), itemUrl(configId, a.name()));
			case ConfigBinaryFileAttribute a -> new ConfigAttributeDto.BinaryFile(a.name(), a.originalName(), a.fileSize(),
				a.checksum(), itemUrl(configId, a.name()));
		};
	}

	private static Instant instant(Date date) {
		return date == null ? null : date.toInstant();
	}

	/**
	 * Maps a comparison of two configurations.
	 *
	 * @param diff the comparison
	 * @param original the original configuration (null for the empty configuration)
	 * @param revised the revised configuration
	 * @return the representation
	 */
	public static ConfigDiffDto toDto(ConfigDiff diff, ConfigDto original, ConfigDto revised) {
		Map<String, List<ConfigDiffDto.Delta>> deltas = null;
		if (diff.deltas() != null) {
			deltas = new LinkedHashMap<>();
			for (Map.Entry<String, List<ConfigDelta>> entry : diff.deltas().entrySet()) {
				List<ConfigDiffDto.Delta> items = new ArrayList<>();
				entry.getValue().forEach(delta -> items.add(toDto(delta)));
				deltas.put(entry.getKey(), items);
			}
		}
		return new ConfigDiffDto(original, revised, instant(diff.originalDate()), instant(diff.revisedDate()), deltas);
	}

	private static ConfigDiffDto.Delta toDto(ConfigDelta delta) {
		return new ConfigDiffDto.Delta(delta.type(), delta.originalPosition(), delta.revisedPosition(), delta.originalLines(),
			delta.revisedLines(), delta.preContext(), delta.postContext(),
			delta.hierarchy().stream().map(line -> new ConfigDiffDto.LineWithPosition(line.line(), line.position())).toList());
	}
}
