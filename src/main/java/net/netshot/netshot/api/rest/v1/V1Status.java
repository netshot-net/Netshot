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
package net.netshot.netshot.api.rest.v1;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import net.netshot.netshot.platform.error.ErrorCode;

/**
 * The HTTP status of the error codes in the v1 API. It differs from the legacy one ({@link ErrorCode#getHttpStatus()},
 * kept for v0) where v0 misused {@code 412 Precondition Failed}, which v1 keeps for the version checks
 * ({@code If-Match}): an object in use or a task which can't be cancelled is a {@code 409 Conflict}, invalid
 * credentials a {@code 400}, a locked user or an expired password a {@code 403}.
 */
public final class V1Status {

	private V1Status() {
	}

	/**
	 * Gets the HTTP status of an error code.
	 *
	 * @param code the error code
	 * @return the status
	 */
	public static int of(ErrorCode code) {
		return switch (code) {
			case NETSHOT_DOMAIN_IN_USE, NETSHOT_CREDENTIALS_IN_USE, NETSHOT_DEVICE_IN_USE, NETSHOT_VAULT_INSTANCE_IN_USE,
				NETSHOT_TASK_NOT_CANCELLABLE -> 409;
			case NETSHOT_INVALID_CREDENTIALS -> 400;
			case NETSHOT_EXPIRED_PASSWORD, NETSHOT_LOCKED_USER -> 403;
			default -> code.getHttpStatus();
		};
	}

	/**
	 * Gets the title of an error code: its name in words ({@code NETSHOT_DUPLICATE_DOMAIN} is "Duplicate domain").
	 *
	 * @param code the error code
	 * @return the title
	 */
	public static String title(ErrorCode code) {
		String words = code.name().replaceFirst("^NETSHOT_", "").replace('_', ' ').toLowerCase(Locale.ROOT);
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	/**
	 * Gets the problem type URI of an error code ({@code urn:netshot:error:duplicate-domain}).
	 *
	 * @param code the error code
	 * @return the URI
	 */
	public static String type(ErrorCode code) {
		return "urn:netshot:error:" + Arrays.stream(code.name().replaceFirst("^NETSHOT_", "").split("_"))
			.map(w -> w.toLowerCase(Locale.ROOT)).collect(Collectors.joining("-"));
	}
}
