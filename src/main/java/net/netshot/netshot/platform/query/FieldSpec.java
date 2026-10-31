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

import java.util.List;
import java.util.Set;

/**
 * A field of a list query: what can be filtered or sorted on, by name.
 *
 * @param name the name of the field (as in the API representation)
 * @param type the type of the field
 * @param lookups the lookups which can be used to filter on the field (empty if it can't be filtered on)
 * @param enumValues the possible values, for an {@link FieldType#ENUM} field (empty otherwise)
 * @param sortable whether the result can be sorted by the field
 * @param searchable whether the free text search looks into the field
 */
public record FieldSpec(String name, FieldType type, Set<Lookup> lookups, List<String> enumValues,
	boolean sortable, boolean searchable) {

	/**
	 * Copies the collections.
	 */
	public FieldSpec {
		lookups = Set.copyOf(lookups);
		enumValues = List.copyOf(enumValues);
	}

	/**
	 * Tells whether the field can be filtered on.
	 *
	 * @return true if at least one lookup applies
	 */
	public boolean filterable() {
		return !this.lookups.isEmpty();
	}
}
