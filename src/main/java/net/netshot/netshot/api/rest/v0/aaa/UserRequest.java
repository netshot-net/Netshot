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
package net.netshot.netshot.api.rest.v0.aaa;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import net.netshot.netshot.credentials.HideSecretDeserializer;
import net.netshot.netshot.credentials.HideSecretSerializer;

/**
 * The request to add or edit a user.
 *
 * @param id the user ID (ignored)
 * @param username the user name
 * @param password the clear password (a local user only)
 * @param level the authorization level
 * @param local true for a user authenticated by a local password
 */
public record UserRequest(
	long id,
	String username,
	@JsonSerialize(contentUsing = HideSecretSerializer.class) @JsonDeserialize(contentUsing = HideSecretDeserializer.class) String password,
	int level,
	boolean local) {
}
