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
package net.netshot.netshot.api.rest.v0;

import java.util.function.Supplier;

import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * Translates the errors of the services to the ones the v0 API has always reported. The services report the errors of
 * the v1 API (a missing object is NOT_FOUND, a faulty property names its target); where the v0 API reported another
 * code or message for the same case, its resources run the service call through {@link #translate}.
 */
public final class V0Errors {

	private V0Errors() {
	}

	/**
	 * A translation of an error: the errors matching it are reported with the v0 code and message.
	 *
	 * @param code the code of the service error, or null for any code
	 * @param target the target of the service error, or null for any (or no) target
	 * @param v0Code the code reported by the v0 API
	 * @param v0Message the message reported by the v0 API, or null to keep the message of the service error
	 */
	public record Translation(ErrorCode code, String target, ErrorCode v0Code, String v0Message) {

		boolean matches(NetshotException e) {
			return (this.code == null || this.code == e.getErrorCode())
				&& (this.target == null || this.target.equals(e.getTarget()));
		}

		NetshotException apply(NetshotException e) {
			return new NetshotException(this.v0Code, this.v0Message == null ? e.getMessage() : this.v0Message, e);
		}
	}

	/**
	 * Translates the errors of a code.
	 *
	 * @param code the code of the service error
	 * @param v0Code the code reported by the v0 API
	 * @param v0Message the message reported by the v0 API, or null to keep the message of the service error
	 * @return the translation
	 */
	public static Translation code(ErrorCode code, ErrorCode v0Code, String v0Message) {
		return new Translation(code, null, v0Code, v0Message);
	}

	/**
	 * Translates the errors of an invalid request parameter.
	 *
	 * @param target the faulty parameter named by the service error
	 * @param v0Code the code reported by the v0 API
	 * @param v0Message the message reported by the v0 API, or null to keep the message of the service error
	 * @return the translation
	 */
	public static Translation target(String target, ErrorCode v0Code, String v0Message) {
		return new Translation(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, target, v0Code, v0Message);
	}

	/**
	 * Runs a service call, translating its errors with the first matching translation.
	 *
	 * @param <T> the type of the result
	 * @param call the service call
	 * @param translations the translations
	 * @return the result of the call
	 * @throws NetshotException the error of the call, translated if a translation matches it
	 */
	public static <T> T translate(Supplier<T> call, Translation... translations) {
		try {
			return call.get();
		}
		catch (NetshotException e) {
			for (Translation translation : translations) {
				if (translation.matches(e)) {
					throw translation.apply(e);
				}
			}
			throw e;
		}
	}
}
