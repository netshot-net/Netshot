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
package net.netshot.netshot.api.rest.v0.report;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.api.rest.v0.device.DeviceSummaryDto;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * A device with its software version.
 *
 * @param device the device (its properties are published at the level of the others)
 * @param softwareVersion the software version
 */
@JsonView(DefaultView.class)
@JacksonXmlRootElement(localName = "RsLightSoftwareLevelDevice")
public record SoftwareLevelDeviceDto(@JsonUnwrapped DeviceSummaryDto device, String softwareVersion) {
}
