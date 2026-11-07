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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;

/**
 * A configuration being gathered by a snapshot (mutable, then stored as a {@link Config}).
 */
public final class ConfigDraft {

	/** The ID of the device. */
	@Getter
	private final long deviceId;

	/** The author of the last change of the configuration, as reported by the device. */
	@Getter
	@Setter
	private String author = "";

	/** The hash the driver computed to tell whether the configuration has changed, if any. */
	@Getter
	@Setter
	private String customHash;

	/** The attributes, by name. */
	private final Map<String, ConfigAttribute> attributes = new LinkedHashMap<>();

	/**
	 * Starts a configuration of a device.
	 *
	 * @param deviceId the ID of the device
	 */
	public ConfigDraft(long deviceId) {
		this.deviceId = deviceId;
	}

	/**
	 * Adds an attribute. Only the first attribute of a given name is kept.
	 *
	 * @param attribute the attribute
	 */
	public void addAttribute(ConfigAttribute attribute) {
		this.attributes.putIfAbsent(attribute.name(), attribute);
	}

	/**
	 * Gets the attributes gathered so far.
	 *
	 * @return the attributes, in the order they were added
	 */
	public Collection<ConfigAttribute> getAttributes() {
		return this.attributes.values();
	}

	/**
	 * Gets the attributes gathered so far, by name.
	 *
	 * @return the attributes
	 */
	public Map<String, ConfigAttribute> getAttributeMap() {
		return new LinkedHashMap<>(this.attributes);
	}

	/**
	 * Builds the configuration (not stored yet: no ID, no date).
	 *
	 * @return the configuration
	 */
	public Config toConfig() {
		return Config.builder()
			.deviceId(this.deviceId)
			.author(this.author)
			.customHash(this.customHash)
			.attributes(new ArrayList<>(this.attributes.values()))
			.build();
	}
}
