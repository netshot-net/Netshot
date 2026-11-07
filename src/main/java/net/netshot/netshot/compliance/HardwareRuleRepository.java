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
package net.netshot.netshot.compliance;

import static net.netshot.netshot.platform.database.schema.Tables.HARDWARE_RULE;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Record;

import net.netshot.netshot.platform.database.QueryMapping;
import net.netshot.netshot.platform.database.QueryMapping.Option;
import net.netshot.netshot.platform.database.Versions;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.SortOrder;

/**
 * The queries on the hardware compliance rules. The {@link DSLContext} is always the first
 * parameter: the caller (the service) owns the transaction.
 */
public class HardwareRuleRepository {

	/** The fields the hardware rule lists can be filtered and sorted on. */
	static final QueryMapping LIST = QueryMapping.builder(HARDWARE_RULE.ID)
		.integer("group", HARDWARE_RULE.TARGET_GROUP)
		.string("driver", HARDWARE_RULE.DRIVER, Option.SORTABLE)
		.string("family", HARDWARE_RULE.FAMILY, Option.SORTABLE, Option.SEARCHABLE)
		.bool("familyRegExp", HARDWARE_RULE.FAMILY_REG_EXP)
		.string("partNumber", HARDWARE_RULE.PART_NUMBER, Option.SORTABLE, Option.SEARCHABLE)
		.bool("partNumberRegExp", HARDWARE_RULE.PART_NUMBER_REG_EXP)
		.date("endOfSale", HARDWARE_RULE.END_OF_SALE, Option.SORTABLE)
		.date("endOfLife", HARDWARE_RULE.END_OF_LIFE, Option.SORTABLE)
		.defaultSort(SortOrder.asc("id"))
		.build();

	/**
	 * Lists the rules matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query
	 * @return the rules of the window of the query
	 */
	public List<HardwareRule> findAll(DSLContext dsl, ListQuery query) {
		return query.page().apply(dsl.select(HARDWARE_RULE.fields()).from(HARDWARE_RULE)
			.where(LIST.condition(query))
			.orderBy(LIST.orderBy(query)))
			.fetch(HardwareRuleRepository::toRule);
	}

	/**
	 * Counts the rules matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query (its window is ignored)
	 * @return the number of rules
	 */
	public long count(DSLContext dsl, ListQuery query) {
		return dsl.fetchCount(HARDWARE_RULE, LIST.condition(query));
	}

	private static HardwareRule toRule(Record r) {
		return new HardwareRule(r.get(HARDWARE_RULE.ID), r.get(HARDWARE_RULE.VERSION), r.get(HARDWARE_RULE.CHANGE_DATE), r.get(HARDWARE_RULE.DRIVER), r.get(HARDWARE_RULE.TARGET_GROUP),
			r.get(HARDWARE_RULE.FAMILY), r.get(HARDWARE_RULE.FAMILY_REG_EXP), r.get(HARDWARE_RULE.PART_NUMBER),
			r.get(HARDWARE_RULE.PART_NUMBER_REG_EXP), r.get(HARDWARE_RULE.END_OF_SALE), r.get(HARDWARE_RULE.END_OF_LIFE));
	}

	/**
	 * Lists the rules, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @return the rules
	 */
	public List<HardwareRule> findAll(DSLContext dsl) {
		return dsl.select(HARDWARE_RULE.fields()).from(HARDWARE_RULE).orderBy(HARDWARE_RULE.ID).fetch(HardwareRuleRepository::toRule);
	}

	/**
	 * Gets a rule.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @return the rule, or null if there is no such rule
	 */
	public HardwareRule find(DSLContext dsl, long id) {
		return dsl.select(HARDWARE_RULE.fields()).from(HARDWARE_RULE).where(HARDWARE_RULE.ID.eq(id)).fetchOne(HardwareRuleRepository::toRule);
	}

	/**
	 * Stores a new rule.
	 *
	 * @param dsl the jOOQ context
	 * @param rule the rule (its ID is ignored)
	 * @return the ID of the new rule
	 */
	public long add(DSLContext dsl, HardwareRule rule) {
		return dsl.insertInto(HARDWARE_RULE)
			.set(HARDWARE_RULE.VERSION, 0)
			.set(HARDWARE_RULE.CHANGE_DATE, Versions.now())
			.set(HARDWARE_RULE.DRIVER, rule.driver())
			.set(HARDWARE_RULE.TARGET_GROUP, rule.targetGroupId())
			.set(HARDWARE_RULE.FAMILY, rule.family())
			.set(HARDWARE_RULE.FAMILY_REG_EXP, rule.familyRegExp())
			.set(HARDWARE_RULE.PART_NUMBER, rule.partNumber())
			.set(HARDWARE_RULE.PART_NUMBER_REG_EXP, rule.partNumberRegExp())
			.set(HARDWARE_RULE.END_OF_SALE, rule.endOfSale())
			.set(HARDWARE_RULE.END_OF_LIFE, rule.endOfLife())
			.returning(HARDWARE_RULE.ID)
			.fetchOne(HARDWARE_RULE.ID);
	}

	/**
	 * Locks the row of a rule until the end of the transaction (see {@link Versions#lock}).
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @return the version of the rule, or null if there is no such rule
	 */
	public Integer lock(DSLContext dsl, long id) {
		return Versions.lock(dsl, HARDWARE_RULE, HARDWARE_RULE.ID, HARDWARE_RULE.VERSION, id);
	}

	/**
	 * Saves an existing rule (its version is incremented, its change date set).
	 *
	 * @param dsl the jOOQ context
	 * @param rule the rule
	 * @return true if the rule was found and saved
	 */
	public boolean save(DSLContext dsl, HardwareRule rule) {
		return dsl.update(HARDWARE_RULE)
			.set(HARDWARE_RULE.VERSION, HARDWARE_RULE.VERSION.plus(1))
			.set(HARDWARE_RULE.CHANGE_DATE, Versions.now())
			.set(HARDWARE_RULE.DRIVER, rule.driver())
			.set(HARDWARE_RULE.TARGET_GROUP, rule.targetGroupId())
			.set(HARDWARE_RULE.FAMILY, rule.family())
			.set(HARDWARE_RULE.FAMILY_REG_EXP, rule.familyRegExp())
			.set(HARDWARE_RULE.PART_NUMBER, rule.partNumber())
			.set(HARDWARE_RULE.PART_NUMBER_REG_EXP, rule.partNumberRegExp())
			.set(HARDWARE_RULE.END_OF_SALE, rule.endOfSale())
			.set(HARDWARE_RULE.END_OF_LIFE, rule.endOfLife())
			.where(HARDWARE_RULE.ID.eq(rule.id()))
			.execute() > 0;
	}

	/**
	 * Removes a rule.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @return true if the rule was found and removed
	 */
	public boolean remove(DSLContext dsl, long id) {
		return dsl.deleteFrom(HARDWARE_RULE).where(HARDWARE_RULE.ID.eq(id)).execute() > 0;
	}
}
