/**
 * Copyright 2013-present Netshot
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
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_GROUP_CACHED_DEVICES;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Select;
import org.jooq.impl.DSL;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.search.Finder;
import net.netshot.netshot.inventory.search.Finder.FinderParseException;
import net.netshot.netshot.platform.database.Transactions;

/**
 * Refreshes the members of the dynamic groups (the devices their query finds).
 */
@Slf4j
public final class DynamicGroups {

	private static final GroupRepository REPOSITORY = new GroupRepository();

	/**
	 * How many groups are refreshed by one statement: the statement grows with the queries of its groups
	 * (planning time, bound values), so many groups take several statements.
	 */
	static final int BATCH_SIZE = 100;

	/** The loaded device drivers, to parse the queries against. */
	private final DriverRegistry driverRegistry;

	/** The transaction support. */
	private final Transactions transactions;

	/** Serializes the refreshes of all the groups. */
	private final Object refreshAllLock = new Object();

	/**
	 * Creates the refresher.
	 *
	 * @param transactions the transaction support
	 * @param driverRegistry the loaded device drivers, to parse the queries against
	 */
	public DynamicGroups(Transactions transactions, DriverRegistry driverRegistry) {
		this.transactions = transactions;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Parses the query of a dynamic group.
	 *
	 * @param group the group
	 * @return the finder
	 * @throws FinderParseException if the query of the group is invalid
	 */
	public Finder finder(DynamicDeviceGroup group) throws FinderParseException {
		return new Finder(group.query() == null ? "" : group.query(), this.driverRegistry, this.transactions);
	}

	/**
	 * Refreshes the members of a dynamic group: they become the devices its query finds.
	 *
	 * @param dsl the jOOQ context
	 * @param group the group (already stored)
	 * @throws FinderParseException if the query of the group is invalid
	 */
	public void refresh(DSLContext dsl, DynamicDeviceGroup group) throws FinderParseException {
		refreshMembers(dsl, Map.of(group.id(), this.finder(group).getCondition()), null);
	}

	/**
	 * Refreshes the members of dynamic groups in one statement, writing only the differences: the
	 * memberships the query no longer finds are deleted, the new ones are inserted, the others are left
	 * untouched (no rewrite of rows, so no dead rows nor foreign key checks for them).
	 *
	 * @param dsl the jOOQ context
	 * @param conditions the condition of each group to refresh, by group ID
	 * @param deviceId the only device to refresh the membership of, null for all the devices
	 */
	private static void refreshMembers(DSLContext dsl, Map<Long, Condition> conditions, Long deviceId) {
		if (conditions.isEmpty()) {
			return;
		}
		// The statement is costly enough for PostgreSQL to compile it (JIT), which takes longer than running it
		// (5 s for 100 groups); until the end of the transaction
		dsl.execute("set local jit = off");
		Select<Record2<Long, Long>> union = null;
		for (Map.Entry<Long, Condition> entry : conditions.entrySet()) {
			Condition condition = deviceId == null ? entry.getValue() : DEVICE.ID.eq(deviceId).and(entry.getValue());
			Select<Record2<Long, Long>> members = DSL.select(DEVICE.ID, DSL.val(entry.getKey()))
				.from(DEVICE).where(condition);
			union = union == null ? members : union.unionAll(members);
		}
		CommonTableExpression<Record2<Long, Long>> wanted = DSL.name("wanted").fields("device", "grp").as(union);
		Field<Long> wantedDevice = wanted.field("device", Long.class);
		Field<Long> wantedGroup = wanted.field("grp", Long.class);
		CommonTableExpression<?> removed = DSL.name("removed").as(DSL.deleteFrom(DEVICE_GROUP_CACHED_DEVICES)
			.where(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.in(conditions.keySet()))
			.and(deviceId == null ? DSL.noCondition() : DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(deviceId))
			.andNotExists(DSL.selectOne().from(wanted)
				.where(wantedDevice.eq(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES))
				.and(wantedGroup.eq(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS)))
			.returning(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS));
		dsl.with(wanted).with(removed)
			.insertInto(DEVICE_GROUP_CACHED_DEVICES, DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES,
				DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS, DEVICE_GROUP_CACHED_DEVICES.LIST_POSITION)
			.select(DSL.select(wantedDevice, wantedGroup, DSL.val(0)).from(wanted)
				.whereNotExists(DSL.selectOne().from(DEVICE_GROUP_CACHED_DEVICES)
					.where(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(wantedDevice))
					.and(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(wantedGroup))))
			.onConflictDoNothing()
			.execute();
	}

	/**
	 * Formats the query of a dynamic group (as the search engine prints it back).
	 *
	 * @param query the query
	 * @return the formatted query
	 * @throws FinderParseException if the query is invalid
	 */
	public String formatQuery(String query) throws FinderParseException {
		return new Finder(query, this.driverRegistry, this.transactions).getFormattedQuery();
	}

	/**
	 * Refreshes the membership of one device in all the dynamic groups (its static groups
	 * are not touched).
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 */
	public void refreshAllGroupsOfOneDevice(DSLContext dsl, long deviceId) {
		refreshInBatches(dsl, this.parseAll(dsl), deviceId);
	}

	/**
	 * Parses the queries of all the dynamic groups. A group whose query doesn't parse is left out (so it keeps its members).
	 *
	 * @param dsl the jOOQ context
	 * @return the condition of each group, by group ID
	 */
	private Map<Long, Condition> parseAll(DSLContext dsl) {
		Map<Long, Condition> conditions = new LinkedHashMap<>();
		for (DynamicDeviceGroup group : REPOSITORY.findDynamic(dsl)) {
			try {
				conditions.put(group.id(), this.finder(group).getCondition());
			}
			catch (FinderParseException e) {
				log.error("Parse error while updating the group {}.", group.id(), e);
			}
		}
		return conditions;
	}

	/**
	 * Refreshes the members of dynamic groups, {@link #BATCH_SIZE} groups per statement.
	 *
	 * @param dsl the jOOQ context
	 * @param conditions the condition of each group to refresh, by group ID
	 * @param deviceId the only device to refresh the membership of, null for all the devices
	 */
	private static void refreshInBatches(DSLContext dsl, Map<Long, Condition> conditions, Long deviceId) {
		List<Long> groupIds = new ArrayList<>(conditions.keySet());
		for (int start = 0; start < groupIds.size(); start += BATCH_SIZE) {
			Map<Long, Condition> batch = new LinkedHashMap<>();
			for (Long groupId : groupIds.subList(start, Math.min(start + BATCH_SIZE, groupIds.size()))) {
				batch.put(groupId, conditions.get(groupId));
			}
			refreshMembers(dsl, batch, deviceId);
		}
	}

	/**
	 * Refreshes the membership of one device in all the dynamic groups, in a transaction of its own.
	 * A failure is only logged.
	 *
	 * @param deviceId the device ID
	 */
	public void refreshAllGroupsOfOneDevice(long deviceId) {
		log.debug("Refreshing all groups for device {}.", deviceId);
		try {
			this.transactions.run(dsl -> this.refreshAllGroupsOfOneDevice(dsl, deviceId));
		}
		catch (Exception e) {
			log.error("Error while refreshing the dynamic groups.", e);
		}
	}

	/**
	 * Refreshes the members of all the dynamic groups, in a transaction of its own.
	 * A failure is only logged.
	 */
	public void refreshAllGroups() {
		synchronized (this.refreshAllLock) {
			this.refreshAllGroupsLocked();
		}
	}

	private void refreshAllGroupsLocked() {
		log.debug("Refreshing all dynamic groups.");
		Instant before = Instant.now();
		try {
			this.transactions.run(dsl -> refreshInBatches(dsl, this.parseAll(dsl), null));
		}
		catch (Exception e) {
			log.error("Error while refreshing the dynamic groups.", e);
		}
		Duration duration = Duration.between(before, Instant.now());
		if (duration.toMillis() > 1000) {
			log.info("Refreshing all dynamic groups took {}ms", duration.toMillis());
		}
	}
}
