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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.jooq.DSLContext;
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

	/** The loaded device drivers, to parse the queries against. */
	private final DriverRegistry driverRegistry;

	/** Serializes the refreshes of all the groups. */
	private final Object refreshAllLock = new Object();

	/**
	 * Creates the refresher.
	 *
	 * @param driverRegistry the loaded device drivers, to parse the queries against
	 */
	public DynamicGroups(DriverRegistry driverRegistry) {
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
		return new Finder(group.query(), this.driverRegistry);
	}

	/**
	 * Refreshes the members of a dynamic group: they become the devices its query finds.
	 *
	 * @param dsl the jOOQ context
	 * @param group the group (already stored)
	 * @throws FinderParseException if the query of the group is invalid
	 */
	public void refresh(DSLContext dsl, DynamicDeviceGroup group) throws FinderParseException {
		Finder finder = this.finder(group);
		List<Long> deviceIds = dsl.select(DEVICE.ID).from(DEVICE).where(finder.getCondition()).fetch(DEVICE.ID);
		REPOSITORY.setMembers(dsl, group.id(), deviceIds);
	}

	/**
	 * Formats the query of a dynamic group (as the search engine prints it back).
	 *
	 * @param query the query
	 * @return the formatted query
	 * @throws FinderParseException if the query is invalid
	 */
	public String formatQuery(String query) throws FinderParseException {
		return new Finder(query, this.driverRegistry).getFormattedQuery();
	}

	private void insertMemberships(DSLContext dsl, DynamicDeviceGroup group, Finder finder) {
		dsl.insertInto(DEVICE_GROUP_CACHED_DEVICES, DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES,
				DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS, DEVICE_GROUP_CACHED_DEVICES.LIST_POSITION)
			.select(DSL.select(DEVICE.ID, DSL.val(group.id()), DSL.val(0)).from(DEVICE).where(finder.getCondition()))
			.onConflictDoNothing()
			.execute();
	}

	/**
	 * Refreshes the membership of one device in all the dynamic groups (its static groups
	 * are not touched).
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 */
	public void refreshAllGroupsOfOneDevice(DSLContext dsl, long deviceId) {
		List<DynamicDeviceGroup> allGroups = REPOSITORY.findDynamic(dsl);
		dsl.deleteFrom(DEVICE_GROUP_CACHED_DEVICES)
			.where(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(deviceId))
			.and(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.in(
				DSL.select(DEVICE_GROUP.ID).from(DEVICE_GROUP).where(DEVICE_GROUP.TYPE.eq(GroupRepository.DYNAMIC))))
			.execute();
		for (DynamicDeviceGroup group : allGroups) {
			try {
				log.trace("Checking device {} vs group {}", deviceId, group.id());
				String deviceQuery = "[Device] is %d".formatted(deviceId);
				if (group.query() != null && !group.query().isBlank()) {
					deviceQuery += " and (%s)".formatted(group.query());
				}
				log.trace("Finder query for group: '{}'.", deviceQuery);
				this.insertMemberships(dsl, group, new Finder(deviceQuery, this.driverRegistry));
			}
			catch (FinderParseException e) {
				log.error("Parse error while updating the group {}.", group.id(), e);
			}
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
			new Transactions().run(dsl -> this.refreshAllGroupsOfOneDevice(dsl, deviceId));
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
			new Transactions().run(dsl -> {
				for (DynamicDeviceGroup group : REPOSITORY.findDynamic(dsl)) {
					try {
						log.trace("Refreshing group {} ({})", group.id(), group.name());
						dsl.deleteFrom(DEVICE_GROUP_CACHED_DEVICES).where(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(group.id())).execute();
						this.insertMemberships(dsl, group, this.finder(group));
					}
					catch (FinderParseException e) {
						log.error("Parse error while updating the group {}.", group.id(), e);
					}
				}
			});
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
