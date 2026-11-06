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
package net.netshot.netshot.platform.database;

import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.SelectJoinStep;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.FieldSpec;
import net.netshot.netshot.platform.query.FieldType;
import net.netshot.netshot.platform.query.Interval;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.Lookup;
import net.netshot.netshot.platform.query.QuerySchema;
import net.netshot.netshot.platform.query.SortOrder;
import net.netshot.netshot.platform.query.StatsQuery;
import net.netshot.netshot.platform.query.StatsRow;

/**
 * The translation of the list queries ({@link ListQuery}) of one kind of object into jOOQ conditions and
 * orders. A repository declares it once (a static field), with the columns (or custom conditions) behind
 * the field names of the API representation; the service publishes its {@link #schema()}.
 *
 * <pre>{@code
 * static final QueryMapping LIST = QueryMapping.builder(DOMAIN.ID)
 *     .string("name", DOMAIN.NAME, Option.SORTABLE, Option.SEARCHABLE)
 *     .inet("ipAddress", DOMAIN.IPV4_ADDRESS)
 *     .defaultSort(SortOrder.asc("name"))
 *     .build();
 * }</pre>
 *
 * <p>The ID is always a field ({@code id}, filterable and sortable), and always ends the order, so that the
 * pages are defined. A list of items without ID (a compliance result: a device and a rule) is keyed by several
 * columns instead ({@link #keyedBy}): it has no {@code id} field, and its order ends with its key.</p>
 */
public final class QueryMapping {

	/** Options of a field. */
	public enum Option {
		/** The result can be sorted by the field. */
		SORTABLE,
		/** The free text search looks into the field (text fields only). */
		SEARCHABLE,
		/** The objects can be counted by the values of the field (statistics). */
		GROUPABLE,
	}

	/**
	 * Joins the tables a grouping needs (the tags of a device...) to the query of the statistics.
	 */
	@FunctionalInterface
	public interface Join {
		/**
		 * Adds the join.
		 *
		 * @param from the query, from the main table
		 * @return the query with the join
		 */
		SelectJoinStep<Record> apply(SelectJoinStep<Record> from);
	}


	/**
	 * Builds the condition of a custom filter (one which isn't a simple comparison of a column).
	 */
	@FunctionalInterface
	public interface CustomFilter {
		/**
		 * Builds the condition matching any of the values (the negated lookups are applied by the caller).
		 *
		 * @param lookup the (positive) lookup
		 * @param values the values, as read by the type of the field
		 * @return the condition
		 */
		Condition matchAny(Lookup lookup, List<Object> values);
	}

	/**
	 * A mapped field.
	 *
	 * @param spec the neutral description of the field
	 * @param column the column (null for a custom filter)
	 * @param custom the custom filter (null for a column)
	 * @param groupExpression the expression the statistics count by (null if the field can't be grouped by)
	 * @param join the join the grouping needs, or null
	 */
	private record Mapped(FieldSpec spec, Field<?> column, CustomFilter custom, Field<?> groupExpression, Join join) {
	}

	/** The columns which identify an item (the ID column, or the columns of a composite key). */
	private final List<Field<?>> keyColumns;

	/** The fields, by name. */
	private final Map<String, Mapped> fields;

	/** The neutral schema. */
	private final QuerySchema schema;

	private QueryMapping(List<Field<?>> keyColumns, Map<String, Mapped> fields, List<SortOrder> defaultSort) {
		this.keyColumns = List.copyOf(keyColumns);
		this.fields = Map.copyOf(fields);
		Map<String, FieldSpec> specs = new LinkedHashMap<>();
		fields.forEach((name, mapped) -> specs.put(name, mapped.spec()));
		this.schema = new QuerySchema(specs, defaultSort);
	}

	/**
	 * Starts a mapping.
	 *
	 * @param idColumn the ID column (field {@code id})
	 * @return the builder
	 */
	public static Builder builder(Field<Long> idColumn) {
		return new Builder(List.of(idColumn)).column("id", FieldType.INTEGER, idColumn, List.of(), Option.SORTABLE);
	}

	/**
	 * Starts a mapping of items without ID, identified by several columns: there is no {@code id} field, and the
	 * order ends with these columns.
	 *
	 * @param keyColumns the columns which identify an item, in the order which ends the sort
	 * @return the builder
	 */
	public static Builder keyedBy(Field<?>... keyColumns) {
		return new Builder(List.of(keyColumns));
	}

	/**
	 * Gets the neutral description of the fields.
	 *
	 * @return the schema
	 */
	public QuerySchema schema() {
		return this.schema;
	}

	/**
	 * Builds the condition of the filters and of the free text search of a query.
	 *
	 * @param query the query
	 * @return the condition (true if there is no filter)
	 */
	public Condition condition(ListQuery query) {
		List<Condition> conditions = new ArrayList<>();
		for (Criterion criterion : query.criteria()) {
			conditions.add(this.condition(criterion));
		}
		if (query.search() != null) {
			List<Condition> any = new ArrayList<>();
			for (Mapped mapped : this.fields.values()) {
				if (mapped.spec().searchable() && mapped.column() != null) {
					any.add(text(mapped.column()).containsIgnoreCase(query.search()));
				}
			}
			conditions.add(any.isEmpty() ? DSL.falseCondition() : DSL.or(any));
		}
		return DSL.and(conditions);
	}

	/**
	 * Builds the order of a query: the order of the query (or the default one), ended by the ID (or by the columns of
	 * the key which are not sorted on yet), so that the pages are defined. The empty values
	 * (null) come last in both directions (PostgreSQL puts them first in a descending order).
	 *
	 * @param query the query
	 * @return the sort fields
	 */
	public List<SortField<?>> orderBy(ListQuery query) {
		List<SortOrder> orders = query.sort().isEmpty() ? this.schema.defaultSort() : query.sort();
		List<SortField<?>> sortFields = new ArrayList<>();
		Set<Field<?>> sorted = new HashSet<>();
		for (SortOrder order : orders) {
			Mapped mapped = this.fields.get(order.field());
			if (mapped == null || mapped.column() == null || !mapped.spec().sortable()) {
				throw new IllegalArgumentException("Can't sort by " + order.field());
			}
			if (!order.descending()) {
				sortFields.add(mapped.column().asc());
			}
			else if (mapped.column().getDataType().nullable()) {
				sortFields.add(mapped.column().desc().nullsLast());
			}
			else {
				// A non null column keeps the plain order (which its index gives)
				sortFields.add(mapped.column().desc());
			}
			sorted.add(mapped.column());
		}
		for (Field<?> key : this.keyColumns) {
			if (!sorted.contains(key)) {
				sortFields.add(key.asc());
			}
		}
		return sortFields;
	}

	/**
	 * Counts the objects matching a list query, by the values of fields and/or by time buckets
	 * ({@link StatsQuery}). With time buckets, every bucket of the range is in the result, with a count of 0 if no
	 * object falls in it, for each combination of the grouped values found.
	 *
	 * @param dsl the jOOQ context
	 * @param table the main table
	 * @param query the filters (its window and order are ignored)
	 * @param stats what to count by
	 * @param extra an additional condition (the device search query...), or null
	 * @return the rows: by combination of values then by time with time buckets, by decreasing count otherwise
	 * @throws IllegalArgumentException if a field can't be grouped by, or the field of the buckets isn't a date
	 */
	@SuppressWarnings("unchecked")
	public List<StatsRow> stats(DSLContext dsl, Table<?> table, ListQuery query, StatsQuery stats, Condition extra) {
		List<Field<?>> keys = new ArrayList<>();
		List<Join> joins = new ArrayList<>();
		for (String name : stats.groupBy()) {
			Mapped mapped = this.fields.get(name);
			if (mapped == null || mapped.groupExpression() == null) {
				throw new IllegalArgumentException("Can't group by " + name);
			}
			keys.add(mapped.groupExpression().as("k" + keys.size()));
			if (mapped.join() != null) {
				joins.add(mapped.join());
			}
		}
		Condition condition = this.condition(query).and(extra == null ? DSL.noCondition() : extra);
		Field<Integer> bucket = null;
		List<Instant> starts = null;
		if (stats.interval() != null) {
			Mapped on = this.fields.get(stats.on());
			if (on == null || on.column() == null || on.spec().type() != FieldType.DATE) {
				throw new IllegalArgumentException("Can't count by time on " + stats.on());
			}
			Field<Instant> column = (Field<Instant>) on.column();
			starts = stats.interval().starts(stats.from(), stats.to(), stats.timeZone(), Interval.MAX_BUCKETS);
			if (starts == null) {
				throw new IllegalArgumentException("Too many time buckets");
			}
			// The bucket boundaries are computed once, in Java (time zone rules, daylight saving changes), and each row
			// is placed in them by the database (1 for the first bucket): truncating the local time in SQL would merge
			// the two hours of the same local time at the end of the daylight saving time
			String boundaries = starts.stream().map(start -> LocalDateTime.ofInstant(start, ZoneOffset.UTC).toString())
				.collect(Collectors.joining(",", "{", "}"));
			bucket = DSL.field("width_bucket({0}, cast({1} as timestamp[]))", Integer.class, column, DSL.inline(boundaries));
			condition = condition.and(column.ge(stats.from())).and(column.lt(stats.to()));
		}
		List<SelectField<?>> select = new ArrayList<>(keys);
		Field<Integer> bucketAlias = bucket == null ? null : bucket.as("bucket");
		if (bucketAlias != null) {
			select.add(bucketAlias);
		}
		Field<Integer> count = DSL.count().as("n");
		select.add(count);
		SelectJoinStep<Record> from = dsl.select(select).from(table);
		for (Join join : joins) {
			from = join.apply(from);
		}
		List<Field<?>> groupFields = new ArrayList<>();
		for (int i = 0; i < keys.size(); i++) {
			groupFields.add(DSL.field(DSL.name("k" + i)));
		}
		if (bucket != null) {
			groupFields.add(DSL.field(DSL.name("bucket")));
		}
		List<StatsRow> rows = new ArrayList<>();
		for (Record r : from.where(condition).groupBy(groupFields).fetch()) {
			List<Object> values = new ArrayList<>();
			for (Field<?> key : keys) {
				values.add(r.get(key));
			}
			Instant start = null;
			if (bucketAlias != null) {
				int index = r.get(bucketAlias);
				if (index < 1 || index > starts.size()) {
					continue;
				}
				start = starts.get(index - 1);
			}
			rows.add(new StatsRow(values, start, r.get(count)));
		}
		if (starts == null) {
			rows.sort(Comparator.comparingLong(StatsRow::count).reversed());
			return rows;
		}
		return fillBuckets(rows, starts, stats.groupBy().isEmpty());
	}

	/**
	 * Completes time bucket rows: every bucket for every combination of values.
	 *
	 * @param rows the counted rows
	 * @param starts the starts of the buckets
	 * @param single true without grouped values (one series, even if empty)
	 * @return the complete rows, by combination of values then by time
	 */
	private static List<StatsRow> fillBuckets(List<StatsRow> rows, List<Instant> starts, boolean single) {
		Map<List<Object>, Map<Instant, Long>> series = new LinkedHashMap<>();
		if (single) {
			series.put(List.of(), new HashMap<>());
		}
		for (StatsRow row : rows) {
			series.computeIfAbsent(row.keys(), _ -> new HashMap<>()).merge(row.start(), row.count(), Long::sum);
		}
		List<List<Object>> keys = new ArrayList<>(series.keySet());
		keys.sort(Comparator.comparing(Object::toString));
		List<StatsRow> filled = new ArrayList<>();
		for (List<Object> key : keys) {
			Map<Instant, Long> counts = series.get(key);
			for (Instant start : starts) {
				filled.add(new StatsRow(key, start, counts.getOrDefault(start, 0L)));
			}
		}
		return filled;
	}

	/**
	 * Builds the condition of a filter.
	 *
	 * @param criterion the filter
	 * @return the condition
	 */
	private Condition condition(Criterion criterion) {
		Mapped mapped = this.fields.get(criterion.field());
		if (mapped == null || !mapped.spec().lookups().contains(criterion.lookup())) {
			throw new IllegalArgumentException("Can't filter on " + criterion.field() + " with " + criterion.lookup());
		}
		Lookup lookup = criterion.lookup();
		if (lookup == Lookup.EMPTY) {
			Condition empty = mapped.custom() != null ? mapped.custom().matchAny(lookup, List.of(true)) : isEmpty(mapped);
			// "true" and "false" given together match everything
			boolean wantEmpty = criterion.values().contains(Boolean.TRUE);
			boolean wantNotEmpty = criterion.values().contains(Boolean.FALSE);
			if (wantEmpty && wantNotEmpty) {
				return DSL.trueCondition();
			}
			return wantEmpty ? empty : DSL.not(empty);
		}
		Lookup positive = lookup.positive();
		Condition any = mapped.custom() != null
			? mapped.custom().matchAny(positive, criterion.values())
			: matchAny(mapped.column(), mapped.spec().type(), positive, criterion.values());
		if (!lookup.isNegated()) {
			return any;
		}
		if (mapped.column() != null) {
			return mapped.column().isNull().or(DSL.not(any));
		}
		return DSL.not(any);
	}

	private static Condition isEmpty(Mapped mapped) {
		Field<?> column = mapped.column();
		if (mapped.spec().type() == FieldType.STRING) {
			return column.isNull().or(text(column).eq(""));
		}
		return column.isNull();
	}

	/**
	 * Gets a column as text.
	 *
	 * @param column the column
	 * @return the column itself if it holds text, otherwise its cast to text
	 */
	@SuppressWarnings("unchecked")
	private static Field<String> text(Field<?> column) {
		return column.getType() == String.class ? (Field<String>) column : column.cast(SQLDataType.VARCHAR);
	}

	/**
	 * Builds the condition matching a column against any of the values with a positive lookup.
	 *
	 * @param column the column
	 * @param type the type of the field
	 * @param lookup the lookup (positive)
	 * @param values the values
	 * @return the condition
	 */
	@SuppressWarnings("unchecked")
	private static Condition matchAny(Field<?> column, FieldType type, Lookup lookup, List<Object> values) {
		Field<Object> field = (Field<Object>) column;
		if (lookup == Lookup.EXACT) {
			List<Field<Object>> bound = values.stream().<Field<Object>>map(v -> DSL.val(v, field)).toList();
			return bound.size() == 1 ? field.eq(bound.get(0)) : field.in(bound);
		}
		List<Condition> any = new ArrayList<>();
		for (Object value : values) {
			any.add(switch (lookup) {
				case IE -> text(field).equalIgnoreCase((String) value);
				case IC -> text(field).containsIgnoreCase((String) value);
				case ISW -> text(field).startsWithIgnoreCase((String) value);
				case IEW -> text(field).endsWithIgnoreCase((String) value);
				case REGEX -> DSL.condition("{0} ~ {1}", field, DSL.val((String) value));
				case IREGEX -> DSL.condition("{0} ~* {1}", field, DSL.val((String) value));
				case LT -> field.lt(DSL.val(value, field));
				case LTE -> field.le(DSL.val(value, field));
				case GT -> field.gt(DSL.val(value, field));
				case GTE -> field.ge(DSL.val(value, field));
				case NET -> DSL.condition("{0} <<= cast({1} as inet)", field, DSL.val((String) value));
				default -> throw new IllegalArgumentException("Unexpected lookup " + lookup + " for a " + type + " field");
			});
		}
		return DSL.or(any);
	}

	/**
	 * Builder of a mapping.
	 */
	public static final class Builder {

		private final List<Field<?>> keyColumns;
		private final Map<String, Mapped> fields = new LinkedHashMap<>();
		private List<SortOrder> defaultSort = List.of();

		private Builder(List<Field<?>> keyColumns) {
			this.keyColumns = keyColumns;
		}

		private Builder column(String name, FieldType type, Field<?> column, List<String> enumValues, Option... options) {
			Set<Option> opts = options.length == 0 ? EnumSet.noneOf(Option.class) : EnumSet.copyOf(Arrays.asList(options));
			if (opts.contains(Option.SEARCHABLE) && type != FieldType.STRING) {
				throw new IllegalArgumentException("Only a text field can be searchable: " + name);
			}
			if (opts.contains(Option.GROUPABLE) && type == FieldType.INET) {
				throw new IllegalArgumentException("An IP address field can't be grouped by: " + name);
			}
			FieldSpec spec = new FieldSpec(name, type, type.lookups(), enumValues,
				opts.contains(Option.SORTABLE), opts.contains(Option.SEARCHABLE), opts.contains(Option.GROUPABLE));
			if (this.fields.put(name, new Mapped(spec, column, null, opts.contains(Option.GROUPABLE) ? column : null, null)) != null) {
				throw new IllegalArgumentException("Duplicate field " + name);
			}
			return this;
		}

		/**
		 * Adds a text field.
		 *
		 * @param name the name of the field
		 * @param column the column
		 * @param options the options
		 * @return this builder
		 */
		public Builder string(String name, Field<String> column, Option... options) {
			return this.column(name, FieldType.STRING, column, List.of(), options);
		}

		/**
		 * Adds an integer field (number, or reference to another object by ID).
		 *
		 * @param name the name of the field
		 * @param column the column
		 * @param options the options
		 * @return this builder
		 */
		public Builder integer(String name, Field<? extends Number> column, Option... options) {
			return this.column(name, FieldType.INTEGER, column, List.of(), options);
		}

		/**
		 * Adds a boolean field.
		 *
		 * @param name the name of the field
		 * @param column the column
		 * @param options the options
		 * @return this builder
		 */
		public Builder bool(String name, Field<Boolean> column, Option... options) {
			return this.column(name, FieldType.BOOLEAN, column, List.of(), options);
		}

		/**
		 * Adds a date field.
		 *
		 * @param name the name of the field
		 * @param column the column
		 * @param options the options
		 * @return this builder
		 */
		public Builder date(String name, Field<Instant> column, Option... options) {
			return this.column(name, FieldType.DATE, column, List.of(), options);
		}

		/**
		 * Adds an IP address field.
		 *
		 * @param name the name of the field
		 * @param column the column ({@code inet})
		 * @param options the options
		 * @return this builder
		 */
		public Builder inet(String name, Field<InetAddress> column, Option... options) {
			return this.column(name, FieldType.INET, column, List.of(), options);
		}

		/**
		 * Adds an enumeration field (stored by name).
		 *
		 * @param name the name of the field
		 * @param column the column (holding the names of the constants)
		 * @param enumClass the enumeration
		 * @param options the options
		 * @return this builder
		 */
		public Builder enumeration(String name, Field<String> column, Class<? extends Enum<?>> enumClass, Option... options) {
			List<String> names = Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).toList();
			return this.column(name, FieldType.ENUM, column, names, options);
		}

		/**
		 * Adds an enumeration field, of given values.
		 *
		 * @param name the name of the field
		 * @param column the column (holding one of the values)
		 * @param values the possible values
		 * @param options the options
		 * @return this builder
		 */
		public Builder enumeration(String name, Field<String> column, Collection<String> values, Option... options) {
			return this.column(name, FieldType.ENUM, column, values.stream().sorted().toList(), options);
		}

		/**
		 * Adds a custom filter, which can't be sorted on.
		 *
		 * @param name the name of the field
		 * @param type the type of the values
		 * @param lookups the lookups the filter supports (the negated ones are built from the positive ones)
		 * @param filter the condition builder
		 * @return this builder
		 */
		public Builder custom(String name, FieldType type, Set<Lookup> lookups, CustomFilter filter) {
			FieldSpec spec = new FieldSpec(name, type, lookups, List.of(), false, false);
			if (this.fields.put(name, new Mapped(spec, null, filter, null, null)) != null) {
				throw new IllegalArgumentException("Duplicate field " + name);
			}
			return this;
		}

		/**
		 * Makes a custom filter a grouping too (the statistics count by it), with the expression giving its value, and
		 * the join this expression needs (a device counted once per tag, per group...).
		 *
		 * @param name the name of the (custom) field
		 * @param expression the expression of the value
		 * @param join the join the expression needs, or null
		 * @return this builder
		 */
		public Builder grouping(String name, Field<?> expression, Join join) {
			Mapped mapped = this.fields.get(name);
			if (mapped == null) {
				throw new IllegalArgumentException("Unknown field " + name);
			}
			FieldSpec spec = mapped.spec();
			this.fields.put(name, new Mapped(new FieldSpec(spec.name(), spec.type(), spec.lookups(), spec.enumValues(),
				spec.sortable(), spec.searchable(), true), mapped.column(), mapped.custom(), expression, join));
			return this;
		}

		/**
		 * Sets the default order.
		 *
		 * @param orders the sort keys
		 * @return this builder
		 */
		public Builder defaultSort(SortOrder... orders) {
			this.defaultSort = List.of(orders);
			return this;
		}

		/**
		 * Builds the mapping.
		 *
		 * @return the mapping
		 */
		public QueryMapping build() {
			for (SortOrder order : this.defaultSort) {
				Mapped mapped = this.fields.get(order.field());
				if (mapped == null || !mapped.spec().sortable()) {
					throw new IllegalArgumentException("The default sort field isn't sortable: " + order.field());
				}
			}
			return new QueryMapping(this.keyColumns, this.fields, this.defaultSort);
		}
	}
}
