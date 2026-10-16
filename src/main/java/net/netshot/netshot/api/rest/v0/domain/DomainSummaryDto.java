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
package net.netshot.netshot.api.rest.v0.domain;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * The representation of a domain given by the domain endpoints (list, add, edit): the IPv4
 * address of the server only, and no change date. (The {@link DomainDto} is the complete
 * representation of a domain, as nested in other objects.)
 *
 * @param id the domain ID
 * @param name the name
 * @param description the description
 * @param ipAddress the IPv4 address of the server of the domain
 */
@JsonView(DefaultView.class)
@JacksonXmlRootElement(localName = "RsDomain")
public record DomainSummaryDto(
	long id,
	String name,
	String description,
	String ipAddress) {
}
