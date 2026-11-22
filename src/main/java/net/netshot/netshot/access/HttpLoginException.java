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
package net.netshot.netshot.access;

import java.io.IOException;

import lombok.Getter;

/**
 * Thrown when the login request of a "cookie" HTTP authentication comes back with a non-2xx status:
 * with 401 or 403, the credentials were refused, as for a request answered with that status.
 */
public class HttpLoginException extends IOException {
	private static final long serialVersionUID = 1L;

	/** The HTTP status of the login response. */
	@Getter
	private final int status;

	/**
	 * Creates the exception.
	 *
	 * @param status the HTTP status of the login response
	 */
	public HttpLoginException(int status) {
		super("Cookie-auth login failed (HTTP status " + status + ").");
		this.status = status;
	}
}
