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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * The request to add or edit a domain.
 *
 * <p>Kept as a bean rather than a record: for the properties which have a default value
 * (the ID, the name and the description), an absent property and an explicit {@code null}
 * must keep giving different outcomes (the default value, or {@code null}, which the
 * service then fails on), which a bean with initialized fields does for free.</p>
 */
// Transitional: with @XmlRootElement, Jersey reads a XML body through its JAXB provider, which fails
// (there is no JAXB runtime), as it always did for this request (see the API contract goldens).
@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class DomainRequest {

	/** The id (not used). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private long id = -1;

	/** The name. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String name = "";

	/** The description. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String description = "";

	/** The IPv4 address of the server of the domain. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@JsonDeserialize(using = Network4Address.AddressOnlyDeserializer.class),
	}))
	@Setter
	private Network4Address ipAddress;
}
