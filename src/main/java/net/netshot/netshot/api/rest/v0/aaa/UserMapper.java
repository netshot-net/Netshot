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
package net.netshot.netshot.api.rest.v0.aaa;

import java.util.ArrayList;
import java.util.List;

import net.netshot.netshot.aaa.auth.LoginAttempt;
import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.aaa.user.UserCreation;
import net.netshot.netshot.aaa.user.UserUpdate;

/**
 * Maps the users and the API tokens between the AAA entities and their REST representation,
 * and the requests to the commands of the services. All the methods are pure functions.
 */
public final class UserMapper {

	private UserMapper() {
		// Static helpers only
	}

	/**
	 * Maps a user.
	 *
	 * @param user the user
	 * @return the DTO
	 */
	public static UserDto toDto(UiUser user) {
		return new UserDto(user.id(), user.local(), user.username(), user.level());
	}

	/**
	 * Maps some users.
	 *
	 * @param users the users
	 * @return the DTOs
	 */
	public static List<UserDto> toUserDtos(List<UiUser> users) {
		List<UserDto> dtos = new ArrayList<>();
		users.forEach(user -> dtos.add(toDto(user)));
		return dtos;
	}

	/**
	 * Maps an API token.
	 *
	 * @param apiToken the API token
	 * @return the DTO
	 */
	public static ApiTokenDto toDto(ApiToken apiToken) {
		return new ApiTokenDto(apiToken.id(), apiToken.description(), apiToken.level());
	}

	/**
	 * Maps some API tokens.
	 *
	 * @param apiTokens the API tokens
	 * @return the DTOs
	 */
	public static List<ApiTokenDto> toApiTokenDtos(List<ApiToken> apiTokens) {
		List<ApiTokenDto> dtos = new ArrayList<>();
		apiTokens.forEach(apiToken -> dtos.add(toDto(apiToken)));
		return dtos;
	}

	/**
	 * Maps the principal of a request, which is either a user or an API token.
	 *
	 * @param principal the principal
	 * @return the DTO
	 */
	public static CurrentUserDto toCurrentUserDto(User principal) {
		return switch (principal) {
			case UiUser user -> toDto(user);
			case ApiToken apiToken -> toDto(apiToken);
		};
	}

	/**
	 * Maps the request to create a user.
	 *
	 * @param request the request
	 * @return the command
	 */
	public static UserCreation toCreation(UserRequest request) {
		return new UserCreation(request.username(), request.local(), request.password(), request.level());
	}

	/**
	 * Maps the request to edit a user.
	 *
	 * @param request the request
	 * @return the command
	 */
	public static UserUpdate toUpdate(UserRequest request) {
		return new UserUpdate(request.username(), request.local(), request.password(), request.level());
	}

	/**
	 * Maps the request to log in.
	 *
	 * @param request the request
	 * @param remoteAddress the address of the client
	 * @return the credentials presented
	 */
	public static LoginAttempt toAttempt(LoginRequest request, String remoteAddress) {
		return new LoginAttempt(request.username(), request.password(), request.newPassword(),
			request.authorizationCode(), request.redirectUri(), remoteAddress);
	}
}
