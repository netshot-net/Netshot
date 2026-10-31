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
package net.netshot.netshot.api.rest.v1.domain;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;

/**
 * A device domain.
 *
 * @param id the ID
 * @param url the path of the domain
 * @param display the display name
 * @param version the version, incremented on each change (the ETag)
 * @param changeDate the date of the last change
 * @param name the name
 * @param description the description
 * @param server4Address the IPv4 address of Netshot as seen by the devices of the domain
 * @param server6Address the IPv6 address of Netshot as seen by the devices of the domain
 */
@Schema(name = "Domain", description = "Device domain")
public record DomainDto(
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) long id,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String url,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String display,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) long version,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) Instant changeDate,
	@Brief String name,
	String description,
	String server4Address,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String server6Address) {
}
