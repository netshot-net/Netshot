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
	EXACT("", false),
	/** Not equal to. */
	N("n", true),
	/** Equal to, ignoring the case. */
	IE("ie", false),
	/** Not equal to, ignoring the case. */
	NIE("nie", true),
	/** Contains, ignoring the case. */
	IC("ic", false),
	/** Doesn't contain, ignoring the case. */
	NIC("nic", true),
	/** Starts with, ignoring the case. */
	ISW("isw", false),
	/** Doesn't start with, ignoring the case. */
	NISW("nisw", true),
	/** Ends with, ignoring the case. */
	IEW("iew", false),
	/** Doesn't end with, ignoring the case. */
	NIEW("niew", true),
	/** Matches the (POSIX) regular expression. */
	REGEX("regex", false),
	/** Matches the (POSIX) regular expression, ignoring the case. */
	IREGEX("iregex", false),
	/** Lower than. */
	LT("lt", false),
	/** Lower than or equal to. */
	LTE("lte", false),
	/** Greater than. */
	GT("gt", false),
	/** Greater than or equal to. */
	GTE("gte", false),
	/** Is empty (null, or an empty string) for {@code true}, isn't for {@code false}. */
	EMPTY("empty", false),
	/** IP address within the given subnet (e.g. {@code 10.0.0.0/8}). */
	NET("net", false);

	/** The suffix of the filter parameter (after {@code __}). */
	private final String suffix;

	/** Whether the lookup is the negation of another one. */
	private final boolean negated;

	Lookup(String suffix, boolean negated) {
		this.suffix = suffix;
		this.negated = negated;
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
