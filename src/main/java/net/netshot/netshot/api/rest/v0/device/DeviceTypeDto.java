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

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.driver.AttributeDefinition.AttributeLevel;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.driver.DeviceDriver.DriverProtocol;
import net.netshot.netshot.driver.DeviceDriver.LocationType;
import net.netshot.netshot.driver.DriverValueType;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * The representation of a device type: a device driver loaded by the server.
 *
 * @param name the name of the driver
 * @param author the author of the driver
 * @param description the description of the driver
 * @param version the version of the driver
 * @param priority the priority of the driver for autodiscovery
 * @param attributes the attributes the driver collects
 * @param options the options the driver declares, by name
 * @param protocols the protocols the driver provides
 * @param cliMainModes the main CLI modes the driver supports
 * @param sourceHash the hash of the source of the driver
 * @param location where the driver comes from
 * @param accessDefinitions the accesses the driver declares, by name
 */
@JsonView(DefaultView.class)
@JacksonXmlRootElement(localName = "DeviceDriver")
public record DeviceTypeDto(
	String name,
	String author,
	String description,
	String version,
	int priority,
	List<Attribute> attributes,
	Map<String, Option> options,
	List<DriverProtocol> protocols,
	List<String> cliMainModes,
	String sourceHash,
	Location location,
	Map<String, Access> accessDefinitions) {

	/**
	 * An attribute collected by a driver.
	 *
	 * @param type the type of the values
	 * @param level the level (device or configuration)
	 * @param name the name
	 * @param title the title
	 * @param comparable whether the values can be compared
	 * @param searchable whether the values can be searched
	 * @param checkable whether the values can be checked by compliance rules
	 */
	@JsonView(DefaultView.class)
	public record Attribute(
		AttributeType type,
		AttributeLevel level,
		String name,
		String title,
		boolean comparable,
		boolean searchable,
		boolean checkable) {
	}

	/**
	 * An option declared by a driver.
	 *
	 * @param name the name
	 * @param title the title
	 * @param type the type of the values
	 * @param choices the valid choices of a text option
	 * @param defaultValue the default value
	 */
	@JsonView(DefaultView.class)
	public record Option(
		String name,
		String title,
		DriverValueType type,
		List<String> choices,
		Object defaultValue) {
	}

	/**
	 * Where a driver comes from.
	 *
	 * @param type the type of location
	 * @param fileName the name of the file
	 */
	@JsonView(DefaultView.class)
	public record Location(LocationType type, String fileName) {
	}

	/**
	 * An access declared by a driver.
	 *
	 * @param name the name
	 * @param protocol the protocol
	 * @param description the description
	 * @param defaultPort the default port
	 * @param group the group of alternative accesses
	 * @param priority the priority within the group
	 */
	@JsonView(DefaultView.class)
	public record Access(
		String name,
		DriverProtocol protocol,
		String description,
		int defaultPort,
		String group,
		int priority) {
	}
}
