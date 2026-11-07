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
package net.netshot.netshot.api.rest.v1;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.platform.query.StatsQuery;
import net.netshot.netshot.platform.query.StatsRow;

/**
 * The answer of a statistics request: the counts of the matching objects, by the values of the grouped fields
 * and/or by time bucket.
 *
 * @param count the number of matching objects (an object can be counted in several rows when grouped by a
 *        multivalued field, like the groups or the tags of a device)
 * @param groupBy the grouped fields
 * @param interval the size of the time buckets (null without time buckets)
 * @param on the date field of the time buckets
 * @param from the start of the range (included)
 * @param to the end of the range (excluded)
 * @param timeZone the time zone of the buckets
 * @param results the rows: {@code start} (with time buckets), the value of each grouped field (a reference for a
 *        reference field) and {@code count}
 */
@Schema(name = "Stats", description = "Counts of objects, by field values and/or by time bucket")
@JacksonXmlRootElement(localName = "stats")
public record StatsResponse(
	Long count,
	List<String> groupBy,
	String interval,
	String on,
	Instant from,
	Instant to,
	String timeZone,
	List<Map<String, Object>> results) {

	/**
	 * Resolves the IDs of a reference field into references.
	 */
	@FunctionalInterface
	public interface Resolver {
		/**
		 * Loads the referenced objects.
		 *
		 * @param ids the IDs
		 * @return the references by ID (a missing one is given by ID only)
		 */
		Map<Long, RefDto> resolve(Set<Long> ids);
	}

	/**
	 * Builds the answer.
	 *
	 * @param stats what was counted by
	 * @param rows the rows
	 * @param count the number of matching objects, or null
	 * @param resolvers the resolvers of the reference fields, by field name
	 * @return the answer
	 */
	public static StatsResponse of(StatsQuery stats, List<StatsRow> rows, Long count, Map<String, Resolver> resolvers) {
		List<String> groupBy = stats.groupBy();
		List<Function<Object, Object>> mappers = groupBy.stream().map(name -> {
			Resolver resolver = resolvers.get(name);
			if (resolver == null) {
				return Function.<Object>identity();
			}
			Set<Long> ids = new HashSet<>();
			int index = groupBy.indexOf(name);
			rows.forEach(r -> {
				if (r.keys().get(index) instanceof Number n) {
					ids.add(n.longValue());
				}
			});
			Map<Long, RefDto> refs = ids.isEmpty() ? new HashMap<>() : resolver.resolve(ids);
			return (Function<Object, Object>) v -> v instanceof Number n
				? refs.getOrDefault(n.longValue(), new RefDto(n.longValue(), null, null)) : v;
		}).toList();
		List<Map<String, Object>> results = rows.stream().map(r -> {
			Map<String, Object> row = new LinkedHashMap<>();
			if (r.start() != null) {
				row.put("start", r.start());
			}
			for (int i = 0; i < groupBy.size(); i++) {
				row.put(groupBy.get(i), mappers.get(i).apply(r.keys().get(i)));
			}
			row.put("count", r.count());
			return row;
		}).toList();
		return new StatsResponse(count, groupBy, stats.interval() == null ? null : stats.interval().name().toLowerCase(Locale.ROOT),
			stats.on(), stats.from(), stats.to(), stats.interval() == null ? null : stats.timeZone().getId(), results);
	}
}
