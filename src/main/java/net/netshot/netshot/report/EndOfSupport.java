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
package net.netshot.netshot.report;

/**
 * The two hardware support milestones a device can reach.
 */
public enum EndOfSupport {

	/** End of sale. */
	SALE("eosDate"),

	/** End of life. */
	LIFE("eolDate");

	private final String property;

	EndOfSupport(String property) {
		this.property = property;
	}

	/**
	 * Gets the name of the {@code Device} property holding the date of the milestone.
	 *
	 * @return the property name
	 */
	public String property() {
		return this.property;
	}

	/**
	 * Finds a milestone by the code used by the API.
	 *
	 * @param code "eos" or "eol"
	 * @return the milestone, or null if the code is not valid
	 */
	public static EndOfSupport fromCode(String code) {
		if ("eos".equals(code)) {
			return SALE;
		}
		if ("eol".equals(code)) {
			return LIFE;
		}
		return null;
	}
}
