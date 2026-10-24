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

import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;

import java.util.List;

import org.jooq.DSLContext;

import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.platform.database.Page;

/**
 * The query running a device search. The {@link DSLContext} is always the first parameter: the
 * caller (the service) owns the transaction.
 */
public class SearchRepository {

	/**
	 * Finds the devices matching a parsed query, in their summary form.
	 *
	 * @param dsl the jOOQ context
	 * @param finder the parsed query
	 * @param page the window on the result
	 * @return the device summaries
	 */
	public List<DeviceSummary> findDevices(DSLContext dsl, Finder finder, Page page) {
		return page.apply(dsl.select(DeviceSummary.FIELDS).from(DEVICE).where(finder.getCondition()))
			.fetch(DeviceSummary::fromRecord);
	}
}
