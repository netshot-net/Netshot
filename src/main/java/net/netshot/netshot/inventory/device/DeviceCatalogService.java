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
package net.netshot.netshot.inventory.device;

import java.util.List;

import org.jooq.exception.DataAccessException;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The reference lists built from what the devices hold: the device families, the part numbers
 * of the modules, the tags and the software versions currently known.
 *
 * <p>The lists are not distinct: each one has an item per device (or module), and the clients
 * are the ones to remove duplicates.</p>
 */
@Slf4j
public class DeviceCatalogService {

	private final Transactions transactions;
	private final DeviceRepository repository;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the device repository
	 */
	public DeviceCatalogService(Transactions transactions, DeviceRepository repository) {
		this.transactions = transactions;
		this.repository = repository;
	}

	/**
	 * Lists the device families.
	 *
	 * @param page the window on the result
	 * @return the driver and family of each device
	 * @throws NetshotException if the families can't be read
	 */
	public List<DeviceFamily> getFamilies(Page page) {
		try {
			return this.transactions.readDsl(dsl -> this.repository.findFamilies(dsl, page));
		}
		catch (DataAccessException e) {
			log.error("Error while loading device families.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Database error");
		}
	}

	/**
	 * Lists the part numbers of the modules.
	 *
	 * @param page the window on the result
	 * @return the part number of each module
	 * @throws NetshotException if the part numbers can't be read
	 */
	public List<String> getPartNumbers(Page page) {
		try {
			return this.transactions.readDsl(dsl -> this.repository.findPartNumbers(dsl, page));
		}
		catch (DataAccessException e) {
			log.error("Error while loading part numbers.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Database error");
		}
	}

	/**
	 * Lists the tags attached to at least one device.
	 *
	 * @param page the window on the result
	 * @return the tags, sorted, with the number of devices having each
	 * @throws NetshotException if the tags can't be read
	 */
	public List<DeviceTagCount> getTags(Page page) {
		try {
			return this.transactions.readDsl(dsl -> this.repository.findTagCounts(dsl, page));
		}
		catch (DataAccessException e) {
			log.error("Error while loading device tags.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Database error");
		}
	}

	/**
	 * Lists the software versions of the devices.
	 *
	 * @param page the window on the result
	 * @return the software version of each device which has one
	 * @throws NetshotException if the software versions can't be read
	 */
	public List<String> getSoftwareVersions(Page page) {
		try {
			return this.transactions.readDsl(dsl -> this.repository.findSoftwareVersions(dsl, page));
		}
		catch (DataAccessException e) {
			log.error("Error while loading software versions.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Database error");
		}
	}
}
