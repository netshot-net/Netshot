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
package net.netshot.netshot.api.rest.v1;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.ws.rs.core.MultivaluedMap;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.FieldSpec;
import net.netshot.netshot.platform.query.FieldType;
import net.netshot.netshot.platform.query.Interval;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.QuerySchema;
import net.netshot.netshot.platform.query.StatsQuery;

/**
 * Reads the parameters of a statistics request ({@code GET /things/stats}): the filters of the list (as
 * {@link ListParams}), and {@code groupBy}, {@code interval}, {@code on}, {@code from}, {@code to},
 * {@code timeZone}.
 */
public final class StatsParams {

	/** The fields to count by. */
	public static final String GROUP_BY = "groupBy";

	/** The size of the time buckets. */
	public static final String INTERVAL = "interval";

	/** The date field of the time buckets. */
	public static final String ON = "on";

	/** The start of the range. */
	public static final String FROM = "from";

	/** The end of the range. */
	public static final String TO = "to";

	/** The time zone of the buckets. */
	public static final String TIME_ZONE = "timeZone";

	/** The maximum number of time buckets. */
	public static final int MAX_BUCKETS = Interval.MAX_BUCKETS;

	/** The default time zone of the buckets. */
	private static final ZoneId UTC = ZoneId.of("UTC");

	/** The maximum number of fields to count by. */
	private static final int MAX_GROUP_BY = 2;

	private static final List<String> STATS_PARAMETERS = List.of(GROUP_BY, INTERVAL, ON, FROM, TO, TIME_ZONE);

	/** The list parameters which don't apply to statistics. */
	private static final Set<String> REFUSED = Set.of(ListParams.OFFSET, ListParams.LIMIT, ListParams.SORT, ListParams.COUNT,
		Selection.VIEW, Selection.FIELDS, Selection.OMIT, Selection.EXPAND);

	/**
	 * A statistics request.
	 *
	 * @param query the filters
	 * @param stats what to count by
	 */
	public record Parsed(ListQuery query, StatsQuery stats) {
	}

	private StatsParams() {
	}

	/**
	 * Reads a statistics request.
	 *
	 * @param params the query parameters
	 * @param schema what the list can be filtered on and grouped by
	 * @param defaultOn the date field of the time buckets when {@code on} isn't given
	 * @param handled the other parameters the resource reads itself (not filters)
	 * @return the request
	 * @throws NetshotException if a parameter is unknown or invalid
	 */
	public static Parsed parse(MultivaluedMap<String, String> params, QuerySchema schema, String defaultOn, String... handled) {
		for (String name : params.keySet()) {
			if (REFUSED.contains(name)) {
				throw NetshotException.invalidParameter(name, "The parameter '%s' doesn't apply to statistics".formatted(name));
			}
		}
		List<String> skipped = new ArrayList<>(STATS_PARAMETERS);
		skipped.addAll(List.of(handled));
		ListQuery query = ListParams.parse(params, schema, skipped.toArray(String[]::new));
		List<String> groupBy = new ArrayList<>();
		for (String value : values(params, GROUP_BY)) {
			for (String name : value.split(",")) {
				String field = name.trim();
				if (field.isEmpty()) {
					continue;
				}
				FieldSpec spec = schema.field(field);
				if (spec == null || !spec.groupable()) {
					throw NetshotException.invalidParameter(GROUP_BY, "Can't group by '%s'".formatted(field));
				}
				if (groupBy.contains(field)) {
					throw NetshotException.invalidParameter(GROUP_BY, "'%s' is given twice".formatted(field));
				}
				groupBy.add(field);
			}
		}
		if (groupBy.size() > MAX_GROUP_BY) {
			throw NetshotException.invalidParameter(GROUP_BY, "At most %d fields can be grouped by".formatted(MAX_GROUP_BY));
		}
		String intervalText = single(params, INTERVAL);
		if (intervalText == null) {
			for (String name : List.of(ON, FROM, TO, TIME_ZONE)) {
				if (params.containsKey(name)) {
					throw NetshotException.invalidParameter(name, "The parameter '%s' needs an interval".formatted(name));
				}
			}
			return new Parsed(query, new StatsQuery(groupBy, null, null, null, null, UTC));
		}
		Interval interval;
		try {
			interval = Interval.valueOf(intervalText.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			throw NetshotException.invalidParameter(INTERVAL, "Invalid interval: hour, day, week or month is expected");
		}
		String on = single(params, ON);
		if (on == null) {
			on = defaultOn;
		}
		FieldSpec onSpec = schema.field(on);
		if (onSpec == null || onSpec.type() != FieldType.DATE) {
			throw NetshotException.invalidParameter(ON, "Invalid date field '%s'".formatted(on));
		}
		ZoneId zone = UTC;
		String zoneText = single(params, TIME_ZONE);
		if (zoneText != null) {
			try {
				zone = ZoneId.of(zoneText.trim());
			}
			catch (DateTimeException e) {
				throw NetshotException.invalidParameter(TIME_ZONE, "Invalid time zone: an IANA name (Europe/Paris) is expected");
			}
		}
		Instant to = date(params, TO, zone);
		if (to == null) {
			to = Instant.now();
		}
		Instant from = date(params, FROM, zone);
		if (from == null) {
			// The current bucket and the previous ones: 24 hours, 7 days, 12 weeks or 12 months
			int count = switch (interval) {
				case HOUR -> 24;
				case DAY -> 7;
				case WEEK, MONTH -> 12;
			};
			ZonedDateTime start = interval.truncate(to.minusNanos(1), zone);
			for (int i = 1; i < count; i++) {
				start = interval.previous(start);
			}
			from = start.toInstant();
		}
		if (!from.isBefore(to)) {
			throw NetshotException.invalidParameter(FROM, "The start of the range must be before its end");
		}
		if (interval.starts(from, to, zone, MAX_BUCKETS) == null) {
			throw NetshotException.invalidParameter(INTERVAL, "Too many time buckets (more than %d): use a longer interval or a shorter range"
				.formatted(MAX_BUCKETS));
		}
		return new Parsed(query, new StatsQuery(groupBy, interval, on, from, to, zone));
	}

	private static List<String> values(MultivaluedMap<String, String> params, String name) {
		List<String> values = params.get(name);
		return values == null ? List.of() : values;
	}

	private static String single(MultivaluedMap<String, String> params, String name) {
		List<String> values = values(params, name);
		if (values.isEmpty()) {
			return null;
		}
		if (values.size() > 1) {
			throw NetshotException.invalidParameter(name, "The parameter '%s' can only be given once".formatted(name));
		}
		return values.get(0);
	}

	// A date, or a day (its start in the time zone)
	private static Instant date(MultivaluedMap<String, String> params, String name, ZoneId zone) {
		String text = single(params, name);
		if (text == null) {
			return null;
		}
		try {
			String trimmed = text.trim();
			if (trimmed.length() == 10) {
				return LocalDate.parse(trimmed).atStartOfDay(zone).toInstant();
			}
			return OffsetDateTime.parse(trimmed).toInstant();
		}
		catch (DateTimeParseException e) {
			throw NetshotException.invalidParameter(name, "Invalid date: ISO-8601 with an offset (or a day) is expected");
		}
	}
}
