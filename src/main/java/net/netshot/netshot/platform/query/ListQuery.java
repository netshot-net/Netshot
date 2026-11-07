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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.netshot.netshot.platform.database.Page;

/**
 * A list query: filters (all must match), free text search, order and window. It refers to the fields of
 * the {@link QuerySchema} of the list use case by name.
 *
 * @param criteria the filters, all of which must match
 * @param search the free text to look for in the searchable fields (case-insensitive), or null
 * @param sort the order (the default order of the schema if empty; the ID always ends the order)
 * @param page the window on the result
 * @param count whether the total number of matching items must be computed
 */
public record ListQuery(List<Criterion> criteria, String search, List<SortOrder> sort, Page page, boolean count) {

	/** Everything, in the default order, counted. */
	public static final ListQuery ALL = new ListQuery(List.of(), null, List.of(), Page.ALL, true);

	/**
	 * Copies the collections.
	 */
	public ListQuery {
		criteria = List.copyOf(criteria);
		sort = List.copyOf(sort);
		if (page == null) {
			page = Page.ALL;
		}
		if (search != null && search.isBlank()) {
			search = null;
		}
	}

	/**
	 * Builds the query of the objects of given IDs (to resolve references in one query), uncounted.
	 *
	 * @param ids the IDs
	 * @return the query
	 */
	public static ListQuery byIds(Collection<Long> ids) {
		return new ListQuery(List.of(new Criterion("id", Lookup.EXACT, List.copyOf(ids))), null, List.of(),
			new Page(0, Math.max(ids.size(), 1)), false);
	}

	/**
	 * Gets a copy of this query with one more filter.
	 *
	 * @param criterion the filter to add
	 * @return the new query
	 */
	public ListQuery and(Criterion criterion) {
		List<Criterion> all = new ArrayList<>(this.criteria);
		all.add(criterion);
		return new ListQuery(all, this.search, this.sort, this.page, this.count);
	}
}
