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
package net.netshot.netshot.driver;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Value;

import lombok.Getter;
import lombok.Setter;

/**
 * Definition, declared by a driver, of a per-device option: a
 * user-configurable setting (as opposed to {@link AttributeDefinition},
 * which describes driver-collected, read-only data). The value for each
 * declared option is stored per-device and read back by the driver at
 * runtime via {@code device.options.<name>}.
 */
public final class OptionDefinition {

	@Getter
	@Setter
	private DeviceDriver driver;

	@Getter
	@Setter
	private String name;

	@Getter
	@Setter
	private String title;

	@Getter
	@Setter
	private DriverValueType type;

	/** Valid choices restricting a TEXT option's value; when set, the value must be one of these. */
	@Getter
	@Setter
	private List<String> choices;

	/**
	 * Default value, typed to match {@link #type}: a {@link Boolean} for
	 * BOOLEAN, a {@link String} for TEXT - never a stringified "true"/
	 * "false", so it can be stored and returned as real JSON, matching how
	 * {@link net.netshot.netshot.inventory.device.Device#getOptions()} persists values.
	 */
	@Getter
	@Setter
	private Object defaultValue;

	protected OptionDefinition() {
	}

	public OptionDefinition(DeviceDriver driver, String name, Value data) throws Exception {
		this.driver = driver;
		this.name = name;
		this.title = data.getMember("title").asString();
		if (!this.title.matches("^[0-9A-Za-z\\-_\\(\\)][0-9A-Za-z \\-_\\(\\)]+$")) {
			throw new IllegalArgumentException("Invalid title for item %s.".formatted(name));
		}
		String textType = data.getMember("type").asString();
		switch (textType) {
			case "Text":
				this.type = DriverValueType.TEXT;
				break;
			case "Boolean":
				this.type = DriverValueType.BOOLEAN;
				break;
			default:
				throw new IllegalArgumentException("Invalid type for item %s.".formatted(name));
		}
		Value choicesValue = data.getMember("choices");
		if (choicesValue != null) {
			if (this.type != DriverValueType.TEXT) {
				throw new IllegalArgumentException("'choices' is not applicable to item %s.".formatted(name));
			}
			if (!choicesValue.hasArrayElements() || choicesValue.getArraySize() == 0) {
				throw new IllegalArgumentException("Invalid 'choices' for item %s.".formatted(name));
			}
			List<String> parsedChoices = new ArrayList<>();
			for (long i = 0; i < choicesValue.getArraySize(); i++) {
				parsedChoices.add(choicesValue.getArrayElement(i).asString());
			}
			this.choices = parsedChoices;
		}
		Value defaultMember = data.getMember("default");
		if (defaultMember != null) {
			if (this.type == DriverValueType.BOOLEAN) {
				if (!defaultMember.isBoolean()) {
					throw new IllegalArgumentException("The 'default' value for item %s should be a boolean.".formatted(name));
				}
				this.defaultValue = defaultMember.asBoolean();
			}
			else {
				if (!defaultMember.isString()) {
					throw new IllegalArgumentException("The 'default' value for item %s should be a string.".formatted(name));
				}
				String textDefault = defaultMember.asString();
				if (this.choices != null && !this.choices.contains(textDefault)) {
					throw new IllegalArgumentException("Invalid 'default' value for item %s.".formatted(name));
				}
				this.defaultValue = textDefault;
			}
		}
	}

	@Override
	public String toString() {
		return "OptionDefinition [name=%s, type=%s, title=%s]".formatted(this.name, this.type, this.title);
	}
}
