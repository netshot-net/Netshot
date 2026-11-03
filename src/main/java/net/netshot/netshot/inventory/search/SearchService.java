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

import org.jooq.Condition;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.search.Finder.FinderParseException;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.platform.query.StatsQuery;
import net.netshot.netshot.platform.query.StatsRow;
import net.netshot.netshot.platform.query.QuerySchema;
import net.netshot.netshot.driver.DriverRegistry;

/**
 * The device search use case: finds the devices matching a string-based query (parsed by {@link Finder}).
 */
@Slf4j
public class SearchService {

	private final Transactions transactions;
	private final SearchRepository repository;
	private final DeviceRepository deviceRepository;

	/** The loaded device drivers, to parse the queries against. */
	private final DriverRegistry driverRegistry;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the search repository
	 * @param deviceRepository the device repository
	 * @param driverRegistry the loaded device drivers, to parse the queries against
	 */
	public SearchService(Transactions transactions, SearchRepository repository, DeviceRepository deviceRepository,
		DriverRegistry driverRegistry) {
		this.driverRegistry = driverRegistry;
		this.transactions = transactions;
		this.repository = repository;
		this.deviceRepository = deviceRepository;
	}

	/**
	 * Gets what the device lists can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public static QuerySchema deviceListSchema() {
		return DeviceRepository.LIST.schema();
	}

	/**
	 * Parses a device search query.
	 *
	 * @param query the string-based query, or null
	 * @return the condition (true if there is no query)
	 * @throws NetshotException if the query is invalid
	 */
	private Condition searchCondition(String query) {
		if (query == null || query.isBlank()) {
			return DSL.trueCondition();
		}
		try {
			return new Finder(query, this.driverRegistry, this.transactions).getCondition();
		}
		catch (FinderParseException e) {
			log.warn("User's query is invalid.", e);
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_SEARCH_STRING, "Invalid search string. " + e.getMessage(), "query");
		}
	}

	/**
	 * Lists the summaries of the devices matching a list query and a search query.
	 *
	 * @param listQuery the list query (filters, order, window)
	 * @param searchQuery the string-based search query (see {@link Finder}), or null
	 * @return the device summaries of the window, with the total number if the list query asks for it
	 * @throws NetshotException if a filter or the search query is invalid, or the devices can't be read
	 */
	public ListResult<DeviceSummary> listDeviceSummaries(ListQuery listQuery, String searchQuery) {
		Condition condition = this.searchCondition(searchQuery);
		try {
			return this.transactions.read(dsl -> new ListResult<>(this.deviceRepository.findSummaries(dsl, listQuery, condition),
				listQuery.count() ? this.deviceRepository.count(dsl, listQuery, condition) : null));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the devices.", e);
			throw DatabaseErrors.listFailure(e, "Unable to fetch the devices");
		}
	}

	/**
	 * Lists the devices matching a list query and a search query, with all their details but their modules and
	 * network interfaces (not loaded: empty).
	 *
	 * @param listQuery the list query (filters, order, window)
	 * @param searchQuery the string-based search query (see {@link Finder}), or null
	 * @return the devices of the window, with the total number if the list query asks for it
	 * @throws NetshotException if a filter or the search query is invalid, or the devices can't be read
	 */
	public ListResult<Device> listDevices(ListQuery listQuery, String searchQuery) {
		Condition condition = this.searchCondition(searchQuery);
		try {
			return this.transactions.read(dsl -> new ListResult<>(this.deviceRepository.findAll(dsl, listQuery, condition),
				listQuery.count() ? this.deviceRepository.count(dsl, listQuery, condition) : null));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the devices.", e);
			throw DatabaseErrors.listFailure(e, "Unable to fetch the devices");
		}
	}

	/**
	 * Counts the devices matching a list query and a search query, by field values and/or time buckets.
	 *
	 * @param listQuery the filters
	 * @param stats what to count by
	 * @param searchQuery the search query (Netshot query language), or null
	 * @return the rows, with the number of matching devices
	 * @throws NetshotException if the search query or a filter is invalid, or the devices can't be read
	 */
	public ListResult<StatsRow> deviceStats(ListQuery listQuery, StatsQuery stats, String searchQuery) {
		Condition condition = this.searchCondition(searchQuery);
		try {
			return this.transactions.read(dsl -> new ListResult<>(this.deviceRepository.stats(dsl, listQuery, stats, condition),
				this.deviceRepository.count(dsl, listQuery, condition)));
		}
		catch (DataAccessException e) {
			log.error("Unable to count the devices.", e);
			throw DatabaseErrors.listFailure(e, "Unable to count the devices");
		}
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
			Finder finder = new Finder(query, this.driverRegistry, this.transactions);
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
