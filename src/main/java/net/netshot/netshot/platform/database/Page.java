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

import org.hibernate.query.Query;

import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * A window on the result of a list query: the number of items to skip and the maximum number
 * of items to return. A {@code null} component means no restriction.
 *
 * <p>Services take a {@code Page} where an API channel has pagination parameters; the channel
 * builds it from its own parameters (the REST one from {@code PaginationParams}).</p>
 *
 * @param offset the number of items to skip (not negative), or null
 * @param limit the maximum number of items to return (at least 1), or null
 */
public record Page(Integer offset, Integer limit) {

	/** No restriction: the whole result. */
	public static final Page ALL = new Page(null, null);

	/**
	 * Checks the window.
	 *
	 * @throws NetshotException if the offset is negative or the limit lower than 1
	 */
	public Page {
		if (offset != null && offset < 0) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, "Invalid offset parameter");
		}
		if (limit != null && limit < 1) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, "Invalid limit parameter");
		}
	}

	/**
	 * Restricts a query to this window.
	 *
	 * @param query the query being prepared
	 */
	public void apply(Query<?> query) {
		if (this.offset != null) {
			query.setFirstResult(this.offset);
		}
		if (this.limit != null) {
			query.setMaxResults(this.limit);
		}
	}
}
