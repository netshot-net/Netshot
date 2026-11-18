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
package net.netshot.netshot.api.rest.v0;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.SortOrder;

/**
 * Builds the reads of the v0 API on the list use cases of the services (the ones of the v1 API): a v0 page is the
 * window of a list query, in the default order of the list.
 */
public final class V0Lists {

	private V0Lists() {
	}

	/**
	 * Gets the list query of a v0 page: no filter but the given ones, the default order, not counted.
	 *
	 * @param page the window
	 * @param criteria the filters
	 * @return the query
	 */
	public static ListQuery query(Page page, Criterion... criteria) {
		return sorted(page, List.of(), criteria);
	}

	/**
	 * Gets the list query of a v0 page, in a given order.
	 *
	 * @param page the window
	 * @param sort the order (the ID of the list always ends it)
	 * @param criteria the filters
	 * @return the query
	 */
	public static ListQuery sorted(Page page, List<SortOrder> sort, Criterion... criteria) {
		return new ListQuery(List.of(criteria), null, sort, page, false);
	}

	/**
	 * Runs a list use case which reports a missing parent object with an error, for a v0 resource which answers an
	 * empty list instead.
	 *
	 * @param <T> the type of the items
	 * @param list the list use case
	 * @param notFound the code of the error of the missing parent object
	 * @return the items, or an empty list if the parent object doesn't exist
	 */
	public static <T> List<T> orEmpty(Supplier<List<T>> list, ErrorCode notFound) {
		try {
			return list.get();
		}
		catch (NetshotException e) {
			if (e.getErrorCode() == notFound) {
				return List.of();
			}
			throw e;
		}
	}

	/**
	 * Loads groups in one query.
	 *
	 * @param groupService the group use cases
	 * @param ids the IDs of the groups (null ones are ignored)
	 * @return the groups which exist, by ID
	 */
	public static Map<Long, DeviceGroup> groups(GroupService groupService, Collection<Long> ids) {
		Set<Long> wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
		Map<Long, DeviceGroup> groups = new HashMap<>();
		if (!wanted.isEmpty()) {
			groupService.list(ListQuery.byIds(wanted)).items().forEach(group -> groups.put(group.id(), group));
		}
		return groups;
	}

	/**
	 * Loads a group which may be missing (deleted meanwhile).
	 *
	 * @param groupService the group use cases
	 * @param groupId the ID of the group, or null
	 * @return the group, or null for no group (or a group which doesn't exist)
	 */
	public static DeviceGroup group(GroupService groupService, Long groupId) {
		return groupId == null ? null : groups(groupService, List.of(groupId)).get(groupId);
	}
}
