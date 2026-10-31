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

import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.auth.ApiTokenNames;
import net.netshot.netshot.api.rest.v1.auth.AuthMapper;

/**
 * Maps the API tokens to their v1 representation.
 */
public final class ApiTokenMapper {

	private ApiTokenMapper() {
	}

	/**
	 * Maps an API token (without its value).
	 *
	 * @param token the API token (null gives null)
	 * @return the representation
	 */
	public static ApiTokenDto toDto(ApiToken token) {
		if (token == null) {
			return null;
		}
		return new ApiTokenDto(token.id(), V1Links.of(AuthMapper.API_TOKENS_PATH, token.id()), ApiTokenNames.display(token),
			token.description(), token.level(), AuthMapper.roleOf(token.level()));
	}

	/**
	 * Maps a newly created API token, with its value.
	 *
	 * @param token the API token
	 * @param value the clear token value
	 * @return the representation
	 */
	public static ApiTokenCreatedDto toCreatedDto(ApiToken token, String value) {
		return new ApiTokenCreatedDto(token.id(), V1Links.of(AuthMapper.API_TOKENS_PATH, token.id()), ApiTokenNames.display(token),
			token.description(), token.level(), AuthMapper.roleOf(token.level()), value);
	}
}
