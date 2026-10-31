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
package net.netshot.netshot.api.rest.v1.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The credentials to log in: a user name and a password, or an OIDC authorization code with the redirect URI it was
 * obtained with. One of the two forms is required, not both.
 *
 * @param username the user name (with the password)
 * @param password the password (with the user name)
 * @param newPassword the new password, to change the password while logging in (required when the password of the
 *        user has expired)
 * @param authorizationCode the OIDC authorization code (with the redirect URI)
 * @param redirectUri the redirect URI the authorization code was obtained with
 */
@Schema(name = "SessionRequest", description = "Credentials to log in: username and password, or OIDC authorization code")
public record SessionRequest(
	String username,
	@Schema(format = "password") String password,
	@Schema(format = "password") String newPassword,
	@Schema(format = "password") String authorizationCode,
	String redirectUri) {
}
