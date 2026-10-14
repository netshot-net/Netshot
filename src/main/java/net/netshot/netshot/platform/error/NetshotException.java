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
package net.netshot.netshot.platform.error;

import lombok.Getter;

/**
 * Neutral exception thrown by the services (and anything below the API
 * layers) for an error which has to be reported to the client: it carries an
 * {@link ErrorCode} and a message, and knows nothing about the transport.
 * Each API channel translates it to its own error format (the REST API
 * answers with the HTTP status of the code and a body holding the message and
 * the numeric code).
 */
public class NetshotException extends RuntimeException {

	private static final long serialVersionUID = 4829205112267352419L;

	/** The error code. */
	@Getter
	private final ErrorCode errorCode;

	/**
	 * Instantiates a new exception.
	 *
	 * @param errorCode the error code
	 * @param message the message, reported to the client
	 */
	public NetshotException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	/**
	 * Instantiates a new exception.
	 *
	 * @param errorCode the error code
	 * @param message the message, reported to the client
	 * @param cause the cause
	 */
	public NetshotException(ErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}
}
