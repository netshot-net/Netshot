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

import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_TAGS;
import static net.netshot.netshot.platform.database.schema.Tables.MODULE;
import static net.netshot.netshot.platform.database.schema.Tables.NETWORK_INTERFACE;
import static net.netshot.netshot.platform.database.schema.Tables.NETWORK_INTERFACE_IP4ADDRESSES;
import static net.netshot.netshot.platform.database.schema.Tables.NETWORK_INTERFACE_IP6ADDRESSES;

import java.net.InetAddress;
import java.text.ParseException;
import java.util.EnumSet;
import java.util.List;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

import net.netshot.netshot.platform.database.QueryMapping;
import net.netshot.netshot.platform.database.QueryMapping.Option;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.net.PhysicalAddress;
import net.netshot.netshot.platform.query.FieldType;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.Lookup;
import net.netshot.netshot.platform.query.SortOrder;

/**
 * The queries on what belongs to the devices (their network interfaces and modules) and on the reference lists
 * built from the devices (families, tags, part numbers, software versions), for the list use cases. The
 * {@link DSLContext} is always the first parameter: the caller (the service) owns the transaction.
 */
public class DeviceInventoryRepository {

	/** The filters, sort and search of the network interfaces. */
	public static final QueryMapping INTERFACE_LIST = QueryMapping.builder(NETWORK_INTERFACE.ID)
		.string("name", NETWORK_INTERFACE.INTERFACE_NAME, Option.SORTABLE, Option.SEARCHABLE)
		.string("description", NETWORK_INTERFACE.DESCRIPTION, Option.SORTABLE, Option.SEARCHABLE)
		.string("vrf", NETWORK_INTERFACE.VRF_INSTANCE, Option.SORTABLE)
		.string("virtualDevice", NETWORK_INTERFACE.VIRTUAL_DEVICE, Option.SORTABLE)
		.bool("enabled", NETWORK_INTERFACE.ENABLED, Option.SORTABLE)
		.bool("level3", NETWORK_INTERFACE.LEVEL3, Option.SORTABLE)
		.custom("macAddress", FieldType.STRING, EnumSet.of(Lookup.EXACT, Lookup.N), (_, values) -> {
			List<Condition> any = values.stream().map(v -> NETWORK_INTERFACE.PHYSICAL_ADDRESS.eq(macAddress((String) v))).toList();
			return DSL.or(any);
		})
		.custom("ipAddress", FieldType.INET, EnumSet.of(Lookup.EXACT, Lookup.N, Lookup.NET), (lookup, values) ->
			DSL.or(values.stream().map(v -> hasAddress(lookup, v)).toList()))
		.defaultSort(SortOrder.asc("id"))
		.build();

	/** The filters, sort and search of the modules. */
	public static final QueryMapping MODULE_LIST = QueryMapping.builder(MODULE.ID)
		.string("slot", MODULE.SLOT, Option.SORTABLE, Option.SEARCHABLE)
		.string("partNumber", MODULE.PART_NUMBER, Option.SORTABLE, Option.SEARCHABLE)
		.string("serialNumber", MODULE.SERIAL_NUMBER, Option.SORTABLE, Option.SEARCHABLE)
		.date("firstSeenDate", MODULE.FIRST_SEEN_DATE, Option.SORTABLE)
		.date("lastSeenDate", MODULE.LAST_SEEN_DATE, Option.SORTABLE)
		.bool("removed", MODULE.REMOVED, Option.SORTABLE)
		.defaultSort(SortOrder.asc("id"))
		.build();

	private static long macAddress(String text) {
		try {
			return new PhysicalAddress(text).getAddress();
		}
		catch (ParseException e) {
			throw NetshotException.invalidParameter("macAddress",
				"Invalid value '%s' for parameter 'macAddress': a MAC address is expected".formatted(text));
		}
	}

	private static Condition hasAddress(Lookup lookup, Object value) {
		Condition ip4 = DSL.exists(DSL.selectOne().from(NETWORK_INTERFACE_IP4ADDRESSES)
			.where(NETWORK_INTERFACE_IP4ADDRESSES.NETWORK_INTERFACE.eq(NETWORK_INTERFACE.ID))
			.and(address(NETWORK_INTERFACE_IP4ADDRESSES.ADDRESS, lookup, value)));
		Condition ip6 = DSL.exists(DSL.selectOne().from(NETWORK_INTERFACE_IP6ADDRESSES)
			.where(NETWORK_INTERFACE_IP6ADDRESSES.NETWORK_INTERFACE.eq(NETWORK_INTERFACE.ID))
			.and(address(NETWORK_INTERFACE_IP6ADDRESSES.ADDRESS, lookup, value)));
		return ip4.or(ip6);
	}

	private static Condition address(Field<InetAddress> column, Lookup lookup, Object value) {
		if (lookup == Lookup.NET) {
			return DSL.condition("{0} <<= cast({1} as inet)", column, DSL.val((String) value));
		}
		return column.eq(DSL.val(value, column));
	}

	/**
	 * Tells whether a device exists.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 * @return true if there is such a device
	 */
	public boolean deviceExists(DSLContext dsl, long deviceId) {
		return dsl.fetchExists(DEVICE, DEVICE.ID.eq(deviceId));
	}

	/**
	 * Lists the network interfaces of a device matching a query, with their IP addresses.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 * @param query the query
	 * @return the interfaces of the window of the query (none if there is no such device)
	 */
	public List<NetworkInterface> findInterfaces(DSLContext dsl, long deviceId, ListQuery query) {
		return query.page().apply(dsl.select(DeviceRepository.INTERFACE_FIELDS).from(NETWORK_INTERFACE)
				.where(NETWORK_INTERFACE.DEVICE.eq(deviceId), INTERFACE_LIST.condition(query))
				.orderBy(INTERFACE_LIST.orderBy(query)))
			.fetch(DeviceRepository::toInterface);
	}

	/**
	 * Counts the network interfaces of a device matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 * @param query the query (its window is ignored)
	 * @return the number of interfaces
	 */
	public long countInterfaces(DSLContext dsl, long deviceId, ListQuery query) {
		return dsl.fetchCount(NETWORK_INTERFACE, NETWORK_INTERFACE.DEVICE.eq(deviceId).and(INTERFACE_LIST.condition(query)));
	}

	/**
	 * Lists the modules of a device matching a query (the removed ones included, unless filtered out).
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 * @param query the query
	 * @return the modules of the window of the query (none if there is no such device)
	 */
	public List<Module> findModules(DSLContext dsl, long deviceId, ListQuery query) {
		return query.page().apply(dsl.select(MODULE.fields()).from(MODULE)
				.where(MODULE.DEVICE.eq(deviceId), MODULE_LIST.condition(query))
				.orderBy(MODULE_LIST.orderBy(query)))
			.fetch(m -> DeviceRepository.toModule(m, MODULE));
	}

	/**
	 * Counts the modules of a device matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 * @param query the query (its window is ignored)
	 * @return the number of modules
	 */
	public long countModules(DSLContext dsl, long deviceId, ListQuery query) {
		return dsl.fetchCount(MODULE, MODULE.DEVICE.eq(deviceId).and(MODULE_LIST.condition(query)));
	}

	/**
	 * Lists the distinct device families (driver and family), with their number of devices.
	 *
	 * @param dsl the jOOQ context
	 * @return the families
	 */
	public List<DeviceFamilyCount> findFamilyCounts(DSLContext dsl) {
		Field<Integer> count = DSL.count();
		return dsl.select(DEVICE.DRIVER, DEVICE.FAMILY, count).from(DEVICE).groupBy(DEVICE.DRIVER, DEVICE.FAMILY)
			.fetch(r -> new DeviceFamilyCount(r.get(DEVICE.DRIVER), r.get(DEVICE.FAMILY), r.get(count).longValue()));
	}

	/**
	 * Lists the tags attached to at least one device, with their number of devices.
	 *
	 * @param dsl the jOOQ context
	 * @return the tags
	 */
	public List<DeviceTagCount> findTagCounts(DSLContext dsl) {
		Field<Integer> count = DSL.count(DEVICE_TAGS.DEVICE);
		return dsl.select(DEVICE_TAGS.TAG, count).from(DEVICE_TAGS).groupBy(DEVICE_TAGS.TAG)
			.fetch(r -> new DeviceTagCount(r.get(DEVICE_TAGS.TAG), r.get(count).longValue()));
	}

	/**
	 * Lists the distinct part numbers of the modules, with their number of modules.
	 *
	 * @param dsl the jOOQ context
	 * @return the part numbers
	 */
	public List<PartNumberCount> findPartNumberCounts(DSLContext dsl) {
		Field<Integer> count = DSL.count();
		return dsl.select(MODULE.PART_NUMBER, count).from(MODULE).groupBy(MODULE.PART_NUMBER)
			.fetch(r -> new PartNumberCount(r.get(MODULE.PART_NUMBER) == null ? "" : r.get(MODULE.PART_NUMBER), r.get(count).longValue()));
	}

	/**
	 * Lists the distinct (non empty) software versions of the devices, with their number of devices.
	 *
	 * @param dsl the jOOQ context
	 * @return the software versions
	 */
	public List<SoftwareVersionCount> findSoftwareVersionCounts(DSLContext dsl) {
		Field<Integer> count = DSL.count();
		return dsl.select(DEVICE.SOFTWARE_VERSION, count).from(DEVICE)
			.where(DEVICE.SOFTWARE_VERSION.isNotNull(), DEVICE.SOFTWARE_VERSION.ne(""))
			.groupBy(DEVICE.SOFTWARE_VERSION)
			.fetch(r -> new SoftwareVersionCount(r.get(DEVICE.SOFTWARE_VERSION), r.get(count).longValue()));
	}
}
