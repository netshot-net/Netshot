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

import java.util.Arrays;
import java.util.Optional;

/**
 * The way a filter compares the value of a field with the given values. The names are the suffixes of the
 * filter parameters ({@code name__ic=core}), as in NetBox; the exact match has no suffix.
 *
 * <p>A positive lookup given several values matches any of them; a negated one matches none of them (a
 * {@code null} value of the field is never equal, so it passes a negated lookup).</p>
 */
public enum Lookup {

	/** Equal to (case-sensitive for the strings). */
	EXACT("", false, "equal to"),
	/** Not equal to. */
	N("n", true, "not equal to"),
	/** Equal to, ignoring the case. */
	IE("ie", false, "equal to, ignoring the case"),
	/** Not equal to, ignoring the case. */
	NIE("nie", true, "not equal to, ignoring the case"),
	/** Contains, ignoring the case. */
	IC("ic", false, "contains, ignoring the case"),
	/** Doesn't contain, ignoring the case. */
	NIC("nic", true, "doesn't contain, ignoring the case"),
	/** Starts with, ignoring the case. */
	ISW("isw", false, "starts with, ignoring the case"),
	/** Doesn't start with, ignoring the case. */
	NISW("nisw", true, "doesn't start with, ignoring the case"),
	/** Ends with, ignoring the case. */
	IEW("iew", false, "ends with, ignoring the case"),
	/** Doesn't end with, ignoring the case. */
	NIEW("niew", true, "doesn't end with, ignoring the case"),
	/** Matches the (POSIX) regular expression. */
	REGEX("regex", false, "matches the (POSIX) regular expression"),
	/** Matches the (POSIX) regular expression, ignoring the case. */
	IREGEX("iregex", false, "matches the (POSIX) regular expression, ignoring the case"),
	/** Lower than. */
	LT("lt", false, "lower than"),
	/** Lower than or equal to. */
	LTE("lte", false, "lower than or equal to"),
	/** Greater than. */
	GT("gt", false, "greater than"),
	/** Greater than or equal to. */
	GTE("gte", false, "greater than or equal to"),
	/** Is empty (null, or an empty string) for {@code true}, isn't for {@code false}. */
	EMPTY("empty", false, "is empty (null, or an empty string) for true, isn't for false"),
	/** IP address within the given subnet (e.g. {@code 10.0.0.0/8}). */
	NET("net", false, "IP address within the given subnet (10.0.0.0/8)");

	/** The suffix of the filter parameter (after {@code __}). */
	private final String suffix;

	/** Whether the lookup is the negation of another one. */
	private final boolean negated;

	/** What the lookup matches, in words (for the API documentation). */
	private final String description;

	Lookup(String suffix, boolean negated, String description) {
		this.suffix = suffix;
		this.negated = negated;
		this.description = description;
	}

	/**
	 * Gets the suffix of the filter parameter.
	 *
	 * @return the suffix (empty for the exact match)
	 */
	public String suffix() {
		return this.suffix;
	}

	/**
	 * Tells what the lookup matches, in words.
	 *
	 * @return the description ("contains, ignoring the case")
	 */
	public String description() {
		return this.description;
	}

	/**
	 * Tells whether the lookup is the negation of another one.
	 *
	 * @return true for the negated lookups ({@code n}, {@code nie}, {@code nic}...)
	 */
	public boolean isNegated() {
		return this.negated;
	}

	/**
	 * Gets the lookup this one is the negation of.
	 *
	 * @return the positive lookup ({@code IC} for {@code NIC}), or this lookup if it isn't negated
	 */
	public Lookup positive() {
		return switch (this) {
			case N -> EXACT;
			case NIE -> IE;
			case NIC -> IC;
			case NISW -> ISW;
			case NIEW -> IEW;
			default -> this;
		};
	}

	/**
	 * Finds the lookup of a suffix.
	 *
	 * @param suffix the suffix (without the {@code __})
	 * @return the lookup, if the suffix is a known one
	 */
	public static Optional<Lookup> ofSuffix(String suffix) {
		return Arrays.stream(values()).filter(l -> l != EXACT && l.suffix.equals(suffix)).findFirst();
	}
}
