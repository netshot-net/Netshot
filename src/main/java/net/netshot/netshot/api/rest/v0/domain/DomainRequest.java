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
package net.netshot.netshot.api.rest.v0.domain;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

/**
 * The request to add or edit a domain.
 *
 * <p>Kept as a bean rather than a record: for the properties which have a default value
 * (the ID, the name and the description), an absent property and an explicit {@code null}
 * must keep giving different outcomes (the default value, or {@code null}, which the
 * service then fails on), which a bean with initialized fields does for free.</p>
 */
@XmlAccessorType(XmlAccessType.NONE)
public class DomainRequest {

	/** The id (not used). */
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private long id = -1;

	/** The name. */
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private String name = "";

	/** The description. */
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private String description = "";

	/** The IPv4 address of the server of the domain (parsed by the service). */
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private String ipAddress;
}
