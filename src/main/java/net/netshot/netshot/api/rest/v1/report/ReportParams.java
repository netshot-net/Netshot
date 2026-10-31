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
package net.netshot.netshot.api.rest.v1.report;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.ws.rs.core.MultivaluedMap;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * Strict reading of the query parameters of the reports (and of the task statistics): an unknown parameter, a
 * repeated single-valued parameter or a value which can't be read is a 400.
 */
public final class ReportParams {

	private ReportParams() {
	}

	/**
	 * Checks that a request has no other query parameter than the given ones.
	 *
	 * @param params the query parameters
	 * @param allowed the names of the accepted parameters
	 * @throws NetshotException if there is another parameter
	 */
	public static void only(MultivaluedMap<String, String> params, String... allowed) {
		Set<String> names = Set.of(allowed);
		for (String name : params.keySet()) {
			if (!names.contains(name)) {
				throw NetshotException.invalidParameter(name, "Unknown parameter '%s'".formatted(name));
			}
		}
	}

	/**
	 * Reads a parameter which can only be given once.
	 *
	 * @param params the query parameters
	 * @param name the name of the parameter
	 * @return the value, or null if the parameter is absent
	 * @throws NetshotException if the parameter is repeated or empty
	 */
	public static String single(MultivaluedMap<String, String> params, String name) {
		List<String> values = params.get(name);
		if (values == null || values.isEmpty()) {
			return null;
		}
		if (values.size() > 1) {
			throw NetshotException.invalidParameter(name, "The parameter '%s' can only be given once".formatted(name));
		}
		String value = values.get(0).trim();
		if (value.isEmpty()) {
			throw NetshotException.invalidParameter(name, "The parameter '%s' can't be empty".formatted(name));
		}
		return value;
	}

	/**
	 * Reads a mandatory parameter which can only be given once.
	 *
	 * @param params the query parameters
	 * @param name the name of the parameter
	 * @return the value
	 * @throws NetshotException if the parameter is absent, repeated or empty
	 */
	public static String required(MultivaluedMap<String, String> params, String name) {
		String value = single(params, name);
		if (value == null) {
			throw NetshotException.invalidParameter(name, "The parameter '%s' is required".formatted(name));
		}
		return value;
	}

	/**
	 * Reads the values of a parameter which can be repeated or hold comma-separated values.
	 *
	 * @param params the query parameters
	 * @param name the name of the parameter
	 * @return the distinct values (empty if the parameter is absent)
	 * @throws NetshotException if a value is empty
	 */
	public static Set<String> multiple(MultivaluedMap<String, String> params, String name) {
		Set<String> result = new LinkedHashSet<>();
		List<String> values = params.get(name);
		if (values == null) {
			return result;
		}
		for (String value : values) {
			for (String item : value.split(",", -1)) {
				if (item.trim().isEmpty()) {
					throw NetshotException.invalidParameter(name, "The parameter '%s' has an empty value".formatted(name));
				}
				result.add(item.trim());
			}
		}
		return result;
	}

	/**
	 * Parses an ID.
	 *
	 * @param name the name of the parameter
	 * @param text the text
	 * @return the ID
	 * @throws NetshotException if the text is not an ID
	 */
	public static long id(String name, String text) {
		try {
			long id = Long.parseLong(text);
			if (id >= 1) {
				return id;
			}
		}
		catch (NumberFormatException e) {
			// Same error as out of range
		}
		throw NetshotException.invalidParameter(name, "Invalid parameter '%s': an ID is expected".formatted(name));
	}

	/**
	 * Reads a parameter holding IDs (repeated or comma-separated).
	 *
	 * @param params the query parameters
	 * @param name the name of the parameter
	 * @return the IDs (empty if the parameter is absent: no filter)
	 * @throws NetshotException if a value is not an ID
	 */
	public static Set<Long> ids(MultivaluedMap<String, String> params, String name) {
		Set<Long> ids = new LinkedHashSet<>();
		for (String value : multiple(params, name)) {
			ids.add(id(name, value));
		}
		return ids;
	}

	/**
	 * Reads a mandatory parameter holding an ID.
	 *
	 * @param params the query parameters
	 * @param name the name of the parameter
	 * @return the ID
	 * @throws NetshotException if the parameter is absent, repeated or not an ID
	 */
	public static long requiredId(MultivaluedMap<String, String> params, String name) {
		return id(name, required(params, name));
	}

	/**
	 * Reads a parameter holding names of an enumeration (repeated or comma-separated, case insensitive).
	 *
	 * @param <E> the enumeration
	 * @param params the query parameters
	 * @param name the name of the parameter
	 * @param type the enumeration
	 * @return the values (empty if the parameter is absent: no filter)
	 * @throws NetshotException if a value is not a constant of the enumeration
	 */
	public static <E extends Enum<E>> Set<E> enums(MultivaluedMap<String, String> params, String name, Class<E> type) {
		Set<E> values = new LinkedHashSet<>();
		for (String value : multiple(params, name)) {
			values.add(constant(name, value, type));
		}
		return values;
	}

	/**
	 * Parses a constant of an enumeration (case insensitive).
	 *
	 * @param <E> the enumeration
	 * @param name the name of the parameter
	 * @param text the text
	 * @param type the enumeration
	 * @return the constant
	 * @throws NetshotException if the text is not a constant of the enumeration
	 */
	public static <E extends Enum<E>> E constant(String name, String text, Class<E> type) {
		try {
			return Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			List<String> names = new ArrayList<>();
			for (E constant : type.getEnumConstants()) {
				names.add(constant.name());
			}
			throw NetshotException.invalidParameter(name,
				"Invalid value '%s' for the parameter '%s': one of %s is expected".formatted(text, name, names));
		}
	}

	/**
	 * Reads an integer parameter.
	 *
	 * @param params the query parameters
	 * @param name the name of the parameter
	 * @param min the minimum value
	 * @param max the maximum value
	 * @return the value, or null if the parameter is absent
	 * @throws NetshotException if the value is not an integer in the range
	 */
	public static Integer integer(MultivaluedMap<String, String> params, String name, int min, int max) {
		String text = single(params, name);
		if (text == null) {
			return null;
		}
		try {
			int value = Integer.parseInt(text);
			if (value >= min && value <= max) {
				return value;
			}
		}
		catch (NumberFormatException e) {
			// Same error as out of range
		}
		throw NetshotException.invalidParameter(name,
			"Invalid parameter '%s': an integer from %d to %d is expected".formatted(name, min, max));
	}

	/**
	 * Reads a boolean parameter.
	 *
	 * @param params the query parameters
	 * @param name the name of the parameter
	 * @return the value (false if the parameter is absent)
	 * @throws NetshotException if the value is neither true nor false
	 */
	public static boolean bool(MultivaluedMap<String, String> params, String name) {
		String text = single(params, name);
		if (text == null || "false".equalsIgnoreCase(text)) {
			return false;
		}
		if ("true".equalsIgnoreCase(text)) {
			return true;
		}
		throw NetshotException.invalidParameter(name, "Invalid parameter '%s': true or false is expected".formatted(name));
	}

	/**
	 * Reads a date parameter: an ISO-8601 date and time with an offset, or a date (midnight UTC).
	 *
	 * @param params the query parameters
	 * @param name the name of the parameter
	 * @return the date, or null if the parameter is absent
	 * @throws NetshotException if the value can't be read
	 */
	public static Instant instant(MultivaluedMap<String, String> params, String name) {
		String text = single(params, name);
		if (text == null) {
			return null;
		}
		try {
			if (text.length() == 10) {
				return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant();
			}
			return OffsetDateTime.parse(text).toInstant();
		}
		catch (DateTimeException e) {
			throw NetshotException.invalidParameter(name,
				"Invalid parameter '%s': an ISO-8601 date and time with an offset (such as 2026-10-30T09:12:44Z) is expected".formatted(name));
		}
	}

	/**
	 * Reads a time zone parameter (IANA name such as {@code Europe/Paris}, or an offset).
	 *
	 * @param params the query parameters
	 * @param name the name of the parameter
	 * @return the time zone, or null if the parameter is absent
	 * @throws NetshotException if the value is not a time zone
	 */
	public static ZoneId zone(MultivaluedMap<String, String> params, String name) {
		String text = single(params, name);
		if (text == null) {
			return null;
		}
		try {
			return ZoneId.of(text);
		}
		catch (DateTimeException e) {
			throw NetshotException.invalidParameter(name, "Invalid parameter '%s': a time zone such as Europe/Paris is expected".formatted(name));
		}
	}
}
