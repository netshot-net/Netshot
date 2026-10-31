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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An attribute of a configuration (a value collected by the device driver). The {@code type} property tells the
 * kind of value. The long texts and the content of the files are not part of the representation: they are
 * downloaded from their {@code url}.
 */
@Schema(name = "ConfigAttribute", description = "Attribute of a configuration")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = ConfigAttributeDto.Numeric.class, name = "NUMERIC"),
	@JsonSubTypes.Type(value = ConfigAttributeDto.Text.class, name = "TEXT"),
	@JsonSubTypes.Type(value = ConfigAttributeDto.LongText.class, name = "LONGTEXT"),
	@JsonSubTypes.Type(value = ConfigAttributeDto.Binary.class, name = "BINARY"),
	@JsonSubTypes.Type(value = ConfigAttributeDto.BinaryFile.class, name = "BINARYFILE"),
})
public sealed interface ConfigAttributeDto {

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
	record Numeric(String name, Double number) implements ConfigAttributeDto {
	}

	/**
	 * A short text attribute.
	 *
	 * @param name the name
	 * @param text the value
	 */
	record Text(String name, String text) implements ConfigAttributeDto {
	}

	/**
	 * A long text attribute (value not included).
	 *
	 * @param name the name
	 * @param url the path to download the text
	 */
	record LongText(String name, String url) implements ConfigAttributeDto {
	}

	/**
	 * A binary (true/false) attribute.
	 *
	 * @param name the name
	 * @param assumption the value
	 */
	record Binary(String name, Boolean assumption) implements ConfigAttributeDto {
	}

	/**
	 * A binary file attribute (content not included).
	 *
	 * @param name the name
	 * @param originalName the original name of the file
	 * @param fileSize the size of the file, in bytes
	 * @param checksum the checksum of the file ({@code sha256:<hex>})
	 * @param url the path to download the file
	 */
	record BinaryFile(String name, String originalName, long fileSize, String checksum, String url) implements ConfigAttributeDto {
	}
}
