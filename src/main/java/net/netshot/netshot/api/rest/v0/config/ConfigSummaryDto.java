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

import java.util.Date;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.inventory.device.Device;

/**
 * The summary representation of a configuration, as listed by the configuration changes.
 *
 * @param id the configuration ID
 * @param deviceName the name of the device
 * @param deviceId the ID of the device
 * @param changeDate the date of the change
 * @param author the author of the change
 * @param deviceNetworkClass the network class of the device
 */
@JacksonXmlRootElement(localName = "RsLightConfig")
public record ConfigSummaryDto(
	long id,
	String deviceName,
	long deviceId,
	Date changeDate,
	String author,
	Device.NetworkClass deviceNetworkClass) {
}
