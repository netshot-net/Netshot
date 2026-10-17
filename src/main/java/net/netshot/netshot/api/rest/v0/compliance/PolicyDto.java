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
package net.netshot.netshot.api.rest.v0.compliance;

import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.api.rest.v0.group.DeviceGroupDto;

/**
 * A compliance policy, with the device groups it applies to.
 *
 * @param id the policy ID
 * @param name the name
 * @param targetGroups the target groups
 */
@JacksonXmlRootElement(localName = "Policy")
public record PolicyDto(long id, String name, List<DeviceGroupDto> targetGroups) {
}
