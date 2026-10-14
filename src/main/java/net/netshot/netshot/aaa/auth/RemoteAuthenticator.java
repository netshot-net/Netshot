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
package net.netshot.netshot.aaa.auth;

import java.net.URL;

import net.netshot.netshot.aaa.user.UiUser;

/**
 * The remote authentication methods a login can fall back to (OpenID Connect, RADIUS, TACACS+).
 * A method which is not configured simply authenticates nobody (returns null).
 *
 * <p>{@link ConfiguredRemoteAuthenticator} uses the configured servers; the tests use a fake.</p>
 */
public interface RemoteAuthenticator {

	/**
	 * Authenticates a user by exchanging an OpenID Connect authorization code.
	 *
	 * @param authorizationCode the authorization code
	 * @param redirectUrl the redirect URL the code was issued for
	 * @return the authenticated user, or null if the authentication failed or OIDC is not configured
	 */
	UiUser authenticateWithCode(String authorizationCode, URL redirectUrl);

	/**
	 * Authenticates a user against the RADIUS servers.
	 *
	 * @param username the user name
	 * @param password the password
	 * @param remoteAddress the address of the client
	 * @return the authenticated user, or null if the authentication failed or RADIUS is not configured
	 */
	UiUser authenticateWithRadius(String username, String password, String remoteAddress);

	/**
	 * Authenticates a user against the TACACS+ servers.
	 *
	 * @param username the user name
	 * @param password the password
	 * @param remoteAddress the address of the client
	 * @return the authenticated user, or null if the authentication failed or TACACS+ is not configured
	 */
	UiUser authenticateWithTacacs(String username, String password, String remoteAddress);
}
