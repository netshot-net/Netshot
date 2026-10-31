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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.QuerySchema;
import net.netshot.netshot.platform.query.SortOrder;

/**
 * Reads the list query of a v1 list request: the window ({@code offset}, {@code limit}), the order
 * ({@code sort=name,-changeDate}), the free text search ({@code q}), whether to count ({@code count}), and the
 * filters (every other parameter, {@code <field>[__<lookup>]=<value>}, checked against the {@link QuerySchema}
 * of the list). The parameters of the field selection ({@link Selection}) are left to it. An unknown parameter
 * is refused.
 */
@Slf4j
public final class ListParams {

	/** Offset parameter. */
	public static final String OFFSET = "offset";

	/** Limit parameter. */
	public static final String LIMIT = "limit";

	/** Sort parameter. */
	public static final String SORT = "sort";

	/** Free text search parameter. */
	public static final String SEARCH = "q";

	/** Count parameter. */
	public static final String COUNT = "count";

	/** The parameters which are not filters. */
	private static final Set<String> RESERVED;

	static {
		Set<String> reserved = new HashSet<>(Selection.PARAMETERS);
		reserved.addAll(Set.of(OFFSET, LIMIT, SORT, SEARCH, COUNT));
		RESERVED = Set.copyOf(reserved);
	}

	/**
	 * The page sizes.
	 *
	 * @param defaultPageSize the number of items of a page when the request gives no limit
	 * @param maxPageSize the maximum limit, or null for no maximum
	 */
	public record Settings(int defaultPageSize, Integer maxPageSize) {

		/** The number of items of a page when the request gives no limit. */
		static final Setting<Integer> DEFAULT_PAGE_SIZE = Setting.integer("netshot.http.api.pagesize.default")
			.defaultValue(100).range(1, Integer.MAX_VALUE).live();

		/** The maximum number of items of a page (no maximum by default). */
		static final Setting<Integer> MAX_PAGE_SIZE = Setting.integer("netshot.http.api.pagesize.max")
			.range(1, Integer.MAX_VALUE).live();

		/**
		 * Reads the settings.
		 *
		 * @param config the configuration
		 * @return the settings
		 */
		static Settings from(Config config) {
			Integer max = config.get(MAX_PAGE_SIZE);
			int def = config.get(DEFAULT_PAGE_SIZE);
			if (max != null && def > max) {
				log.warn("The default page size of the API ({}) is above the maximum ({}), using the maximum.", def, max);
				def = max;
			}
			return new Settings(def, max);
		}
	}

	/** The settings, from the configuration. */
	private static final ConfigStore.Binding<Settings> SETTINGS = Netshot.config().bind(Settings::from);

	private ListParams() {
	}

	/**
	 * Gets the page size settings.
	 *
	 * @return the settings
	 */
	public static Settings settings() {
		return SETTINGS.get();
	}

	/**
	 * Reads the list query of a request.
	 *
	 * @param params the query parameters
	 * @param schema what the list can be filtered and sorted on
	 * @param handled the other parameters the resource reads itself (not filters)
	 * @return the list query
	 * @throws NetshotException if a parameter is unknown or invalid
	 */
	public static ListQuery parse(MultivaluedMap<String, String> params, QuerySchema schema, String... handled) {
		Settings settings = settings();
		Integer offset = integer(params, OFFSET, 0);
		if (offset == null) {
			offset = 0;
		}
		Integer limit = integer(params, LIMIT, 1);
		if (limit == null) {
			limit = settings.defaultPageSize();
		}
		else if (settings.maxPageSize() != null && limit > settings.maxPageSize()) {
			throw NetshotException.invalidParameter(LIMIT,
				"The limit can't be more than %d".formatted(settings.maxPageSize()));
		}
		String countText = single(params, COUNT);
		boolean count = true;
		if (countText != null) {
			if ("false".equalsIgnoreCase(countText.trim())) {
				count = false;
			}
			else if (!"true".equalsIgnoreCase(countText.trim())) {
				throw NetshotException.invalidParameter(COUNT, "Invalid count parameter: true or false is expected");
			}
		}
		String sortText = single(params, SORT);
		List<SortOrder> sort = sortText == null ? List.of() : schema.sort(SORT, sortText);
		String search = single(params, SEARCH);
		Set<String> skipped = Set.of(handled);
		List<Criterion> criteria = new ArrayList<>();
		for (Map.Entry<String, List<String>> param : params.entrySet()) {
			if (RESERVED.contains(param.getKey()) || skipped.contains(param.getKey())) {
				continue;
			}
			criteria.add(schema.criterion(param.getKey(), param.getValue()));
		}
		return new ListQuery(criteria, search, sort, new Page(offset, limit), count);
	}

	/**
	 * Checks that a request which isn't a list has no other query parameter than the selection ones
	 * ({@link Selection#PARAMETERS}) and the given ones.
	 *
	 * @param params the query parameters
	 * @param handled the other parameters the resource reads
	 * @throws NetshotException if there is another parameter
	 */
	public static void checkSelectionOnly(MultivaluedMap<String, String> params, String... handled) {
		Set<String> skipped = Set.of(handled);
		for (String name : params.keySet()) {
			if (!Selection.PARAMETERS.contains(name) && !skipped.contains(name)) {
				throw NetshotException.invalidParameter(name, "Unknown parameter '%s'".formatted(name));
			}
		}
	}

	private static String single(MultivaluedMap<String, String> params, String name) {
		List<String> values = params.get(name);
		if (values == null || values.isEmpty()) {
			return null;
		}
		if (values.size() > 1) {
			throw NetshotException.invalidParameter(name, "The parameter '%s' can only be given once".formatted(name));
		}
		return values.get(0);
	}

	private static Integer integer(MultivaluedMap<String, String> params, String name, int min) {
		String text = single(params, name);
		if (text == null) {
			return null;
		}
		try {
			int value = Integer.parseInt(text.trim());
			if (value < min) {
				throw NetshotException.invalidParameter(name, "The parameter '%s' must be at least %d".formatted(name, min));
			}
			return value;
		}
		catch (NumberFormatException e) {
			throw NetshotException.invalidParameter(name, "Invalid parameter '%s': an integer is expected".formatted(name));
		}
	}
}
