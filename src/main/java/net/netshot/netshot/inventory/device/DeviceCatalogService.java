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
import java.util.function.Function;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.InMemoryList;
import net.netshot.netshot.platform.query.InMemoryList.Column;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.platform.query.QuerySchema;
import net.netshot.netshot.platform.query.SortOrder;

/**
 * The reference lists built from what the devices hold: the device families, the part numbers
 * of the modules, the tags and the software versions currently known.
 *
 * <p>The lists of the {@code get...} methods are not distinct: each one has an item per device (or module), and
 * the clients are the ones to remove duplicates. The {@code list...} methods answer distinct values with their
 * number of devices (or modules), as list queries. This service also lists the interfaces and the modules of a
 * device.</p>
 */
@Slf4j
public class DeviceCatalogService {

	private static final InMemoryList<DeviceFamilyCount> FAMILIES = new InMemoryList<>(
		List.of(SortOrder.asc("driver"), SortOrder.asc("family")),
		Column.<DeviceFamilyCount>string("driver", DeviceFamilyCount::driver, true),
		Column.<DeviceFamilyCount>string("family", DeviceFamilyCount::family, true),
		Column.<DeviceFamilyCount>integer("deviceCount", DeviceFamilyCount::deviceCount));

	private static final InMemoryList<DeviceTagCount> TAGS = new InMemoryList<>(
		List.of(SortOrder.asc("name")),
		Column.<DeviceTagCount>string("name", DeviceTagCount::name, true),
		Column.<DeviceTagCount>integer("deviceCount", DeviceTagCount::deviceCount));

	private static final InMemoryList<PartNumberCount> PART_NUMBERS = new InMemoryList<>(
		List.of(SortOrder.asc("partNumber")),
		Column.<PartNumberCount>string("partNumber", PartNumberCount::partNumber, true),
		Column.<PartNumberCount>integer("moduleCount", PartNumberCount::moduleCount));

	private static final InMemoryList<SoftwareVersionCount> SOFTWARE_VERSIONS = new InMemoryList<>(
		List.of(SortOrder.asc("version")),
		Column.<SoftwareVersionCount>string("version", SoftwareVersionCount::version, true),
		Column.<SoftwareVersionCount>integer("deviceCount", SoftwareVersionCount::deviceCount));

	private final Transactions transactions;
	private final DeviceRepository repository;
	private final DeviceInventoryRepository inventoryRepository = new DeviceInventoryRepository();

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
			return this.transactions.read(dsl -> this.repository.findFamilies(dsl, page));
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
			return this.transactions.read(dsl -> this.repository.findPartNumbers(dsl, page));
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
			return this.transactions.read(dsl -> this.repository.findTagCounts(dsl, page));
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
			return this.transactions.read(dsl -> this.repository.findSoftwareVersions(dsl, page));
		}
		catch (DataAccessException e) {
			log.error("Error while loading software versions.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Database error");
		}
	}

	/**
	 * Tells what the list of the network interfaces of a device can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public QuerySchema interfaceSchema() {
		return DeviceInventoryRepository.INTERFACE_LIST.schema();
	}

	/**
	 * Lists the network interfaces of a device matching a query, with their IP addresses.
	 *
	 * @param deviceId the device ID
	 * @param query the query
	 * @return the interfaces of the window, with the total number of matching interfaces if the query asks for it
	 * @throws NetshotException if the device doesn't exist, a filter is invalid, or the interfaces can't be read
	 */
	public ListResult<NetworkInterface> listInterfaces(long deviceId, ListQuery query) {
		try {
			return this.transactions.read(dsl -> {
				this.checkDevice(dsl, deviceId);
				return new ListResult<>(this.inventoryRepository.findInterfaces(dsl, deviceId, query),
					query.count() ? this.inventoryRepository.countInterfaces(dsl, deviceId, query) : null);
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the interfaces.", e);
			throw DatabaseErrors.listFailure(e, "Unable to fetch the interfaces");
		}
	}

	/**
	 * Tells what the list of the modules of a device can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public QuerySchema moduleSchema() {
		return DeviceInventoryRepository.MODULE_LIST.schema();
	}

	/**
	 * Lists the modules of a device matching a query (the ones removed from the device included, unless filtered out).
	 *
	 * @param deviceId the device ID
	 * @param query the query
	 * @return the modules of the window, with the total number of matching modules if the query asks for it
	 * @throws NetshotException if the device doesn't exist, a filter is invalid, or the modules can't be read
	 */
	public ListResult<Module> listModules(long deviceId, ListQuery query) {
		try {
			return this.transactions.read(dsl -> {
				this.checkDevice(dsl, deviceId);
				return new ListResult<>(this.inventoryRepository.findModules(dsl, deviceId, query),
					query.count() ? this.inventoryRepository.countModules(dsl, deviceId, query) : null);
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the modules.", e);
			throw DatabaseErrors.listFailure(e, "Unable to fetch the modules");
		}
	}

	/**
	 * Checks that a device exists.
	 *
	 * @param deviceId the device ID
	 * @throws NetshotException if the device doesn't exist, or can't be read
	 */
	public void checkDevice(long deviceId) {
		try {
			this.transactions.read(dsl -> {
				this.checkDevice(dsl, deviceId);
				return null;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the device.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the device");
		}
	}

	private void checkDevice(DSLContext dsl, long deviceId) {
		if (!this.inventoryRepository.deviceExists(dsl, deviceId)) {
			throw new NetshotException(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, "The device doesn't exist.");
		}
	}

	/**
	 * Tells what the list of the device families can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public QuerySchema familySchema() {
		return FAMILIES.schema();
	}

	/**
	 * Lists the distinct device families (driver and family) of the devices, with their number of devices.
	 *
	 * @param query the query
	 * @return the families of the window, with the total number of matching families if the query asks for it
	 * @throws NetshotException if the families can't be read
	 */
	public ListResult<DeviceFamilyCount> listFamilies(ListQuery query) {
		return FAMILIES.apply(this.read("device families", this.inventoryRepository::findFamilyCounts), query);
	}

	/**
	 * Tells what the list of the device tags can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public QuerySchema tagSchema() {
		return TAGS.schema();
	}

	/**
	 * Lists the distinct tags attached to at least one device, with their number of devices.
	 *
	 * @param query the query
	 * @return the tags of the window, with the total number of matching tags if the query asks for it
	 * @throws NetshotException if the tags can't be read
	 */
	public ListResult<DeviceTagCount> listTags(ListQuery query) {
		return TAGS.apply(this.read("device tags", this.inventoryRepository::findTagCounts), query);
	}

	/**
	 * Tells what the list of the part numbers can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public QuerySchema partNumberSchema() {
		return PART_NUMBERS.schema();
	}

	/**
	 * Lists the distinct part numbers of the modules, with their number of modules.
	 *
	 * @param query the query
	 * @return the part numbers of the window, with the total number of matching part numbers if the query asks for it
	 * @throws NetshotException if the part numbers can't be read
	 */
	public ListResult<PartNumberCount> listPartNumbers(ListQuery query) {
		return PART_NUMBERS.apply(this.read("part numbers", this.inventoryRepository::findPartNumberCounts), query);
	}

	/**
	 * Tells what the list of the software versions can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public QuerySchema softwareVersionSchema() {
		return SOFTWARE_VERSIONS.schema();
	}

	/**
	 * Lists the distinct software versions of the devices, with their number of devices.
	 *
	 * @param query the query
	 * @return the software versions of the window, with the total number of matching versions if the query asks for it
	 * @throws NetshotException if the software versions can't be read
	 */
	public ListResult<SoftwareVersionCount> listSoftwareVersions(ListQuery query) {
		return SOFTWARE_VERSIONS.apply(this.read("software versions", this.inventoryRepository::findSoftwareVersionCounts), query);
	}

	private <T> List<T> read(String what, Function<DSLContext, List<T>> query) {
		try {
			return this.transactions.read(query);
		}
		catch (DataAccessException e) {
			log.error("Error while loading {}.", what, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Database error");
		}
	}
}
