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

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonView;

import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * A management domain, as nested in the device representations.
 *
 * @param id the domain ID
 * @param changeDate the last change date
 * @param name the name
 * @param description the description
 * @param server4Address the IPv4 address of the domain server
 * @param server6Address the IPv6 address of the domain server
 */
@JsonView(DefaultView.class)
public record DomainDto(
	long id,
	Date changeDate,
	String name,
	String description,
	String server4Address,
	String server6Address) {
}
