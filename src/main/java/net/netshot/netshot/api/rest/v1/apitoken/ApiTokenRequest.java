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
package net.netshot.netshot.api.rest.v1.apitoken;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.api.rest.v1.Secret;

/**
 * The definition of an API token to create.
 *
 * @param description the description (empty if absent)
 * @param level the permission level
 * @param token the token value to register (32 alphanumeric characters); a random one is generated if absent
 */
@Schema(name = "ApiTokenRequest", description = "Definition of an API token")
@ReadOnlyFrom(ApiTokenDto.class)
public record ApiTokenRequest(
	String description,
	@JsonProperty(required = true) Integer level,
	Secret token) {
}
