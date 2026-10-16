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
package net.netshot.netshot.api.rest.v0.diagnostic;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.api.rest.v0.device.DeviceGroupDto;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * The representation of a diagnostic, of one of the three kinds. The {@code type} property (an
 * attribute in XML) is the name of the kind.
 */
@JsonView(DefaultView.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = DiagnosticDto.Simple.class, name = "SimpleDiagnostic"),
	@JsonSubTypes.Type(value = DiagnosticDto.JavaScript.class, name = "JavaScriptDiagnostic"),
	@JsonSubTypes.Type(value = DiagnosticDto.Python.class, name = "PythonDiagnostic"),
})
public sealed interface DiagnosticDto {

	/**
	 * Gets the diagnostic ID.
	 *
	 * @return the ID
	 */
	long id();

	/**
	 * Gets the name of the diagnostic.
	 *
	 * @return the name
	 */
	String name();

	/**
	 * A diagnostic running a CLI command and optionally modifying its output.
	 *
	 * @param cliMode the CLI mode to run the command in
	 * @param command the CLI command
	 * @param deviceDriver the name of the device driver the diagnostic applies to
	 * @param deviceDriverDescription the description of the device driver
	 * @param enabled whether the diagnostic is enabled
	 * @param id the diagnostic ID
	 * @param modifierPattern the pattern to search in the output
	 * @param modifierReplacement the replacement of the pattern
	 * @param name the name
	 * @param resultType the type of result
	 * @param targetGroup the group of devices the diagnostic applies to, or null
	 */
	@JsonView(DefaultView.class)
	@JacksonXmlRootElement(localName = "SimpleDiagnostic")
	record Simple(
		String cliMode,
		String command,
		String deviceDriver,
		String deviceDriverDescription,
		boolean enabled,
		long id,
		String modifierPattern,
		String modifierReplacement,
		String name,
		AttributeType resultType,
		DeviceGroupDto targetGroup) implements DiagnosticDto {
	}

	/**
	 * A diagnostic evaluated by a JavaScript script.
	 *
	 * @param enabled whether the diagnostic is enabled
	 * @param id the diagnostic ID
	 * @param name the name
	 * @param resultType the type of result
	 * @param script the script
	 * @param targetGroup the group of devices the diagnostic applies to, or null
	 */
	@JsonView(DefaultView.class)
	@JacksonXmlRootElement(localName = "JavaScriptDiagnostic")
	record JavaScript(
		boolean enabled,
		long id,
		String name,
		AttributeType resultType,
		String script,
		DeviceGroupDto targetGroup) implements DiagnosticDto {
	}

	/**
	 * A diagnostic evaluated by a Python script.
	 *
	 * @param enabled whether the diagnostic is enabled
	 * @param id the diagnostic ID
	 * @param name the name
	 * @param resultType the type of result
	 * @param script the script
	 * @param targetGroup the group of devices the diagnostic applies to, or null
	 */
	@JsonView(DefaultView.class)
	@JacksonXmlRootElement(localName = "PythonDiagnostic")
	record Python(
		boolean enabled,
		long id,
		String name,
		AttributeType resultType,
		String script,
		DeviceGroupDto targetGroup) implements DiagnosticDto {
	}
}
