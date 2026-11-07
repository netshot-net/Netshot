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
 * The remote authentication methods as configured for the server: delegates to {@link Oidc},
 * {@link Radius} and {@link Tacacs}.
 */
public class ConfiguredRemoteAuthenticator implements RemoteAuthenticator {

	private final Oidc oidc;
	private final Radius radius;
	private final Tacacs tacacs;

	/**
	 * Creates the authenticator.
	 *
	 * @param oidc the OIDC authentication
	 * @param radius the RADIUS authentication
	 * @param tacacs the TACACS+ authentication
	 */
	public ConfiguredRemoteAuthenticator(Oidc oidc, Radius radius, Tacacs tacacs) {
		this.oidc = oidc;
		this.radius = radius;
		this.tacacs = tacacs;
	}

	@Override
	public UiUser authenticateWithCode(String authorizationCode, URL redirectUrl) {
		return this.oidc.authenticateWithCode(authorizationCode, redirectUrl);
	}

	@Override
	public UiUser authenticateWithBearerToken(String token) {
		return this.oidc.authenticateWithBearerToken(token);
	}

	@Override
	public UiUser authenticateWithRadius(String username, String password, String remoteAddress) {
		return this.radius.authenticate(username, password, remoteAddress);
	}

	@Override
	public UiUser authenticateWithTacacs(String username, String password, String remoteAddress) {
		return this.tacacs.authenticate(username, password, remoteAddress);
	}
}
