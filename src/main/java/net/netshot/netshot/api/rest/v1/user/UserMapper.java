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

import jakarta.inject.Inject;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.UserDefinition;
import net.netshot.netshot.api.rest.v1.Secret;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.auth.AuthMapper;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * Maps the users to their v1 representation and back.
 */
public class UserMapper {

	/** The paths of the v1 objects. */
	private final V1Links links;

	/** Maps the auth objects. */
	private final AuthMapper authMapper;

	/**
	 * Instantiates the mapper.
	 *
	 * @param links the paths of the v1 objects
	 * @param authMapper maps the auth objects
	 */
	@Inject
	public UserMapper(V1Links links, AuthMapper authMapper) {
		this.links = links;
		this.authMapper = authMapper;
	}

	/**
	 * Maps a user.
	 *
	 * @param user the user (null gives null)
	 * @return the representation
	 */
	public UserDto toDto(UiUser user) {
		if (user == null) {
			return null;
		}
		boolean hasPassword = user.local() && user.hashedPassword() != null && !user.hashedPassword().isEmpty();
		return new UserDto(user.id(), this.links.of(AuthMapper.USERS_PATH, user.id()), user.username(), user.version(),
			user.changeDate(), user.username(), user.local(), user.level(), this.authMapper.roleOf(user.level()), hasPassword,
			hasPassword ? user.lastPasswordChangeDate() : null);
	}

	/**
	 * Maps a user to the request which would define it as it is (the base of a merge patch): the password is absent,
	 * so unchanged.
	 *
	 * @param user the user
	 * @return the request
	 */
	public UserRequest toRequest(UiUser user) {
		return new UserRequest(user.username(), user.local(), user.level(), null);
	}

	/**
	 * Maps a request to the settings of a user (to create or replace it).
	 *
	 * @param request the request
	 * @return the command
	 */
	public UserDefinition toDefinition(UserRequest request) {
		return new UserDefinition(request.username(), request.local(), password(request), request.level());
	}

	private String password(UserRequest request) {
		Secret password = request.password();
		if (password == null || !request.local()) {
			return null;
		}
		if (password.value() == null) {
			throw NetshotException.invalidParameter("password", "The password of a local user can't be cleared");
		}
		return password.value();
	}
}
