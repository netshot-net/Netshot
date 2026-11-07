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
package net.netshot.netshot.platform.query;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.netshot.netshot.platform.error.NetshotException;

/**
 * The type of a field which can be filtered on: it tells which lookups apply and how the values given as
 * text are read.
 */
public enum FieldType {

	/** Text. */
	STRING(EnumSet.of(Lookup.EXACT, Lookup.N, Lookup.IE, Lookup.NIE, Lookup.IC, Lookup.NIC, Lookup.ISW, Lookup.NISW,
		Lookup.IEW, Lookup.NIEW, Lookup.REGEX, Lookup.IREGEX, Lookup.EMPTY)),
	/** Integer number (including the IDs and the references to other objects by ID). */
	INTEGER(EnumSet.of(Lookup.EXACT, Lookup.N, Lookup.LT, Lookup.LTE, Lookup.GT, Lookup.GTE, Lookup.EMPTY)),
	/** Boolean. */
	BOOLEAN(EnumSet.of(Lookup.EXACT)),
	/** One of a set of names. */
	ENUM(EnumSet.of(Lookup.EXACT, Lookup.N, Lookup.EMPTY)),
	/** Date and time, given in ISO-8601 with a time zone ({@code 2026-10-30T10:00:00Z}), or a date (midnight UTC). */
	DATE(EnumSet.of(Lookup.EXACT, Lookup.N, Lookup.LT, Lookup.LTE, Lookup.GT, Lookup.GTE, Lookup.EMPTY)),
	/** IP address (IPv4 or IPv6). */
	INET(EnumSet.of(Lookup.EXACT, Lookup.N, Lookup.NET, Lookup.EMPTY));

	/** IPv4 address literal. */
	private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

	/** Hexadecimal digits, colons and dots: IPv6 address literal (checked by InetAddress afterwards). */
	private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]*:[0-9a-fA-F:.]*$");

	/** The lookups which apply to the type. */
	private final Set<Lookup> lookups;

	FieldType(Set<Lookup> lookups) {
		this.lookups = lookups;
	}

	/**
	 * Gets the lookups which apply to the fields of this type (by default).
	 *
	 * @return the lookups
	 */
	public Set<Lookup> lookups() {
		return EnumSet.copyOf(this.lookups);
	}

	/**
	 * Reads a value given as text.
	 *
	 * @param field the field the value is for
	 * @param lookup the lookup
	 * @param parameter the name of the request parameter (for the error message)
	 * @param text the value
	 * @return the value: a {@code Boolean} for the {@code empty} lookup, otherwise a {@code String} (STRING, ENUM:
	 *         the name as declared; INET with the {@code net} lookup: the subnet), {@code Long}, {@code Boolean},
	 *         {@code Instant} or {@code InetAddress}
	 * @throws NetshotException if the value can't be read
	 */
	public Object parse(FieldSpec field, Lookup lookup, String parameter, String text) {
		if (lookup == Lookup.EMPTY) {
			return parseBoolean(parameter, text);
		}
		return switch (this) {
			case STRING -> {
				if (lookup == Lookup.REGEX || lookup == Lookup.IREGEX) {
					try {
						Pattern.compile(text);
					}
					catch (PatternSyntaxException e) {
						throw NetshotException.invalidParameter(parameter,
							"Invalid regular expression '%s' for parameter '%s'".formatted(text, parameter));
					}
				}
				yield text;
			}
			case INTEGER -> {
				try {
					yield Long.parseLong(text.trim());
				}
				catch (NumberFormatException e) {
					throw NetshotException.invalidParameter(parameter,
						"Invalid value '%s' for parameter '%s': an integer is expected".formatted(text, parameter));
				}
			}
			case BOOLEAN -> parseBoolean(parameter, text);
			case ENUM -> field.enumValues().stream()
				.filter(v -> v.equalsIgnoreCase(text.trim()))
				.findFirst()
				.orElseThrow(() -> NetshotException.invalidParameter(parameter,
					"Invalid value '%s' for parameter '%s': expected one of %s".formatted(text, parameter, field.enumValues())));
			case DATE -> parseDate(parameter, text.trim());
			case INET -> lookup == Lookup.NET ? parseSubnet(parameter, text.trim()) : parseAddress(parameter, text.trim());
		};
	}

	private static Boolean parseBoolean(String parameter, String text) {
		String value = text.trim().toLowerCase(Locale.ROOT);
		if ("true".equals(value)) {
			return Boolean.TRUE;
		}
		if ("false".equals(value)) {
			return Boolean.FALSE;
		}
		throw NetshotException.invalidParameter(parameter,
			"Invalid value '%s' for parameter '%s': true or false is expected".formatted(text, parameter));
	}

	private static Instant parseDate(String parameter, String text) {
		try {
			if (text.length() == 10) {
				return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant();
			}
			return OffsetDateTime.parse(text).toInstant();
		}
		catch (DateTimeParseException e) {
			throw NetshotException.invalidParameter(parameter,
				"Invalid value '%s' for parameter '%s': an ISO-8601 date (2026-10-30) or date and time with time zone (2026-10-30T10:00:00Z) is expected"
					.formatted(text, parameter));
		}
	}

	/**
	 * Reads an IP address literal, without any name resolution.
	 *
	 * @param parameter the name of the parameter (for the error message)
	 * @param text the address
	 * @return the address
	 */
	private static InetAddress parseAddress(String parameter, String text) {
		NetshotException error = NetshotException.invalidParameter(parameter,
			"Invalid value '%s' for parameter '%s': an IP address is expected".formatted(text, parameter));
		if (!IPV4.matcher(text).matches() && !IPV6.matcher(text).matches()) {
			throw error;
		}
		try {
			// A literal (checked above): no name resolution
			return InetAddress.getByName(text);
		}
		catch (UnknownHostException e) {
			throw error;
		}
	}

	/**
	 * Reads a subnet ({@code address/length}).
	 *
	 * @param parameter the name of the parameter (for the error message)
	 * @param text the subnet
	 * @return the subnet, normalized ({@code address/length})
	 */
	private static String parseSubnet(String parameter, String text) {
		NetshotException error = NetshotException.invalidParameter(parameter,
			"Invalid value '%s' for parameter '%s': a subnet (such as 10.0.0.0/8) is expected".formatted(text, parameter));
		int slash = text.indexOf('/');
		if (slash < 0) {
			throw error;
		}
		InetAddress address = parseAddress(parameter, text.substring(0, slash));
		int length;
		try {
			length = Integer.parseInt(text.substring(slash + 1));
		}
		catch (NumberFormatException e) {
			throw error;
		}
		if (length < 0 || length > address.getAddress().length * 8) {
			throw error;
		}
		return address.getHostAddress() + "/" + length;
	}
}
