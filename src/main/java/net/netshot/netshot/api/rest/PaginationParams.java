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
package net.netshot.netshot.api.rest;

import org.hibernate.query.Query;

import com.fasterxml.jackson.annotation.JsonView;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.ws.rs.QueryParam;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/** Pagination default query params. */
public class PaginationParams {
	/** Pagination offset. */
	@Parameter(description = "Pagination offset for the first item to return")
	@QueryParam("offset")
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private Integer offset;

	/** Pagination limit. */
	@Parameter(description = "Maximum number of items to return")
	@QueryParam("limit")
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private Integer limit;

	/**
	 * Gets the pagination parameters as the window a service takes.
	 * @return the page
	 * @throws net.netshot.netshot.platform.error.NetshotException if a parameter is invalid
	 */
	public Page toPage() {
		return new Page(this.offset, this.limit);
	}

	/**
	 * Apply the pagination parameters to the HQL query.
	 * @param query The query being prepared
	 */
	public void apply(Query<?> query) {
		if (this.offset != null) {
			if (this.offset < 0) {
				throw new NetshotBadRequestException("Invalid offset parameter",
					ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER);
			}
			query.setFirstResult(this.offset);
		}
		if (this.limit != null) {
			if (this.limit < 1) {
				throw new NetshotBadRequestException("Invalid limit parameter",
					ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER);
			}
			query.setMaxResults(this.limit);
		}
	}
}
