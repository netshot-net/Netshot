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
package net.netshot.netshot.inventory.config;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import lombok.Builder;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;

/**
 * A device configuration: the configuration level attributes gathered by a snapshot.
 *
 * @param id the ID (0 for a configuration which isn't stored yet)
 * @param version the version
 * @param changeDate when the configuration was gathered
 * @param deviceId the ID of the device
 * @param author the author of the last change of the configuration, as reported by the device
 * @param customHash the hash the driver computed to tell whether the configuration has changed, if any
 * @param attributes the attributes (one per name)
 */
@Builder(toBuilder = true)
public record Config(
	long id,
	int version,
	Instant changeDate,
	long deviceId,
	String author,
	String customHash,
	List<ConfigAttribute> attributes) {

	/**
	 * Instantiates a configuration, the missing attributes being none.
	 */
	public Config {
		attributes = attributes == null ? List.of() : List.copyOf(attributes);
	}

	/**
	 * Builds an empty configuration of a device (not stored).
	 *
	 * @param deviceId the ID of the device
	 * @return the configuration
	 */
	public static Config empty(long deviceId) {
		return Config.builder().deviceId(deviceId).author("").build();
	}

	/**
	 * From a list of config lines, get an array of pointers to the parent line,
	 * based on indentation (either the direct parent line index, or -1 if no parent).
	 * @param lines the config lines
	 * @return an array, the indice of the direct parent line for each line of the passed list
	 */
	public static int[] getLineParents(List<String> lines) {
		List<Pair<Integer, Integer>> lastLevels = new LinkedList<>();
		int lineCount = lines.size();
		int[] lineParents = new int[lineCount];
		for (int lineIndex = 0; lineIndex < lineCount; lineIndex++) {
			String line = lines.get(lineIndex);
			int l = 0;
			while (l < line.length() && Character.isWhitespace(line.charAt(l))) {
				l += 1;
			}
			final int level = l < line.length() ? l : 0;
			lastLevels.removeIf(le -> le.getKey() >= level);
			lastLevels.add(Pair.of(level, lineIndex));
			lineParents[lineIndex] = lastLevels.size() > 1 ? lastLevels.get(lastLevels.size() - 2).getValue() : -1;
		}
		return lineParents;
	}

	/**
	 * Gets the attributes, by name.
	 *
	 * @return the attributes
	 */
	public Map<String, ConfigAttribute> getAttributeMap() {
		Map<String, ConfigAttribute> map = new HashMap<>();
		for (ConfigAttribute a : this.attributes) {
			map.put(a.name(), a);
		}
		return map;
	}

	/**
	 * Gets an attribute by name.
	 *
	 * @param name the name of the attribute
	 * @return the attribute, or null if there is none of this name
	 */
	public ConfigAttribute getAttribute(String name) {
		for (ConfigAttribute attribute : this.attributes) {
			if (attribute.name().equals(name)) {
				return attribute;
			}
		}
		return null;
	}
}
