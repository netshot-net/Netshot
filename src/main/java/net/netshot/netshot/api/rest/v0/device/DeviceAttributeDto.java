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
package net.netshot.netshot.api.rest.v0.device;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;

import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * An attribute of a device (a value collected by the device driver), as nested in the
 * device representation. The {@code type} property tells the kind of value. The long text
 * values are not part of the device representation: only their name is.
 */
@JsonView(DefaultView.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = DeviceAttributeDto.Numeric.class, name = "NUMERIC"),
	@JsonSubTypes.Type(value = DeviceAttributeDto.Text.class, name = "TEXT"),
	@JsonSubTypes.Type(value = DeviceAttributeDto.LongText.class, name = "LONGTEXT"),
	@JsonSubTypes.Type(value = DeviceAttributeDto.Binary.class, name = "BINARY"),
})
public sealed interface DeviceAttributeDto {

	/**
	 * Gets the name of the attribute.
	 *
	 * @return the name
	 */
	String name();

	/**
	 * A numeric attribute.
	 *
	 * @param name the name
	 * @param number the value
	 */
	@JsonView(DefaultView.class)
	record Numeric(String name, Double number) implements DeviceAttributeDto {
	}

	/**
	 * A short text attribute.
	 *
	 * @param name the name
	 * @param text the value
	 */
	@JsonView(DefaultView.class)
	record Text(String name, String text) implements DeviceAttributeDto {
	}

	/**
	 * A long text attribute (value not included).
	 *
	 * @param name the name
	 */
	@JsonView(DefaultView.class)
	record LongText(String name) implements DeviceAttributeDto {
	}

	/**
	 * A binary (true/false) attribute.
	 *
	 * @param name the name
	 * @param assumption the value
	 */
	@JsonView(DefaultView.class)
	record Binary(String name, Boolean assumption) implements DeviceAttributeDto {
	}
}
