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
package net.netshot.netshot.compliance;

import static net.netshot.netshot.platform.database.schema.Tables.CHECK_RESULT;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_GROUP_CACHED_DEVICES;
import static net.netshot.netshot.platform.database.schema.Tables.POLICY;
import static net.netshot.netshot.platform.database.schema.Tables.POLICY_TARGET_GROUPS;
import static net.netshot.netshot.platform.database.schema.Tables.RULE;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;

import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.QueryMapping;
import net.netshot.netshot.platform.database.QueryMapping.Option;
import net.netshot.netshot.platform.database.Versions;
import net.netshot.netshot.platform.query.FieldType;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.Lookup;

/**
 * The queries on the compliance policies. The {@link DSLContext} is always the first parameter:
 * the caller (the service) owns the transaction.
 */
public class PolicyRepository {

	/** The IDs of the target groups of the policy. */
	private static final Field<Set<Long>> TARGET_GROUPS = DSL.multiset(
			DSL.select(POLICY_TARGET_GROUPS.TARGET_GROUPS).from(POLICY_TARGET_GROUPS).where(POLICY_TARGET_GROUPS.POLICY.eq(POLICY.ID)))
		.convertFrom(r -> (Set<Long>) new HashSet<>(r.map(t -> t.value1())))
		.as("target_groups");

	/** The fields the policy lists can be filtered and sorted on. */
	static final QueryMapping LIST = QueryMapping.builder(POLICY.ID)
		.string("name", POLICY.NAME, Option.SORTABLE, Option.SEARCHABLE)
		.custom("targetGroup", FieldType.INTEGER, EnumSet.of(Lookup.EXACT, Lookup.N), (_, values) ->
			DSL.exists(DSL.selectOne().from(POLICY_TARGET_GROUPS)
				.where(POLICY_TARGET_GROUPS.POLICY.eq(POLICY.ID))
				.and(POLICY_TARGET_GROUPS.TARGET_GROUPS.in(values.stream().map(Long.class::cast).toList()))))
		.build();

	private static Policy toPolicy(Record r) {
		return new Policy(r.get(POLICY.ID), r.get(POLICY.VERSION), r.get(POLICY.CHANGE_DATE), r.get(POLICY.NAME), r.get(TARGET_GROUPS));
	}

	private static List<Policy> query(DSLContext dsl, Condition condition, Page page) {
		return page.apply(dsl.select(POLICY.ID, POLICY.VERSION, POLICY.CHANGE_DATE, POLICY.NAME, TARGET_GROUPS).from(POLICY).where(condition).orderBy(POLICY.ID))
			.fetch(PolicyRepository::toPolicy);
	}

	/**
	 * Lists the policies, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the policies
	 */
	public List<Policy> findAll(DSLContext dsl, Page page) {
		return query(dsl, DSL.noCondition(), page);
	}

	/**
	 * Lists the policies matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query
	 * @return the policies of the window of the query
	 */
	public List<Policy> findAll(DSLContext dsl, ListQuery query) {
		return query.page().apply(dsl.select(POLICY.ID, POLICY.VERSION, POLICY.CHANGE_DATE, POLICY.NAME, TARGET_GROUPS).from(POLICY)
			.where(LIST.condition(query))
			.orderBy(LIST.orderBy(query)))
			.fetch(PolicyRepository::toPolicy);
	}

	/**
	 * Counts the policies matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query (its window is ignored)
	 * @return the number of policies
	 */
	public long count(DSLContext dsl, ListQuery query) {
		return dsl.fetchCount(POLICY, LIST.condition(query));
	}

	/**
	 * Gets a policy.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @return the policy, or null if there is no such policy
	 */
	public Policy find(DSLContext dsl, long id) {
		List<Policy> policies = query(dsl, POLICY.ID.eq(id), Page.ALL);
		return policies.isEmpty() ? null : policies.get(0);
	}

	/**
	 * Gets a policy by name.
	 *
	 * @param dsl the jOOQ context
	 * @param name the name
	 * @return the policy, or null if there is no such policy
	 */
	public Policy findByName(DSLContext dsl, String name) {
		List<Policy> policies = query(dsl, POLICY.NAME.eq(name), Page.ALL);
		return policies.isEmpty() ? null : policies.get(0);
	}

	/**
	 * Lists the policies which apply to a device (through the groups of the device).
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the ID of the device
	 * @return the policies, by ID
	 */
	public List<Policy> findOfDevice(DSLContext dsl, long deviceId) {
		return query(dsl, POLICY.ID.in(DSL.select(POLICY_TARGET_GROUPS.POLICY).from(POLICY_TARGET_GROUPS)
			.join(DEVICE_GROUP_CACHED_DEVICES).on(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(POLICY_TARGET_GROUPS.TARGET_GROUPS))
			.where(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(deviceId))), Page.ALL);
	}

	private static void insertTargetGroups(DSLContext dsl, long policyId, Set<Long> groupIds) {
		for (Long groupId : groupIds) {
			dsl.insertInto(POLICY_TARGET_GROUPS)
				.set(POLICY_TARGET_GROUPS.POLICY, policyId)
				.set(POLICY_TARGET_GROUPS.TARGET_GROUPS, groupId)
				.execute();
		}
	}

	/**
	 * Stores a new policy and its target groups.
	 *
	 * @param dsl the jOOQ context
	 * @param policy the policy (its ID is ignored)
	 * @return the ID of the new policy
	 */
	public long add(DSLContext dsl, Policy policy) {
		long id = dsl.insertInto(POLICY).set(POLICY.VERSION, 0).set(POLICY.CHANGE_DATE, Versions.now()).set(POLICY.NAME, policy.name())
			.returning(POLICY.ID).fetchOne(POLICY.ID);
		insertTargetGroups(dsl, id, policy.targetGroupIds());
		return id;
	}

	/**
	 * Locks the row of a policy until the end of the transaction (see {@link Versions#lock}).
	 *
	 * @param dsl the jOOQ context
	 * @param id the policy ID
	 * @return the version of the policy, or null if there is no such policy
	 */
	public Integer lock(DSLContext dsl, long id) {
		return Versions.lock(dsl, POLICY, POLICY.ID, POLICY.VERSION, id);
	}

	/**
	 * Saves an existing policy and replaces its target groups (its version is incremented, its change date set).
	 *
	 * @param dsl the jOOQ context
	 * @param policy the policy
	 * @return true if the policy was found and saved
	 */
	public boolean save(DSLContext dsl, Policy policy) {
		if (dsl.update(POLICY).set(POLICY.VERSION, POLICY.VERSION.plus(1)).set(POLICY.CHANGE_DATE, Versions.now())
			.set(POLICY.NAME, policy.name()).where(POLICY.ID.eq(policy.id())).execute() == 0) {
			return false;
		}
		dsl.deleteFrom(POLICY_TARGET_GROUPS).where(POLICY_TARGET_GROUPS.POLICY.eq(policy.id())).execute();
		insertTargetGroups(dsl, policy.id(), policy.targetGroupIds());
		return true;
	}

	/**
	 * Removes a policy (with its rules, their exemptions and check results).
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @return true if the policy was found and removed
	 */
	public boolean remove(DSLContext dsl, long id) {
		return dsl.deleteFrom(POLICY).where(POLICY.ID.eq(id)).execute() > 0;
	}

	/**
	 * Deletes the check results of the rules of a policy for the devices of a group.
	 *
	 * @param dsl the jOOQ context
	 * @param policyId the ID of the policy
	 * @param groupId the ID of the group
	 */
	public void deleteCheckResults(DSLContext dsl, long policyId, long groupId) {
		dsl.deleteFrom(CHECK_RESULT)
			.where(CHECK_RESULT.RULE.in(DSL.select(RULE.ID).from(RULE).where(RULE.POLICY.eq(policyId))))
			.and(CHECK_RESULT.DEVICE.in(DSL.select(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES).from(DEVICE_GROUP_CACHED_DEVICES)
				.where(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(groupId))))
			.execute();
	}
}
