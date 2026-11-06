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
package net.netshot.netshot.inventory.group;

import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_GROUP;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_GROUP_CACHED_DEVICES;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.InsertValuesStep3;
import org.jooq.Record;
import org.jooq.impl.DSL;

import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.QueryMapping;
import net.netshot.netshot.platform.database.QueryMapping.Option;
import net.netshot.netshot.platform.database.Versions;
import net.netshot.netshot.platform.query.FieldType;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.Lookup;
import net.netshot.netshot.platform.database.schema.tables.records.DeviceGroupCachedDevicesRecord;

/**
 * The queries on device groups and their members. The {@link DSLContext} is always the first
 * parameter: the caller (the service) owns the transaction.
 *
 * <p>The groups are returned without their members: a group representation only reads its own
 * attributes. The members are stored in order (the position of each device).</p>
 */
public class GroupRepository {

	/** The type (discriminator) of the static groups. */
	static final String STATIC = "STATIC";

	/** The type (discriminator) of the dynamic groups. */
	static final String DYNAMIC = "DYNAMIC";

	/**
	 * Builds a group from the columns of a (possibly aliased) group table.
	 *
	 * @param r the record
	 * @param g the group table the record was read from
	 * @return the group
	 * @throws IllegalStateException if the type of the group is unknown
	 */
	public static DeviceGroup toGroup(Record r, net.netshot.netshot.platform.database.schema.tables.DeviceGroup g) {
		String type = r.get(g.TYPE);
		return switch (type) {
			case STATIC -> new StaticDeviceGroup(r.get(g.ID), r.get(g.VERSION), r.get(g.CHANGE_DATE), r.get(g.NAME),
				r.get(g.FOLDER), Boolean.TRUE.equals(r.get(g.HIDDEN_FROM_REPORTS)));
			case DYNAMIC -> new DynamicDeviceGroup(r.get(g.ID), r.get(g.VERSION), r.get(g.CHANGE_DATE), r.get(g.NAME),
				r.get(g.FOLDER), Boolean.TRUE.equals(r.get(g.HIDDEN_FROM_REPORTS)), r.get(g.QUERY));
			default -> throw new IllegalStateException("Unknown type of device group '%s'".formatted(type));
		};
	}

	private static DeviceGroup toGroup(Record r) {
		return toGroup(r, DEVICE_GROUP);
	}

	/**
	 * The time of a change, to the second (as the former Hibernate interceptor did).
	 *
	 * @return the time
	 */
	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.SECONDS);
	}

	/** The fields the group lists can be filtered and sorted on. */
	static final QueryMapping LIST = QueryMapping.builder(DEVICE_GROUP.ID)
		.string("name", DEVICE_GROUP.NAME, Option.SORTABLE, Option.SEARCHABLE)
		.string("folder", DEVICE_GROUP.FOLDER, Option.SORTABLE, Option.SEARCHABLE)
		.enumeration("type", DEVICE_GROUP.TYPE, DeviceGroup.Type.class, Option.SORTABLE)
		.bool("hiddenFromReports", DEVICE_GROUP.HIDDEN_FROM_REPORTS)
		.string("query", DEVICE_GROUP.QUERY, Option.SEARCHABLE)
		.date("changeDate", DEVICE_GROUP.CHANGE_DATE, Option.SORTABLE)
		.custom("device", FieldType.INTEGER, EnumSet.of(Lookup.EXACT, Lookup.N), (_, values) ->
			DSL.exists(DSL.selectOne().from(DEVICE_GROUP_CACHED_DEVICES)
				.where(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(DEVICE_GROUP.ID))
				.and(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.in(values.stream().map(Long.class::cast).toList()))))
		.build();

	/**
	 * Lists the groups matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query
	 * @return the groups of the window of the query
	 */
	public List<DeviceGroup> findAll(DSLContext dsl, ListQuery query) {
		return query.page().apply(dsl.select(DEVICE_GROUP.fields()).from(DEVICE_GROUP)
			.where(LIST.condition(query))
			.orderBy(LIST.orderBy(query)))
			.fetch(GroupRepository::toGroup);
	}

	/**
	 * Counts the groups matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query (its window is ignored)
	 * @return the number of groups
	 */
	public long count(DSLContext dsl, ListQuery query) {
		return dsl.fetchCount(DEVICE_GROUP, LIST.condition(query));
	}

	/**
	 * Lists the groups, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the groups
	 */
	public List<DeviceGroup> findAll(DSLContext dsl, Page page) {
		return page.apply(dsl.select(DEVICE_GROUP.fields()).from(DEVICE_GROUP).orderBy(DEVICE_GROUP.ID.asc()))
			.fetch(GroupRepository::toGroup);
	}

	/**
	 * Lists the dynamic groups, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @return the dynamic groups
	 */
	public List<DynamicDeviceGroup> findDynamic(DSLContext dsl) {
		return dsl.select(DEVICE_GROUP.fields()).from(DEVICE_GROUP).where(DEVICE_GROUP.TYPE.eq(DYNAMIC)).orderBy(DEVICE_GROUP.ID.asc())
			.fetch(r -> (DynamicDeviceGroup) toGroup(r));
	}

	/**
	 * Finds a group.
	 *
	 * @param dsl the jOOQ context
	 * @param id the group ID
	 * @return the group, or null if there is no such group
	 */
	public DeviceGroup find(DSLContext dsl, long id) {
		return dsl.select(DEVICE_GROUP.fields()).from(DEVICE_GROUP).where(DEVICE_GROUP.ID.eq(id)).fetchOne(GroupRepository::toGroup);
	}

	/**
	 * Lists the groups a device is a member of, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 * @return the groups
	 */
	public List<DeviceGroup> findOfDevice(DSLContext dsl, long deviceId) {
		return dsl.select(DEVICE_GROUP.fields()).from(DEVICE_GROUP)
			.join(DEVICE_GROUP_CACHED_DEVICES).on(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(DEVICE_GROUP.ID))
			.where(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(deviceId))
			.orderBy(DEVICE_GROUP.ID.asc())
			.fetch(GroupRepository::toGroup);
	}

	/**
	 * Stores a new group (version 0, changed now), without members.
	 *
	 * @param dsl the jOOQ context
	 * @param group the group (its ID, version and change date are ignored)
	 * @return the stored group
	 */
	public DeviceGroup add(DSLContext dsl, DeviceGroup group) {
		long id = dsl.insertInto(DEVICE_GROUP)
			.set(DEVICE_GROUP.TYPE, group instanceof DynamicDeviceGroup ? DYNAMIC : STATIC)
			.set(DEVICE_GROUP.VERSION, 0)
			.set(DEVICE_GROUP.CHANGE_DATE, now())
			.set(DEVICE_GROUP.NAME, group.name())
			.set(DEVICE_GROUP.FOLDER, group.folder())
			.set(DEVICE_GROUP.HIDDEN_FROM_REPORTS, group.hiddenFromReports())
			.set(DEVICE_GROUP.QUERY, group instanceof DynamicDeviceGroup dynamic ? dynamic.query() : null)
			.returning(DEVICE_GROUP.ID)
			.fetchOne(DEVICE_GROUP.ID);
		return this.find(dsl, id);
	}

	/**
	 * Locks the row of a group until the end of the transaction (see {@link Versions#lock}).
	 *
	 * @param dsl the jOOQ context
	 * @param id the group ID
	 * @return the version of the group, or null if there is no such group
	 */
	public Integer lock(DSLContext dsl, long id) {
		return Versions.lock(dsl, DEVICE_GROUP, DEVICE_GROUP.ID, DEVICE_GROUP.VERSION, id);
	}

	/**
	 * Saves an existing group (its version is incremented, its change date set), not its members.
	 *
	 * @param dsl the jOOQ context
	 * @param group the group
	 * @return the saved group, or null if there is no such group
	 */
	public DeviceGroup save(DSLContext dsl, DeviceGroup group) {
		int updated = dsl.update(DEVICE_GROUP)
			.set(DEVICE_GROUP.VERSION, DEVICE_GROUP.VERSION.plus(1))
			.set(DEVICE_GROUP.CHANGE_DATE, now())
			.set(DEVICE_GROUP.NAME, group.name())
			.set(DEVICE_GROUP.FOLDER, group.folder())
			.set(DEVICE_GROUP.HIDDEN_FROM_REPORTS, group.hiddenFromReports())
			.set(DEVICE_GROUP.QUERY, group instanceof DynamicDeviceGroup dynamic ? dynamic.query() : null)
			.where(DEVICE_GROUP.ID.eq(group.id()))
			.execute();
		return updated == 0 ? null : this.find(dsl, group.id());
	}

	/**
	 * Removes a group, with its memberships (the devices are kept).
	 *
	 * @param dsl the jOOQ context
	 * @param id the group ID
	 * @return true if the group was found and removed
	 */
	public boolean remove(DSLContext dsl, long id) {
		return dsl.deleteFrom(DEVICE_GROUP).where(DEVICE_GROUP.ID.eq(id)).execute() > 0;
	}

	/**
	 * Lists the IDs of the members of a group, in their order.
	 *
	 * @param dsl the jOOQ context
	 * @param groupId the group ID
	 * @return the device IDs
	 */
	public List<Long> findMemberIds(DSLContext dsl, long groupId) {
		return dsl.select(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES).from(DEVICE_GROUP_CACHED_DEVICES)
			.where(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(groupId))
			.orderBy(DEVICE_GROUP_CACHED_DEVICES.LIST_POSITION.asc(), DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.asc())
			.fetch(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES);
	}

	/**
	 * Replaces the members of a group (a device given twice is a member once, at its first position).
	 *
	 * @param dsl the jOOQ context
	 * @param groupId the group ID
	 * @param deviceIds the IDs of the devices, in their order
	 */
	public void setMembers(DSLContext dsl, long groupId, List<Long> deviceIds) {
		dsl.deleteFrom(DEVICE_GROUP_CACHED_DEVICES).where(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(groupId)).execute();
		if (deviceIds.isEmpty()) {
			return;
		}
		InsertValuesStep3<DeviceGroupCachedDevicesRecord, Long, Long, Integer> insert = dsl.insertInto(DEVICE_GROUP_CACHED_DEVICES,
			DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS, DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES, DEVICE_GROUP_CACHED_DEVICES.LIST_POSITION);
		Set<Long> seen = new HashSet<>();
		int position = 0;
		for (Long deviceId : deviceIds) {
			if (seen.add(deviceId)) {
				insert = insert.values(groupId, deviceId, position);
			}
			position++;
		}
		insert.execute();
	}

	/**
	 * Adds a device at the end of a group (nothing if it is already a member).
	 *
	 * @param dsl the jOOQ context
	 * @param groupId the group ID
	 * @param deviceId the device ID
	 */
	public void addMember(DSLContext dsl, long groupId, long deviceId) {
		Integer last = dsl.select(org.jooq.impl.DSL.max(DEVICE_GROUP_CACHED_DEVICES.LIST_POSITION)).from(DEVICE_GROUP_CACHED_DEVICES)
			.where(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(groupId)).fetchOne(0, Integer.class);
		dsl.insertInto(DEVICE_GROUP_CACHED_DEVICES, DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS, DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES,
				DEVICE_GROUP_CACHED_DEVICES.LIST_POSITION)
			.values(groupId, deviceId, last == null ? 0 : last + 1)
			.onConflictDoNothing()
			.execute();
	}

	/**
	 * Finds which of the given devices exist.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceIds the device IDs
	 * @return the IDs of the devices which exist
	 */
	public Set<Long> findExistingDeviceIds(DSLContext dsl, Collection<Long> deviceIds) {
		if (deviceIds.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(dsl.select(DEVICE.ID).from(DEVICE).where(DEVICE.ID.in(deviceIds)).fetch(DEVICE.ID));
	}

	/**
	 * Gets the IDs of the groups of a device.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the ID of the device
	 * @return the IDs of the groups
	 */
	public static Set<Long> findGroupIdsOfDevice(DSLContext dsl, long deviceId) {
		return new HashSet<>(dsl.select(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS).from(DEVICE_GROUP_CACHED_DEVICES)
			.where(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(deviceId))
			.fetch(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS));
	}
}
