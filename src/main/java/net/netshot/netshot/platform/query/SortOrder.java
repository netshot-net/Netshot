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

/**
 * A sort key of a list query.
 *
 * @param field the name of the field
 * @param descending whether the order is descending
 */
public record SortOrder(String field, boolean descending) {

	/**
	 * Builds an ascending sort key.
	 *
	 * @param field the name of the field
	 * @return the sort key
	 */
	public static SortOrder asc(String field) {
		return new SortOrder(field, false);
	}

	/**
	 * Builds a descending sort key.
	 *
	 * @param field the name of the field
	 * @return the sort key
	 */
	public static SortOrder desc(String field) {
		return new SortOrder(field, true);
	}

	@Override
	public String toString() {
		return (this.descending ? "-" : "") + this.field;
	}
}
