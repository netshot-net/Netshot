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
package net.netshot.netshot.api.rest.v1.auth;

import java.time.Instant;

import jakarta.inject.Inject;

import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.V1Links;

/**
 * Maps the principals and the sessions to their v1 representation.
 */
public class AuthMapper {

	/** The path of the users. */
	public static final String USERS_PATH = "users";

	/** The path of the API tokens. */
	public static final String API_TOKENS_PATH = "api-tokens";

	/** The path of the authentication resources. */
	public static final String AUTH_PATH = "auth";

	/** The paths of the v1 objects. */
	private final V1Links links;

	/**
	 * Instantiates the mapper.
	 *
	 * @param links the paths of the v1 objects
	 */
	@Inject
	public AuthMapper(V1Links links) {
		this.links = links;
	}

	/**
	 * Gets the name of the highest role a permission level gives.
	 *
	 * @param level the level
	 * @return the name of the role ({@code read-only}, {@code operator}, {@code read-write}, {@code execute-read-write}
	 *         or {@code admin}), or null if the level is lower than the one of the lowest role
	 */
	public String roleOf(int level) {
		String role = null;
		for (User.Role candidate : User.Role.values()) {
			if (level >= candidate.getLevel()) {
				role = candidate.getName();
			}
		}
		return role;
	}

	/**
	 * Maps the principal of a request.
	 *
	 * @param principal the user or API token the request is authenticated as
	 * @param authMethod the authentication scheme ({@code SESSION}, {@code JWT} or {@code API_TOKEN})
	 * @return the representation
	 */
	public CurrentUserDto toDto(User principal, String authMethod) {
		return switch (principal) {
			case UiUser user -> new CurrentUserDto(user.id(), user.id() > 0 ? this.links.of(USERS_PATH, user.id()) : null,
				user.username(), "USER", authMethod, user.username(), user.local(), user.level(), roleOf(user.level()));
			case ApiToken token -> new CurrentUserDto(token.id(), this.links.of(API_TOKENS_PATH, token.id()),
				ApiTokenNames.display(token), "API_TOKEN", authMethod, null, null, token.level(), roleOf(token.level()));
		};
	}

	/**
	 * Maps a session.
	 *
	 * @param user the user of the session
	 * @param creationTime the creation time of the session, in milliseconds since the epoch
	 * @param maxIdleTime the maximum idle time of the session, in seconds
	 * @return the representation
	 */
	public SessionDto toSessionDto(UiUser user, long creationTime, int maxIdleTime) {
		return new SessionDto(this.links.of(AUTH_PATH, "session"), Instant.ofEpochMilli(creationTime), maxIdleTime,
			toDto(user, "SESSION"));
	}
}
