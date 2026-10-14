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
package net.netshot.netshot.api.rest;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import net.netshot.netshot.platform.error.ErrorCode;

/**
 * The NetshotBadRequestException class, a WebApplication exception
 * embedding an error message, and HTTP status code, to be sent to the REST client.
 */
public class NetshotBadRequestException extends WebApplicationException {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = -4538169756895835186L;

	/**
	 * Instantiates a new netshot bad request exception.
	 *
	 * @param message the message
	 * @param reason the reason
	 */
	public NetshotBadRequestException(String message, ErrorCode reason) {
		super(Response.status(reason.getHttpStatus())
			.entity(new RsErrorBean(message, reason.getCode())).build());
	}
}
