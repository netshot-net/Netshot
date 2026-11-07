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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.netshot.netshot.platform.error.NetshotException;

/**
 * What a list query can filter and sort on: the fields by name, and the default order. A list use case
 * publishes its schema, so that an API channel can read and check the filters it is given, and describe them.
 *
 * @param fields the fields, by name (in declaration order)
 * @param defaultSort the order of the result when the query gives none (the ID always ends the order)
 */
public record QuerySchema(Map<String, FieldSpec> fields, List<SortOrder> defaultSort) {

	/** The separator between the field and the lookup in a filter parameter ({@code name__ic}). */
	public static final String LOOKUP_SEPARATOR = "__";

	/**
	 * Copies the collections.
	 */
	public QuerySchema {
		fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
		defaultSort = List.copyOf(defaultSort);
	}

	/**
	 * Gets a field.
	 *
	 * @param name the name of the field
	 * @return the field, or null if there is no such field
	 */
	public FieldSpec field(String name) {
		return this.fields.get(name);
	}

	/**
	 * Reads a filter: a parameter made of a field name and an optional lookup suffix ({@code name},
	 * {@code name__ic}), and its values.
	 *
	 * @param parameter the parameter
	 * @param texts the values, as text (one per occurrence of the parameter)
	 * @return the criterion
	 * @throws NetshotException if the field is unknown, can't be filtered on, the lookup doesn't apply, or a value
	 *         is invalid
	 */
	public Criterion criterion(String parameter, Collection<String> texts) {
		String name = parameter;
		Lookup lookup = Lookup.EXACT;
		int separator = parameter.lastIndexOf(LOOKUP_SEPARATOR);
		if (separator > 0) {
			var suffixLookup = Lookup.ofSuffix(parameter.substring(separator + LOOKUP_SEPARATOR.length()));
			if (suffixLookup.isPresent()) {
				name = parameter.substring(0, separator);
				lookup = suffixLookup.get();
			}
		}
		FieldSpec field = this.fields.get(name);
		if (field == null || !field.filterable()) {
			throw NetshotException.invalidParameter(parameter, "Unknown filter parameter '%s'".formatted(parameter));
		}
		if (!field.lookups().contains(lookup)) {
			throw NetshotException.invalidParameter(parameter,
				"The lookup '%s' doesn't apply to the field '%s'".formatted(lookup.suffix(), name));
		}
		List<Object> values = new ArrayList<>();
		for (String text : texts) {
			values.add(field.type().parse(field, lookup, parameter, text));
		}
		if (values.isEmpty()) {
			throw NetshotException.invalidParameter(parameter, "No value for parameter '%s'".formatted(parameter));
		}
		return new Criterion(name, lookup, values);
	}

	/**
	 * Reads a sort specification: comma-separated field names, each prefixed with {@code -} for a descending
	 * order ({@code name,-changeDate}).
	 *
	 * @param parameter the name of the parameter (for the error messages)
	 * @param spec the specification
	 * @return the sort keys
	 * @throws NetshotException if a field is unknown or can't be sorted on
	 */
	public List<SortOrder> sort(String parameter, String spec) {
		List<SortOrder> orders = new ArrayList<>();
		for (String item : spec.split(",")) {
			String key = item.trim();
			boolean descending = key.startsWith("-");
			String name = descending ? key.substring(1) : key;
			FieldSpec field = this.fields.get(name);
			if (field == null || !field.sortable()) {
				throw NetshotException.invalidParameter(parameter, "Can't sort by '%s'".formatted(name));
			}
			orders.add(new SortOrder(name, descending));
		}
		return orders;
	}
}
