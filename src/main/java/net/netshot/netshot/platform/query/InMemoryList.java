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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Answers a list query ({@link ListQuery}) on values which are held in memory: the aggregates (distinct values
 * with counts) and the other small reference lists which are not rows of a table, so that they have no
 * {@link net.netshot.netshot.platform.database.QueryMapping}. The fields are declared once as {@link Column}s;
 * they give the {@link #schema()} and the way to filter, search and sort the values.
 *
 * <p>Only the text and integer fields are supported (the text ones are compared as text, the integer ones as
 * {@code Long}), with the lookups which need neither a regular expression nor a database function.</p>
 *
 * @param <T> the type of the values
 */
public final class InMemoryList<T> {

	/**
	 * A field of the values.
	 *
	 * @param <T> the type of the values
	 * @param name the name of the field
	 * @param type the type of the field ({@link FieldType#STRING} or {@link FieldType#INTEGER})
	 * @param getter reads the field of a value (a {@code String} or a {@code Number}, null if there is none)
	 * @param searchable whether the free text search looks into the field (text fields only)
	 */
	public record Column<T>(String name, FieldType type, Function<T, Object> getter, boolean searchable) {

		/**
		 * Declares a text field.
		 *
		 * @param <T> the type of the values
		 * @param name the name of the field
		 * @param getter reads the field
		 * @param searchable whether the free text search looks into the field
		 * @return the column
		 */
		public static <T> Column<T> string(String name, Function<T, String> getter, boolean searchable) {
			return new Column<>(name, FieldType.STRING, getter::apply, searchable);
		}

		/**
		 * Declares an integer field.
		 *
		 * @param <T> the type of the values
		 * @param name the name of the field
		 * @param getter reads the field
		 * @return the column
		 */
		public static <T> Column<T> integer(String name, Function<T, ? extends Number> getter) {
			return new Column<>(name, FieldType.INTEGER, getter::apply, false);
		}
	}

	private static final Set<Lookup> STRING_LOOKUPS = EnumSet.of(Lookup.EXACT, Lookup.N, Lookup.IE, Lookup.NIE, Lookup.IC,
		Lookup.NIC, Lookup.ISW, Lookup.NISW, Lookup.IEW, Lookup.NIEW);

	private static final Set<Lookup> INTEGER_LOOKUPS = EnumSet.of(Lookup.EXACT, Lookup.N, Lookup.LT, Lookup.LTE, Lookup.GT, Lookup.GTE);

	private final Map<String, Column<T>> columns = new LinkedHashMap<>();

	private final QuerySchema schema;

	/**
	 * Declares the fields.
	 *
	 * @param defaultSort the default order
	 * @param columns the fields (all sortable and filterable)
	 */
	@SafeVarargs
	public InMemoryList(List<SortOrder> defaultSort, Column<T>... columns) {
		Map<String, FieldSpec> specs = new LinkedHashMap<>();
		for (Column<T> column : columns) {
			if (column.type() != FieldType.STRING && column.type() != FieldType.INTEGER) {
				throw new IllegalArgumentException("Unsupported type of field " + column.name());
			}
			this.columns.put(column.name(), column);
			specs.put(column.name(), new FieldSpec(column.name(), column.type(),
				column.type() == FieldType.STRING ? STRING_LOOKUPS : INTEGER_LOOKUPS, List.of(), true, column.searchable()));
		}
		this.schema = new QuerySchema(specs, defaultSort);
	}

	/**
	 * Gets the description of the fields.
	 *
	 * @return the schema
	 */
	public QuerySchema schema() {
		return this.schema;
	}

	/**
	 * Filters, searches, sorts and cuts the values.
	 *
	 * @param values the values
	 * @param query the query (as read against the {@link #schema()})
	 * @return the window of the query, with the total number of matching values if the query asks for it
	 */
	public ListResult<T> apply(Collection<T> values, ListQuery query) {
		List<T> matching = new ArrayList<>();
		for (T value : values) {
			if (this.matches(value, query)) {
				matching.add(value);
			}
		}
		matching.sort(this.comparator(query));
		int offset = query.page().offset() == null ? 0 : query.page().offset();
		int end = query.page().limit() == null ? matching.size() : (int) Math.min((long) offset + query.page().limit(), matching.size());
		List<T> window = offset >= end ? List.of() : matching.subList(offset, end);
		return new ListResult<>(window, query.count() ? (long) matching.size() : null);
	}

	private boolean matches(T value, ListQuery query) {
		for (Criterion criterion : query.criteria()) {
			if (!this.matches(value, criterion)) {
				return false;
			}
		}
		if (query.search() == null) {
			return true;
		}
		String wanted = query.search().toLowerCase(Locale.ROOT);
		for (Column<T> column : this.columns.values()) {
			if (column.searchable() && column.getter().apply(value) instanceof String text
				&& text.toLowerCase(Locale.ROOT).contains(wanted)) {
				return true;
			}
		}
		return false;
	}

	private boolean matches(T value, Criterion criterion) {
		Object actual = this.columns.get(criterion.field()).getter().apply(value);
		Lookup positive = criterion.lookup().positive();
		boolean any = false;
		for (Object expected : criterion.values()) {
			if (actual != null && matches(actual, positive, expected)) {
				any = true;
				break;
			}
		}
		return criterion.lookup().isNegated() ? !any : any;
	}

	private static boolean matches(Object actual, Lookup lookup, Object expected) {
		if (actual instanceof String text) {
			String wanted = (String) expected;
			String lower = text.toLowerCase(Locale.ROOT);
			String lowerWanted = wanted.toLowerCase(Locale.ROOT);
			return switch (lookup) {
				case EXACT -> text.equals(wanted);
				case IE -> lower.equals(lowerWanted);
				case IC -> lower.contains(lowerWanted);
				case ISW -> lower.startsWith(lowerWanted);
				case IEW -> lower.endsWith(lowerWanted);
				default -> throw new IllegalArgumentException("Unexpected lookup " + lookup);
			};
		}
		long number = ((Number) actual).longValue();
		long wanted = (Long) expected;
		return switch (lookup) {
			case EXACT -> number == wanted;
			case LT -> number < wanted;
			case LTE -> number <= wanted;
			case GT -> number > wanted;
			case GTE -> number >= wanted;
			default -> throw new IllegalArgumentException("Unexpected lookup " + lookup);
		};
	}

	// The order: the keys of the query (or the default ones), then every field, so that the pages are defined; the
	// empty values come last in both directions, as in the database lists
	private Comparator<T> comparator(ListQuery query) {
		List<SortOrder> orders = query.sort().isEmpty() ? this.schema.defaultSort() : query.sort();
		List<String> names = new ArrayList<>(orders.stream().map(SortOrder::field).toList());
		for (String name : this.columns.keySet()) {
			if (!names.contains(name)) {
				names.add(name);
			}
		}
		Comparator<T> comparator = (_, _) -> 0;
		for (String name : names) {
			Column<T> column = this.columns.get(name);
			boolean descending = orders.stream().anyMatch(o -> o.field().equals(name) && o.descending());
			Comparator<Object> natural = column.type() == FieldType.STRING
				? (a, b) -> String.CASE_INSENSITIVE_ORDER.compare((String) a, (String) b)
				: (a, b) -> Long.compare(((Number) a).longValue(), ((Number) b).longValue());
			Comparator<T> next = Comparator.comparing(v -> column.getter().apply(v),
				Comparator.nullsLast(descending ? natural.reversed() : natural));
			comparator = comparator.thenComparing(next);
		}
		return comparator;
	}
}
