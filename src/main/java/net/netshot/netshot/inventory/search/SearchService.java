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
package net.netshot.netshot.inventory.search;

import org.jooq.exception.DataAccessException;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.search.Finder.FinderParseException;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.driver.DriverRegistry;

/**
 * The device search use case: finds the devices matching a string-based query (parsed by {@link Finder}).
 */
@Slf4j
public class SearchService {

	private final Transactions transactions;
	private final SearchRepository repository;

	/** The loaded device drivers, to parse the queries against. */
	private final DriverRegistry driverRegistry;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the search repository
	 * @param driverRegistry the loaded device drivers, to parse the queries against
	 */
	public SearchService(Transactions transactions, SearchRepository repository, DriverRegistry driverRegistry) {
		this.driverRegistry = driverRegistry;
		this.transactions = transactions;
		this.repository = repository;
	}

	/**
	 * Searches the devices.
	 *
	 * @param query the string-based query
	 * @param page the window on the result
	 * @return the matching devices, and the normalized query
	 * @throws NetshotException if the query is invalid, or the devices can't be read
	 */
	public SearchResult searchDevices(String query, Page page) {
		try {
			Finder finder = new Finder(query, this.driverRegistry);
			try {
				// Read-write session, as it has always been used for this query
				return this.transactions.write(dsl -> new SearchResult(
					finder.getFormattedQuery(), this.repository.findDevices(dsl, finder, page)));
			}
			catch (DataAccessException e) {
				log.error("Error while searching for the devices.", e);
				throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the devices");
			}
		}
		catch (FinderParseException e) {
			log.warn("User's query is invalid.", e);
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_SEARCH_STRING, "Invalid search string. " + e.getMessage());
		}
	}
}
