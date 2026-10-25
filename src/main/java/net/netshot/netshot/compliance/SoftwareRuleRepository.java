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

import static net.netshot.netshot.platform.database.schema.Tables.SOFTWARE_RULE;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Record;

import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.platform.database.Page;

/**
 * The queries on the software compliance rules. The {@link DSLContext} is always the first
 * parameter: the caller (the service) owns the transaction.
 */
public class SoftwareRuleRepository {

	private static SoftwareRule toRule(Record r) {
		return new SoftwareRule(r.get(SOFTWARE_RULE.ID), r.get(SOFTWARE_RULE.PRIORITY), r.get(SOFTWARE_RULE.TARGET_GROUP),
			r.get(SOFTWARE_RULE.DRIVER), r.get(SOFTWARE_RULE.FAMILY), r.get(SOFTWARE_RULE.FAMILY_REG_EXP),
			r.get(SOFTWARE_RULE.VERSION), r.get(SOFTWARE_RULE.VERSION_REG_EXP), r.get(SOFTWARE_RULE.PART_NUMBER),
			r.get(SOFTWARE_RULE.PART_NUMBER_REG_EXP),
			r.get(SOFTWARE_RULE.LEVEL) == null ? null : ConformanceLevel.valueOf(r.get(SOFTWARE_RULE.LEVEL)));
	}

	/**
	 * Lists the rules, by priority.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the rules
	 */
	public List<SoftwareRule> findAll(DSLContext dsl, Page page) {
		return page.apply(dsl.select(SOFTWARE_RULE.fields()).from(SOFTWARE_RULE).orderBy(SOFTWARE_RULE.PRIORITY.asc()))
			.fetch(SoftwareRuleRepository::toRule);
	}

	/**
	 * Gets a rule.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @return the rule, or null if there is no such rule
	 */
	public SoftwareRule find(DSLContext dsl, long id) {
		return dsl.select(SOFTWARE_RULE.fields()).from(SOFTWARE_RULE).where(SOFTWARE_RULE.ID.eq(id)).fetchOne(SoftwareRuleRepository::toRule);
	}

	/**
	 * Stores a new rule.
	 *
	 * @param dsl the jOOQ context
	 * @param rule the rule (its ID is ignored)
	 * @return the ID of the new rule
	 */
	public long add(DSLContext dsl, SoftwareRule rule) {
		return dsl.insertInto(SOFTWARE_RULE)
			.set(SOFTWARE_RULE.PRIORITY, rule.priority())
			.set(SOFTWARE_RULE.TARGET_GROUP, rule.targetGroupId())
			.set(SOFTWARE_RULE.DRIVER, rule.driver())
			.set(SOFTWARE_RULE.FAMILY, rule.family())
			.set(SOFTWARE_RULE.FAMILY_REG_EXP, rule.familyRegExp())
			.set(SOFTWARE_RULE.VERSION, rule.version())
			.set(SOFTWARE_RULE.VERSION_REG_EXP, rule.versionRegExp())
			.set(SOFTWARE_RULE.PART_NUMBER, rule.partNumber())
			.set(SOFTWARE_RULE.PART_NUMBER_REG_EXP, rule.partNumberRegExp())
			.set(SOFTWARE_RULE.LEVEL, rule.level() == null ? null : rule.level().name())
			.returning(SOFTWARE_RULE.ID)
			.fetchOne(SOFTWARE_RULE.ID);
	}

	/**
	 * Saves an existing rule.
	 *
	 * @param dsl the jOOQ context
	 * @param rule the rule
	 * @return true if the rule was found and saved
	 */
	public boolean save(DSLContext dsl, SoftwareRule rule) {
		return dsl.update(SOFTWARE_RULE)
			.set(SOFTWARE_RULE.PRIORITY, rule.priority())
			.set(SOFTWARE_RULE.TARGET_GROUP, rule.targetGroupId())
			.set(SOFTWARE_RULE.DRIVER, rule.driver())
			.set(SOFTWARE_RULE.FAMILY, rule.family())
			.set(SOFTWARE_RULE.FAMILY_REG_EXP, rule.familyRegExp())
			.set(SOFTWARE_RULE.VERSION, rule.version())
			.set(SOFTWARE_RULE.VERSION_REG_EXP, rule.versionRegExp())
			.set(SOFTWARE_RULE.PART_NUMBER, rule.partNumber())
			.set(SOFTWARE_RULE.PART_NUMBER_REG_EXP, rule.partNumberRegExp())
			.set(SOFTWARE_RULE.LEVEL, rule.level() == null ? null : rule.level().name())
			.where(SOFTWARE_RULE.ID.eq(rule.id()))
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
		return dsl.deleteFrom(SOFTWARE_RULE).where(SOFTWARE_RULE.ID.eq(id)).execute() > 0;
	}
}
