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

import java.util.List;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.search.Finder.FinderParseException;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The device group use cases: list, read, add, edit and delete the groups.
 *
 * <p>The groups it returns are loaded without their members, which a group representation
 * doesn't read. The membership of a group is computed (and stored) when it is added or
 * edited: the members of a static group are the devices the client gives, the ones of a
 * dynamic group are the result of its query.</p>
 */
@Slf4j
public class GroupService {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final GroupRepository repository;

	/** The refresher of the dynamic groups. */
	private final DynamicGroups dynamicGroups;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the group repository
	 * @param dynamicGroups the refresher of the dynamic groups
	 */
	public GroupService(Transactions transactions, GroupRepository repository, DynamicGroups dynamicGroups) {
		this.dynamicGroups = dynamicGroups;
		this.transactions = transactions;
		this.repository = repository;
	}

	/**
	 * Lists the groups.
	 *
	 * @param page the window on the result
	 * @return the groups, by ID
	 * @throws NetshotException if the groups can't be read
	 */
	public List<DeviceGroup> list(Page page) {
		try {
			return this.transactions.read(dsl -> this.repository.findAll(dsl, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the groups.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the groups");
		}
	}

	/**
	 * Gets a group.
	 *
	 * @param id the group ID
	 * @return the group
	 * @throws NetshotException if the group doesn't exist, or can't be read
	 */
	public DeviceGroup get(long id) {
		try {
			return this.transactions.read(dsl -> {
				DeviceGroup group = this.repository.find(dsl, id);
				if (group == null) {
					log.warn("Unable to find the device group.");
					throw new NetshotException(ErrorCode.NETSHOT_GROUP_NOT_FOUND, "Unable to find the device group");
				}
				return group;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the group.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the group");
		}
	}

	/**
	 * Adds a group. The members of the new group are set: the given devices for a static group,
	 * the result of the query for a dynamic one.
	 *
	 * @param definition the group to add
	 * @return the new group
	 * @throws NetshotException if the name or the type is invalid, a device of a static group
	 *         doesn't exist, the query of a dynamic group is invalid, the name is already used,
	 *         or the group can't be stored
	 */
	public DeviceGroup create(GroupDefinition definition) {
		String name = definition.name() == null ? "" : definition.name().trim();
		if (name.isEmpty()) {
			log.warn("User posted an empty group name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_GROUP_NAME, "Invalid group name.");
		}
		DeviceGroup group;
		try {
			group = this.transactions.write(dsl -> {
				if ("StaticDeviceGroup".equals(definition.type())) {
					List<Long> deviceIds = this.checkDevices(dsl, definition.staticDevices());
					DeviceGroup added = this.repository.add(dsl, StaticDeviceGroup.builder()
						.name(name)
						.folder(definition.folder())
						.hiddenFromReports(definition.hiddenFromReports())
						.build());
					this.repository.setMembers(dsl, added.id(), deviceIds);
					return added;
				}
				else if ("DynamicDeviceGroup".equals(definition.type())) {
					String query = formatQuery(definition.query() == null ? DynamicDeviceGroup.DEFAULT_QUERY : definition.query());
					DynamicDeviceGroup added = (DynamicDeviceGroup) this.repository.add(dsl, DynamicDeviceGroup.builder()
						.name(name)
						.folder(definition.folder())
						.hiddenFromReports(definition.hiddenFromReports())
						.query(query)
						.build());
					refreshMembers(dsl, added);
					return added;
				}
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_GROUP, "Invalid group type.");
			});
		}
		catch (DataAccessException e) {
			log.error("Error while saving the new device group.", e);
			if (DatabaseErrors.isUniqueViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_GROUP, "A group with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the group to the database");
		}
		AAA_LOG.info("{} has been created.", group);
		return group;
	}

	/**
	 * Edits a group. The kind of the group can't change: the definition gives the new devices of
	 * a static group, or the new query of a dynamic group (which members are then refreshed).
	 *
	 * @param id the group ID
	 * @param definition the new definition of the group
	 * @return the edited group
	 * @throws NetshotException if the group doesn't exist, a device of a static group doesn't
	 *         exist, the query of a dynamic group is invalid, the name is invalid or already used,
	 *         or the group can't be stored
	 */
	public DeviceGroup update(long id, GroupDefinition definition) {
		DeviceGroup group;
		try {
			group = this.transactions.write(dsl -> {
				DeviceGroup existing = this.repository.find(dsl, id);
				if (existing == null) {
					log.error("Unable to find the group {} to be edited.", id);
					throw new NetshotException(ErrorCode.NETSHOT_GROUP_NOT_FOUND, "Unable to find this group.");
				}
				List<Long> deviceIds = null;
				String query = null;
				if (existing instanceof StaticDeviceGroup) {
					deviceIds = this.checkDevices(dsl, definition.staticDevices());
				}
				else if (existing instanceof DynamicDeviceGroup) {
					query = formatQuery(definition.query());
				}
				String name = definition.name() == null ? "" : definition.name().trim();
				if (name.isEmpty()) {
					log.warn("User posted an empty group name.");
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_GROUP_NAME, "Invalid group name.");
				}
				DeviceGroup edited = switch (existing) {
					case StaticDeviceGroup staticGroup -> staticGroup.toBuilder()
						.name(name)
						.folder(definition.folder())
						.hiddenFromReports(definition.hiddenFromReports())
						.build();
					case DynamicDeviceGroup dynamicGroup -> dynamicGroup.toBuilder()
						.name(name)
						.folder(definition.folder())
						.hiddenFromReports(definition.hiddenFromReports())
						.query(query)
						.build();
				};
				DeviceGroup saved = this.repository.save(dsl, edited);
				if (saved instanceof DynamicDeviceGroup dynamicGroup) {
					refreshMembers(dsl, dynamicGroup);
				}
				else {
					this.repository.setMembers(dsl, id, deviceIds);
				}
				return saved;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to save the group {}.", id, e);
			if (DatabaseErrors.isUniqueViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_GROUP, "A group with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the group.");
		}
		AAA_LOG.info("{} has been edited.", group);
		return group;
	}

	/**
	 * Deletes a group. This doesn't delete the devices of the group.
	 *
	 * @param id the group ID
	 * @return true if the group has been deleted, false if there is no such group
	 * @throws NetshotException if the group can't be deleted
	 */
	public boolean delete(long id) {
		try {
			boolean deleted = this.transactions.write(dsl -> {
				if (!this.repository.remove(dsl, id)) {
					log.info("No such device group of ID {}", id);
					return false;
				}
				return true;
			});
			if (deleted) {
				AAA_LOG.info("Device group of ID {} has been deleted.", id);
			}
			return deleted;
		}
		catch (DataAccessException e) {
			log.error("Unable to delete the group {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the group");
		}
	}

	private List<Long> checkDevices(DSLContext dsl, List<Long> deviceIds) {
		List<Long> ids = deviceIds == null ? List.of() : deviceIds;
		Set<Long> existing = this.repository.findExistingDeviceIds(dsl, ids);
		for (Long deviceId : ids) {
			if (!existing.contains(deviceId)) {
				log.warn("Device of ID {} doesn't exist.", deviceId);
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_DEVICE,
					"Device of ID %d doesn't exist.".formatted(deviceId));
			}
		}
		return ids;
	}

	private String formatQuery(String query) {
		try {
			return this.dynamicGroups.formatQuery(query);
		}
		catch (FinderParseException e) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DYNAMICGROUP_QUERY, "Invalid query for the group definition.");
		}
	}

	private void refreshMembers(DSLContext dsl, DynamicDeviceGroup group) {
		try {
			this.dynamicGroups.refresh(dsl, group);
		}
		catch (FinderParseException e) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DYNAMICGROUP_QUERY, "Invalid query for the group definition.");
		}
	}
}
