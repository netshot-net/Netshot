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
package net.netshot.netshot.domain;

/**
 * What the client gives to add a domain or to edit one. The strings are given as they come
 * from the client (the service trims them).
 *
 * @param name the name
 * @param description the description
 * @param server4Address the IPv4 address of the server of the domain, as text (the service parses it)
 */
public record DomainDefinition(String name, String description, String server4Address) {
}
