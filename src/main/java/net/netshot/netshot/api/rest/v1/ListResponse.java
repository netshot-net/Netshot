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

import java.net.URI;
import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;

/**
 * A page of a list: every list of the v1 API is written in this envelope.
 *
 * @param <T> the type of the items
 * @param count the total number of matching items (null when the request disabled the count with {@code count=false})
 * @param offset the offset of the first item of the page
 * @param limit the maximum number of items of the page
 * @param next the path of the next page (null on the last page)
 * @param previous the path of the previous page (null on the first page)
 * @param results the items of the page
 */
public record ListResponse<T>(
	@Schema(description = "Total number of matching items (null if count=false)", nullable = true) Long count,
	@Schema(description = "Offset of the first item of the page") int offset,
	@Schema(description = "Maximum number of items of the page") int limit,
	@Schema(description = "Path of the next page", nullable = true) String next,
	@Schema(description = "Path of the previous page", nullable = true) String previous,
	@Schema(description = "The items of the page") List<T> results) {

	/**
	 * Builds the page of a list result.
	 *
	 * @param <S> the type of the result items
	 * @param <T> the type of the representation of the items
	 * @param uriInfo the request URI (to build the links to the next and previous pages)
	 * @param query the list query (with its window)
	 * @param result the result
	 * @param mapper the mapper from the result item to its representation
	 * @return the page
	 */
	public static <S, T> ListResponse<T> of(UriInfo uriInfo, ListQuery query, ListResult<S> result, Function<? super S, ? extends T> mapper) {
		Page page = query.page();
		int offset = page.offset() == null ? 0 : page.offset();
		int limit = page.limit() == null ? result.items().size() : page.limit();
		int size = result.items().size();
		boolean hasNext = result.count() != null ? offset + size < result.count() : size >= limit && size > 0;
		String next = hasNext ? link(uriInfo, offset + limit, limit) : null;
		String previous = offset > 0 ? link(uriInfo, Math.max(0, offset - limit), limit) : null;
		List<T> results = result.items().stream().<T>map(mapper).toList();
		return new ListResponse<>(result.count(), offset, limit, next, previous, results);
	}

	/**
	 * Builds the path and query of another page of the request.
	 *
	 * @param uriInfo the request URI
	 * @param offset the offset of the page
	 * @param limit the limit
	 * @return the path, e.g. {@code /api/v1/domains?offset=100&limit=100}
	 */
	private static String link(UriInfo uriInfo, int offset, int limit) {
		URI uri = uriInfo.getRequestUriBuilder()
			.replaceQueryParam(ListParams.OFFSET, offset)
			.replaceQueryParam(ListParams.LIMIT, limit)
			.build();
		return uri.getRawQuery() == null ? uri.getRawPath() : uri.getRawPath() + "?" + uri.getRawQuery();
	}
}
