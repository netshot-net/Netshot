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

import java.util.List;

/**
 * A filter of a list query: the field, the lookup and the values (already read, see
 * {@link FieldType#parse}). A positive lookup matches any of the values, a negated one none of them.
 *
 * @param field the name of the field
 * @param lookup the lookup
 * @param values the values (at least one)
 */
public record Criterion(String field, Lookup lookup, List<Object> values) {

	/**
	 * Copies the values.
	 */
	public Criterion {
		values = List.copyOf(values);
		if (values.isEmpty()) {
			throw new IllegalArgumentException("A criterion needs at least one value");
		}
	}
}
