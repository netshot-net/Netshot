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
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_GROUP_CACHED_DEVICES;
import static net.netshot.netshot.platform.database.schema.Tables.EXEMPTION;
import static net.netshot.netshot.platform.database.schema.Tables.POLICY;
import static net.netshot.netshot.platform.database.schema.Tables.POLICY_TARGET_GROUPS;
import static net.netshot.netshot.platform.database.schema.Tables.RULE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;

import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.inventory.compliance.CheckResult.ResultOption;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.QueryMapping;
import net.netshot.netshot.platform.database.QueryMapping.Option;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.SortOrder;
import org.jooq.impl.DSL;

/**
 * The queries on the compliance rules, their exemptions and their check results. The
 * {@link DSLContext} is always the first parameter: the caller (the service) owns the transaction.
 */
public class RuleRepository {

	/** The types (discriminator) of the rules. */
	static final String TEXT = "TEXT";
	static final String JAVASCRIPT = "JAVASCRIPT";
	static final String PYTHON = "PYTHON";

	/** The fields the rule lists can be filtered and sorted on. */
	static final QueryMapping LIST = QueryMapping.builder(RULE.ID)
		.string("name", RULE.NAME, Option.SORTABLE, Option.SEARCHABLE)
		.enumeration("type", RULE.TYPE, List.of(TEXT, JAVASCRIPT, PYTHON), Option.SORTABLE)
		.bool("enabled", RULE.ENABLED, Option.SORTABLE)
		.integer("policy", RULE.POLICY)
		.string("field", RULE.FIELD, Option.SORTABLE)
		.string("deviceDriver", RULE.DEVICE_DRIVER, Option.SORTABLE)
		.string("text", RULE.TEXT, Option.SEARCHABLE)
		.defaultSort(SortOrder.asc("name"))
		.build();

	/** The fields the lists of exemptions can be filtered and sorted on (the ID is the one of the device). */
	static final QueryMapping EXEMPTION_LIST = QueryMapping.builder(EXEMPTION.DEVICE)
		.integer("rule", EXEMPTION.RULE)
		.integer("device", EXEMPTION.DEVICE)
		.date("expirationDate", EXEMPTION.EXPIRATION_DATE, Option.SORTABLE)
		.defaultSort(SortOrder.asc("id"))
		.build();

	/** The fields the lists of check results of a device can be filtered and sorted on (the ID is the one of the rule). */
	static final QueryMapping RESULT_LIST = QueryMapping.builder(RULE.ID)
		.integer("rule", RULE.ID)
		.string("ruleName", RULE.NAME, Option.SORTABLE, Option.SEARCHABLE)
		.integer("policy", POLICY.ID, Option.SORTABLE)
		.string("policyName", POLICY.NAME, Option.SORTABLE, Option.SEARCHABLE)
		.enumeration("result", CHECK_RESULT.RESULT, ResultOption.class, Option.SORTABLE)
		.date("checkDate", CHECK_RESULT.CHECK_DATE, Option.SORTABLE)
		.date("exemptionExpirationDate", EXEMPTION.EXPIRATION_DATE, Option.SORTABLE)
		.defaultSort(SortOrder.asc("policy"), SortOrder.asc("id"))
		.build();

	private static Rule toRule(Record r) {
		String type = r.get(RULE.TYPE);
		long id = r.get(RULE.ID);
		String name = r.get(RULE.NAME);
		boolean enabled = r.get(RULE.ENABLED);
		long policyId = r.get(RULE.POLICY);
		return switch (type) {
			case TEXT -> new TextRule(id, name, enabled, policyId, r.get(RULE.FIELD), r.get(RULE.CONTEXT), r.get(RULE.TEXT),
				r.get(RULE.REG_EXP), r.get(RULE.INVERT), r.get(RULE.ANY_BLOCK), r.get(RULE.MATCH_ALL), r.get(RULE.NORMALIZE),
				r.get(RULE.DEVICE_DRIVER));
			case JAVASCRIPT -> new JavaScriptRule(id, name, enabled, policyId, r.get(RULE.SCRIPT));
			case PYTHON -> new PythonRule(id, name, enabled, policyId, r.get(RULE.SCRIPT));
			default -> throw new IllegalStateException("Unknown type of rule '%s'".formatted(type));
		};
	}

	/**
	 * Gets the name of the type of rule stored in the database, as the API publishes it.
	 *
	 * @param type the stored type
	 * @return the type name
	 */
	static String typeName(String type) {
		return switch (type) {
			case TEXT -> TextRule.class.getSimpleName();
			case JAVASCRIPT -> JavaScriptRule.class.getSimpleName();
			case PYTHON -> PythonRule.class.getSimpleName();
			default -> type;
		};
	}

	private static List<Rule> query(DSLContext dsl, Condition condition, Page page) {
		return page.apply(dsl.select(RULE.fields()).from(RULE).where(condition).orderBy(RULE.ID)).fetch(RuleRepository::toRule);
	}

	/**
	 * Lists all the rules, with minimal details, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the rule summaries
	 */
	public List<RuleSummary> findSummaries(DSLContext dsl, Page page) {
		return page.apply(dsl.select(RULE.ID, RULE.NAME, RULE.TYPE, RULE.ENABLED, RULE.POLICY).from(RULE).orderBy(RULE.ID))
			.fetch(r -> new RuleSummary(r.value1(), r.value2(), typeName(r.value3()), r.value4(), r.value5()));
	}

	/**
	 * Lists the rules matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query
	 * @return the rules of the window of the query
	 */
	public List<Rule> findAll(DSLContext dsl, ListQuery query) {
		return query.page().apply(dsl.select(RULE.fields()).from(RULE)
			.where(LIST.condition(query))
			.orderBy(LIST.orderBy(query)))
			.fetch(RuleRepository::toRule);
	}

	/**
	 * Counts the rules matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query (its window is ignored)
	 * @return the number of rules
	 */
	public long count(DSLContext dsl, ListQuery query) {
		return dsl.fetchCount(RULE, LIST.condition(query));
	}

	/**
	 * Lists the exemptions matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query (it must be restricted to a rule)
	 * @return the exemptions of the window of the query
	 */
	public List<Exemption> findExemptions(DSLContext dsl, ListQuery query) {
		return query.page().apply(dsl.select(EXEMPTION.fields()).from(EXEMPTION)
			.where(EXEMPTION_LIST.condition(query))
			.orderBy(EXEMPTION_LIST.orderBy(query)))
			.fetch(RuleRepository::toExemption);
	}

	/**
	 * Counts the exemptions matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query (its window is ignored)
	 * @return the number of exemptions
	 */
	public long countExemptions(DSLContext dsl, ListQuery query) {
		return dsl.fetchCount(EXEMPTION, EXEMPTION_LIST.condition(query));
	}

	private static Condition appliesTo(long deviceId) {
		return DSL.exists(DSL.selectOne().from(POLICY_TARGET_GROUPS)
			.join(DEVICE_GROUP_CACHED_DEVICES).on(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(POLICY_TARGET_GROUPS.TARGET_GROUPS))
			.where(POLICY_TARGET_GROUPS.POLICY.eq(POLICY.ID))
			.and(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(deviceId)));
	}

	private static org.jooq.Table<?> resultsOf(long deviceId) {
		return RULE
			.join(POLICY).on(POLICY.ID.eq(RULE.POLICY))
			.leftJoin(CHECK_RESULT).on(CHECK_RESULT.RULE.eq(RULE.ID).and(CHECK_RESULT.DEVICE.eq(deviceId)))
			.leftJoin(EXEMPTION).on(EXEMPTION.RULE.eq(RULE.ID).and(EXEMPTION.DEVICE.eq(deviceId)));
	}

	/**
	 * Lists the compliance state of a device matching a query: the rules of the policies applying to it (through
	 * its groups), with their last result and exemption.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the ID of the device
	 * @param query the query
	 * @return the results of the window of the query
	 */
	public List<DeviceCheckResult> findDeviceResults(DSLContext dsl, long deviceId, ListQuery query) {
		return query.page().apply(dsl.select(RULE.ID, POLICY.ID, CHECK_RESULT.RESULT, CHECK_RESULT.COMMENT, CHECK_RESULT.CHECK_DATE,
					EXEMPTION.EXPIRATION_DATE)
				.from(resultsOf(deviceId))
				.where(appliesTo(deviceId)).and(RESULT_LIST.condition(query))
				.orderBy(RESULT_LIST.orderBy(query)))
			.fetch(r -> new DeviceCheckResult(r.value1(), r.value2(), r.value3() == null ? null : ResultOption.valueOf(r.value3()),
				r.value4(), r.value5(), r.value6()));
	}

	/**
	 * Counts the compliance state of a device matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the ID of the device
	 * @param query the query (its window is ignored)
	 * @return the number of results
	 */
	public long countDeviceResults(DSLContext dsl, long deviceId, ListQuery query) {
		return dsl.fetchCount(dsl.selectOne().from(resultsOf(deviceId)).where(appliesTo(deviceId)).and(RESULT_LIST.condition(query)));
	}

	/**
	 * Lists the rules of a policy, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param policyId the ID of the policy
	 * @param page the window on the result
	 * @return the rules
	 */
	public List<Rule> findByPolicy(DSLContext dsl, long policyId, Page page) {
		return query(dsl, RULE.POLICY.eq(policyId), page);
	}

	/**
	 * Gets a rule of a policy.
	 *
	 * @param dsl the jOOQ context
	 * @param policyId the ID of the policy
	 * @param id the ID of the rule
	 * @return the rule, or null if the policy has no such rule
	 */
	public Rule findOfPolicy(DSLContext dsl, long policyId, long id) {
		List<Rule> rules = query(dsl, RULE.POLICY.eq(policyId).and(RULE.ID.eq(id)), Page.ALL);
		return rules.isEmpty() ? null : rules.get(0);
	}

	/**
	 * Gets a rule of a policy by name.
	 *
	 * @param dsl the jOOQ context
	 * @param policyId the ID of the policy
	 * @param name the name of the rule
	 * @return the rule, or null if the policy has no such rule
	 */
	public Rule findOfPolicyByName(DSLContext dsl, long policyId, String name) {
		List<Rule> rules = query(dsl, RULE.POLICY.eq(policyId).and(RULE.NAME.eq(name)), Page.ALL);
		return rules.isEmpty() ? null : rules.get(0);
	}

	/**
	 * Gets a rule.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID of the rule
	 * @return the rule, or null if there is no such rule
	 */
	public Rule find(DSLContext dsl, long id) {
		List<Rule> rules = query(dsl, RULE.ID.eq(id), Page.ALL);
		return rules.isEmpty() ? null : rules.get(0);
	}

	/**
	 * Stores a new rule.
	 *
	 * @param dsl the jOOQ context
	 * @param rule the rule (its ID is ignored)
	 * @return the ID of the new rule
	 */
	public long add(DSLContext dsl, Rule rule) {
		var insert = dsl.insertInto(RULE)
			.set(RULE.NAME, rule.name())
			.set(RULE.ENABLED, rule.enabled())
			.set(RULE.POLICY, rule.policyId());
		var full = switch (rule) {
			case TextRule t -> insert.set(RULE.TYPE, TEXT)
				.set(RULE.FIELD, t.field())
				.set(RULE.CONTEXT, t.context())
				.set(RULE.TEXT, t.text())
				.set(RULE.REG_EXP, t.regExp())
				.set(RULE.INVERT, t.invert())
				.set(RULE.ANY_BLOCK, t.anyBlock())
				.set(RULE.MATCH_ALL, t.matchAll())
				.set(RULE.NORMALIZE, t.normalize())
				.set(RULE.DEVICE_DRIVER, t.deviceDriver());
			case JavaScriptRule j -> insert.set(RULE.TYPE, JAVASCRIPT).set(RULE.SCRIPT, j.script());
			case PythonRule p -> insert.set(RULE.TYPE, PYTHON).set(RULE.SCRIPT, p.script());
		};
		return full.returning(RULE.ID).fetchOne(RULE.ID);
	}

	/**
	 * Saves an existing rule (its type and policy are not changed).
	 *
	 * @param dsl the jOOQ context
	 * @param rule the rule
	 * @return true if the rule was found and saved
	 */
	public boolean save(DSLContext dsl, Rule rule) {
		var update = dsl.update(RULE)
			.set(RULE.NAME, rule.name())
			.set(RULE.ENABLED, rule.enabled());
		var full = switch (rule) {
			case TextRule t -> update
				.set(RULE.FIELD, t.field())
				.set(RULE.CONTEXT, t.context())
				.set(RULE.TEXT, t.text())
				.set(RULE.REG_EXP, t.regExp())
				.set(RULE.INVERT, t.invert())
				.set(RULE.ANY_BLOCK, t.anyBlock())
				.set(RULE.MATCH_ALL, t.matchAll())
				.set(RULE.NORMALIZE, t.normalize())
				.set(RULE.DEVICE_DRIVER, t.deviceDriver());
			case JavaScriptRule j -> update.set(RULE.SCRIPT, j.script());
			case PythonRule p -> update.set(RULE.SCRIPT, p.script());
		};
		return full.where(RULE.ID.eq(rule.id())).execute() > 0;
	}

	/**
	 * Removes a rule (with its exemptions and check results).
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID of the rule
	 * @return true if the rule was found and removed
	 */
	public boolean remove(DSLContext dsl, long id) {
		return dsl.deleteFrom(RULE).where(RULE.ID.eq(id)).execute() > 0;
	}

	// ------------------------------------------------------------------
	// Exemptions
	// ------------------------------------------------------------------

	private static Exemption toExemption(Record r) {
		return new Exemption(r.get(EXEMPTION.RULE), r.get(EXEMPTION.DEVICE), r.get(EXEMPTION.EXPIRATION_DATE));
	}

	/**
	 * Lists the exemptions of a rule.
	 *
	 * @param dsl the jOOQ context
	 * @param ruleId the ID of the rule
	 * @return the exemptions
	 */
	public List<Exemption> findExemptions(DSLContext dsl, long ruleId) {
		return dsl.select(EXEMPTION.fields()).from(EXEMPTION).where(EXEMPTION.RULE.eq(ruleId)).fetch(RuleRepository::toExemption);
	}

	/**
	 * Lists the exemptions of a device.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the ID of the device
	 * @return the exemptions
	 */
	public List<Exemption> findExemptionsOfDevice(DSLContext dsl, long deviceId) {
		return dsl.select(EXEMPTION.fields()).from(EXEMPTION).where(EXEMPTION.DEVICE.eq(deviceId)).fetch(RuleRepository::toExemption);
	}

	/**
	 * Replaces the exemptions of a rule.
	 *
	 * @param dsl the jOOQ context
	 * @param ruleId the ID of the rule
	 * @param expirations the expiration dates, by device ID
	 */
	public void replaceExemptions(DSLContext dsl, long ruleId, Map<Long, Instant> expirations) {
		dsl.deleteFrom(EXEMPTION).where(EXEMPTION.RULE.eq(ruleId)).execute();
		for (Map.Entry<Long, Instant> expiration : expirations.entrySet()) {
			dsl.insertInto(EXEMPTION)
				.set(EXEMPTION.RULE, ruleId)
				.set(EXEMPTION.DEVICE, expiration.getKey())
				.set(EXEMPTION.EXPIRATION_DATE, expiration.getValue())
				.execute();
		}
	}

	// ------------------------------------------------------------------
	// Check results
	// ------------------------------------------------------------------

	/**
	 * Deletes the check results of a device.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the ID of the device
	 */
	public void deleteCheckResultsOfDevice(DSLContext dsl, long deviceId) {
		dsl.deleteFrom(CHECK_RESULT).where(CHECK_RESULT.DEVICE.eq(deviceId)).execute();
	}

	/**
	 * Stores check results (replacing the existing results of the same rules and devices).
	 *
	 * @param dsl the jOOQ context
	 * @param results the results
	 */
	public void saveCheckResults(DSLContext dsl, Collection<CheckResult> results) {
		for (CheckResult result : results) {
			dsl.insertInto(CHECK_RESULT)
				.set(CHECK_RESULT.RULE, result.ruleId())
				.set(CHECK_RESULT.DEVICE, result.deviceId())
				.set(CHECK_RESULT.CHECK_DATE, result.checkDate())
				.set(CHECK_RESULT.COMMENT, result.comment())
				.set(CHECK_RESULT.RESULT, result.result() == null ? null : result.result().name())
				.onConflict(CHECK_RESULT.RULE, CHECK_RESULT.DEVICE)
				.doUpdate()
				.set(CHECK_RESULT.CHECK_DATE, result.checkDate())
				.set(CHECK_RESULT.COMMENT, result.comment())
				.set(CHECK_RESULT.RESULT, result.result() == null ? null : result.result().name())
				.execute();
		}
	}

	// ------------------------------------------------------------------
	// Devices
	// ------------------------------------------------------------------

	/**
	 * Tells whether a device exists.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the ID of the device
	 * @return true if the device exists
	 */
	public boolean deviceExists(DSLContext dsl, long deviceId) {
		return dsl.fetchExists(DEVICE, DEVICE.ID.eq(deviceId));
	}

	/**
	 * Lists the devices exempted from a rule, by device ID.
	 *
	 * @param dsl the jOOQ context
	 * @param ruleId the ID of the rule
	 * @param page the window on the result
	 * @return the exempted devices
	 */
	public List<ExemptedDevice> findExemptedDevices(DSLContext dsl, long ruleId, Page page) {
		List<SelectField<?>> fields = new ArrayList<>(DeviceSummary.FIELDS);
		fields.add(EXEMPTION.EXPIRATION_DATE);
		return page.apply(dsl.select(fields).from(EXEMPTION).join(DEVICE).on(DEVICE.ID.eq(EXEMPTION.DEVICE))
				.where(EXEMPTION.RULE.eq(ruleId)).orderBy(DEVICE.ID))
			.fetch(r -> new ExemptedDevice(DeviceSummary.fromRecord(r), toDate(r.get(EXEMPTION.EXPIRATION_DATE))));
	}

	private static Date toDate(Instant instant) {
		return instant == null ? null : Date.from(instant);
	}

	/**
	 * Lists the compliance state of a device: the rules of the policies applying to it (through
	 * its groups), with their last result and exemption.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the ID of the device
	 * @param page the window on the result
	 * @return the rule results, by policy and rule ID
	 */
	public List<DeviceRuleResult> findDeviceResults(DSLContext dsl, long deviceId, Page page) {
		return page.apply(dsl.select(RULE.ID, RULE.NAME, POLICY.ID, POLICY.NAME, CHECK_RESULT.RESULT, CHECK_RESULT.COMMENT,
					CHECK_RESULT.CHECK_DATE, EXEMPTION.EXPIRATION_DATE)
				.from(RULE)
				.join(POLICY).on(POLICY.ID.eq(RULE.POLICY))
				.join(POLICY_TARGET_GROUPS).on(POLICY_TARGET_GROUPS.POLICY.eq(POLICY.ID))
				.join(DEVICE_GROUP_CACHED_DEVICES).on(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(POLICY_TARGET_GROUPS.TARGET_GROUPS)
					.and(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(deviceId)))
				.leftJoin(CHECK_RESULT).on(CHECK_RESULT.RULE.eq(RULE.ID).and(CHECK_RESULT.DEVICE.eq(deviceId)))
				.leftJoin(EXEMPTION).on(EXEMPTION.RULE.eq(RULE.ID).and(EXEMPTION.DEVICE.eq(deviceId)))
				.orderBy(POLICY.ID, RULE.ID))
			.fetch(r -> new DeviceRuleResult(r.value1(), r.value2(), r.value3(), r.value4(),
				r.value5() == null ? null : ResultOption.valueOf(r.value5()), r.value6(), toDate(r.value7()), toDate(r.value8())));
	}
}
