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

	/** The request parameter or body property the error is about (null if none in particular). */
	@Getter
	private final String target;

	/**
	 * Instantiates a new exception.
	 *
	 * @param errorCode the error code
	 * @param message the message, reported to the client
	 */
	public NetshotException(ErrorCode errorCode, String message) {
		this(errorCode, message, (String) null);
	}

	/**
	 * Instantiates a new exception about a given parameter or property of the request.
	 *
	 * @param errorCode the error code
	 * @param message the message, reported to the client
	 * @param target the name of the parameter or property the error is about, or null
	 */
	public NetshotException(ErrorCode errorCode, String message, String target) {
		super(message);
		this.errorCode = errorCode;
		this.target = target;
	}

	/**
	 * Builds the error of an invalid request parameter.
	 *
	 * @param target the name of the parameter
	 * @param message the message, reported to the client
	 * @return the exception
	 */
	public static NetshotException invalidParameter(String target, String message) {
		return new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, message, target);
	}

	/**
	 * Builds the error of a change conditioned on a version the object isn't at anymore.
	 *
	 * @param object what the object is, for the message ("domain")
	 * @param expectedVersion the version the change was conditioned on
	 * @return the exception
	 */
	public static NetshotException versionMismatch(String object, long expectedVersion) {
		return new NetshotException(ErrorCode.NETSHOT_VERSION_MISMATCH,
			"The %s was changed: it isn't at version %d anymore.".formatted(object, expectedVersion));
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
		this.target = null;
	}
}
