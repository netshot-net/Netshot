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

	@Override
	public UiUser authenticateWithCode(String authorizationCode, URL redirectUrl) {
		return Oidc.authenticateWithCode(authorizationCode, redirectUrl);
	}

	@Override
	public UiUser authenticateWithRadius(String username, String password, String remoteAddress) {
		return Radius.authenticate(username, password, remoteAddress);
	}

	@Override
	public UiUser authenticateWithTacacs(String username, String password, String remoteAddress) {
		return Tacacs.authenticate(username, password, remoteAddress);
	}
}
