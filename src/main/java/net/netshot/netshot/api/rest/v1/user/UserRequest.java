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
package net.netshot.netshot.api.rest.v1.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.api.rest.v1.Secret;

/**
 * The definition of a user.
 *
 * @param username the user name
 * @param local whether the user is a local one (authenticated by its password) or a remote one
 * @param level the permission level
 * @param password the password of a local user: write-only, required to create a local user or to make a remote one a
 *        local one, absent to keep the current one; ignored for a remote user
 */
@Schema(name = "UserRequest", description = "Definition of a Netshot user")
@ReadOnlyFrom(UserDto.class)
public record UserRequest(
	@JsonProperty(required = true) String username,
	@JsonProperty(required = true) Boolean local,
	@JsonProperty(required = true) Integer level,
	@JsonInclude(JsonInclude.Include.NON_NULL) Secret password) {
}
