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
package net.netshot.netshot.api.rest.v0.aaa;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * An API token. Neither the token nor its hash is part of the representation: the value is
 * only known by the administrator who created it.
 *
 * @param id the token ID
 * @param description the description
 * @param level the authorization level
 */
@JsonView(DefaultView.class)
@JacksonXmlRootElement(localName = "ApiToken")
public record ApiTokenDto(
	long id,
	String description,
	int level) implements CurrentUserDto {
}
