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



/**
 * The request to log in or to change the own password of the current user.
 *
 * @param username the user name
 * @param password the (current) password
 * @param newPassword the new password
 * @param authorizationCode the OIDC authorization code
 * @param redirectUri the redirect URI the OIDC authorization code was issued for
 */
public record LoginRequest(
	String username,
	String password,
	String newPassword,
	String authorizationCode,
	String redirectUri) {
}
