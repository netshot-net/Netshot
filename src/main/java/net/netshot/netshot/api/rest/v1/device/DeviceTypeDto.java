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
package net.netshot.netshot.api.rest.v1.device;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;
import net.netshot.netshot.driver.AttributeDefinition.AttributeLevel;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.driver.DeviceDriver.DriverProtocol;
import net.netshot.netshot.driver.DeviceDriver.LocationType;
import net.netshot.netshot.driver.DriverValueType;

/**
 * A device type: a device driver loaded by the server. It has no {@code id} nor {@code url}: the name of the
 * driver is what devices refer to ({@code driver} property of a device).
 *
 * @param name the name of the driver
 * @param description the description of the driver
 * @param version the version of the driver
 * @param author the author of the driver
 * @param priority the priority of the driver for autodiscovery
 * @param protocols the protocols the driver provides
 * @param cliMainModes the main CLI modes the driver supports
 * @param sourceHash the hash of the source of the driver
 * @param location where the driver comes from
 * @param attributes the attributes the driver collects
 * @param options the options the driver declares
 * @param accesses the accesses the driver declares
 */
@Schema(name = "DeviceType", description = "Device type (device driver loaded by the server)")
public record DeviceTypeDto(
	@Brief String name,
	@Brief String description,
	@Brief String version,
	String author,
	int priority,
	List<DriverProtocol> protocols,
	List<String> cliMainModes,
	String sourceHash,
	Location location,
	List<Attribute> attributes,
	List<Option> options,
	List<Access> accesses) {

	/**
	 * An attribute collected by a driver.
	 *
	 * @param name the name
	 * @param title the title
	 * @param type the type of the values
	 * @param level the level (device or configuration)
	 * @param comparable whether the values can be compared
	 * @param searchable whether the values can be searched
	 * @param checkable whether the values can be checked by compliance rules
	 */
	public record Attribute(
		String name,
		String title,
		AttributeType type,
		AttributeLevel level,
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
	public record Access(
		String name,
		DriverProtocol protocol,
		String description,
		int defaultPort,
		String group,
		int priority) {
	}
}
