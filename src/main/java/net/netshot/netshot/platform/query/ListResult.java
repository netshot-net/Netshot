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
import java.util.function.Function;

/**
 * The result of a list query.
 *
 * @param <T> the type of the items
 * @param items the items of the requested window
 * @param count the total number of matching items, or null if it wasn't asked for
 */
public record ListResult<T>(List<T> items, Long count) {

	/**
	 * Copies the items.
	 */
	public ListResult {
		items = List.copyOf(items);
	}

	/**
	 * Maps the items.
	 *
	 * @param <U> the type of the mapped items
	 * @param mapper the item mapper
	 * @return the mapped result
	 */
	public <U> ListResult<U> map(Function<? super T, ? extends U> mapper) {
		return new ListResult<>(this.items.stream().<U>map(mapper).toList(), this.count);
	}
}
