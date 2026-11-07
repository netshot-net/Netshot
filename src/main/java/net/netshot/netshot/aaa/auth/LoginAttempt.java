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
package net.netshot.netshot.aaa.auth;

/**
 * What a client presents to log in.
 *
 * @param username the user name, or null when logging in with an OIDC authorization code
 * @param password the password
 * @param newPassword the new password to set while logging in, if any (to change an expired one)
 * @param authorizationCode the OIDC authorization code, if any
 * @param redirectUri the redirect URI the OIDC authorization code was issued for
 * @param remoteAddress the address of the client (for the audit log and the remote servers)
 */
public record LoginAttempt(
	String username,
	String password,
	String newPassword,
	String authorizationCode,
	String redirectUri,
	String remoteAddress) {
}
