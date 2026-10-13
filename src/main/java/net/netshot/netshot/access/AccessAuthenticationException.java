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
package net.netshot.netshot.access;

/**
 * Thrown when a single candidate credential set failed to authenticate and
 * {@code autoTryCredentials} was disabled for the client, so the driver script
 * is expected to catch it and decide whether to call
 * {@code client.tryNextCredentials()} itself. The message intentionally
 * contains "Authentication failed" so it keeps matching the historical
 * message-based convention used elsewhere (e.g. driver-declared CLI "fail"
 * mode strings), letting the JS wrapper flag it with an
 * {@code authenticationFailed} marker.
 */
public class AccessAuthenticationException extends InvalidCredentialsException {
	private static final long serialVersionUID = 1L;

	public AccessAuthenticationException(String message) {
		super(message);
	}
}
