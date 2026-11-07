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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.query.InMemoryList.Column;
import net.netshot.netshot.testing.UnitTest;

/**
 * Tests for {@link InMemoryList}.
 */
@UnitTest
@DisplayName("In memory list queries")
public class InMemoryListTest {

	private record Item(String name, String color, Integer count) {
	}

	private static final InMemoryList<Item> LIST = new InMemoryList<>(List.of(SortOrder.asc("name")),
		Column.<Item>string("name", Item::name, true),
		Column.<Item>string("color", Item::color, true),
		Column.<Item>integer("count", Item::count));

	private static final List<Item> ITEMS = List.of(
		new Item("beta", "Red", 2),
		new Item("Alpha", "blue", 10),
		new Item("gamma", null, 2),
		new Item("delta", "RED", null));

	private static List<String> names(ListResult<Item> result) {
		return result.items().stream().map(Item::name).toList();
	}

	private static ListQuery query(List<Criterion> criteria, String search, List<SortOrder> sort, Page page) {
		return new ListQuery(criteria, search, sort, page, true);
	}

	@Test
	@DisplayName("The default order ignores the case, the count is the one before the window")
	void defaultOrder() {
		ListResult<Item> all = LIST.apply(ITEMS, ListQuery.ALL);
		Assertions.assertEquals(List.of("Alpha", "beta", "delta", "gamma"), names(all));
		Assertions.assertEquals(4L, all.count());
		ListResult<Item> window = LIST.apply(ITEMS, query(List.of(), null, List.of(), new Page(1, 2)));
		Assertions.assertEquals(List.of("beta", "delta"), names(window));
		Assertions.assertEquals(4L, window.count());
		Assertions.assertEquals(List.of(), names(LIST.apply(ITEMS, query(List.of(), null, List.of(), new Page(10, 2)))));
		Assertions.assertNull(LIST.apply(ITEMS, new ListQuery(List.of(), null, List.of(), Page.ALL, false)).count());
	}

	@Test
	@DisplayName("Sort: descending, nulls last in both directions, ties broken by the other fields")
	void sort() {
		Assertions.assertEquals(List.of("Alpha", "beta", "gamma", "delta"),
			names(LIST.apply(ITEMS, query(List.of(), null, List.of(SortOrder.desc("count")), Page.ALL))));
		Assertions.assertEquals(List.of("beta", "gamma", "Alpha", "delta"),
			names(LIST.apply(ITEMS, query(List.of(), null, List.of(SortOrder.asc("count")), Page.ALL))));
		Assertions.assertEquals(List.of("gamma", "delta", "beta", "Alpha"),
			names(LIST.apply(ITEMS, query(List.of(), null, List.of(SortOrder.desc("name")), Page.ALL))));
	}

	@Test
	@DisplayName("Filters: exact, case-insensitive, negated, numeric, null values")
	void filters() {
		Assertions.assertEquals(List.of("beta"), names(LIST.apply(ITEMS,
			query(List.of(new Criterion("color", Lookup.EXACT, List.of("Red"))), null, List.of(), Page.ALL))));
		Assertions.assertEquals(List.of("beta", "delta"), names(LIST.apply(ITEMS,
			query(List.of(new Criterion("color", Lookup.IE, List.of("red"))), null, List.of(), Page.ALL))));
		Assertions.assertEquals(List.of("Alpha", "gamma"), names(LIST.apply(ITEMS,
			query(List.of(new Criterion("color", Lookup.NIE, List.of("red"))), null, List.of(), Page.ALL))));
		Assertions.assertEquals(List.of("beta", "gamma"), names(LIST.apply(ITEMS,
			query(List.of(new Criterion("count", Lookup.EXACT, List.of(2L))), null, List.of(), Page.ALL))));
		Assertions.assertEquals(List.of("Alpha", "beta", "gamma"), names(LIST.apply(ITEMS,
			query(List.of(new Criterion("count", Lookup.GTE, List.of(2L))), null, List.of(), Page.ALL))));
		Assertions.assertEquals(List.of("Alpha", "delta"), names(LIST.apply(ITEMS,
			query(List.of(new Criterion("count", Lookup.N, List.of(2L))), null, List.of(), Page.ALL))));
		Assertions.assertEquals(List.of("Alpha", "gamma"), names(LIST.apply(ITEMS,
			query(List.of(new Criterion("name", Lookup.IEW, List.of("HA", "MA"))), null, List.of(), Page.ALL))));
		Assertions.assertEquals(List.of("beta"), names(LIST.apply(ITEMS, query(List.of(
			new Criterion("color", Lookup.IC, List.of("ed")), new Criterion("count", Lookup.LT, List.of(3L))), null, List.of(), Page.ALL))));
	}

	@Test
	@DisplayName("Search looks into the searchable text fields only")
	void search() {
		Assertions.assertEquals(List.of("beta", "delta"), names(LIST.apply(ITEMS, query(List.of(), "RED", List.of(), Page.ALL))));
		Assertions.assertEquals(List.of("Alpha"), names(LIST.apply(ITEMS, query(List.of(), "alp", List.of(), Page.ALL))));
		Assertions.assertEquals(List.of(), names(LIST.apply(ITEMS, query(List.of(), "10", List.of(), Page.ALL))));
	}

	@Test
	@DisplayName("The schema lists the fields, their lookups and the default order")
	void schema() {
		QuerySchema schema = LIST.schema();
		Assertions.assertEquals(List.of("name", "color", "count"), List.copyOf(schema.fields().keySet()));
		Assertions.assertTrue(schema.field("name").searchable());
		Assertions.assertFalse(schema.field("count").searchable());
		Assertions.assertTrue(schema.field("count").lookups().contains(Lookup.GT));
		Assertions.assertFalse(schema.field("name").lookups().contains(Lookup.REGEX));
		Assertions.assertEquals(List.of(SortOrder.asc("name")), schema.defaultSort());
	}
}
