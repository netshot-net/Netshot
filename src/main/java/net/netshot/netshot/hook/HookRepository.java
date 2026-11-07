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
package net.netshot.netshot.hook;

import static net.netshot.netshot.platform.database.schema.Tables.HOOK;
import static net.netshot.netshot.platform.database.schema.Tables.HOOK_TRIGGER;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectLimitStep;
import org.jooq.impl.DSL;

import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.QueryMapping;
import net.netshot.netshot.platform.database.QueryMapping.Option;
import net.netshot.netshot.platform.database.Versions;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * The queries on hooks. The jOOQ context is always the first parameter: the caller
 * (the service) owns the transaction.
 */
public class HookRepository {

	/** The type of a Web hook in the hook table. */
	static final String WEB = "WEB";

	/** The triggers of the hook of the current row. */
	private static final Field<Set<HookTrigger>> TRIGGERS = multiset(
		select(HOOK_TRIGGER.TYPE, HOOK_TRIGGER.ITEM).from(HOOK_TRIGGER).where(HOOK_TRIGGER.HOOK.eq(HOOK.ID)))
		.convertFrom(r -> (Set<HookTrigger>) new HashSet<>(r.map(t -> new HookTrigger(HookTrigger.TriggerType.valueOf(t.value1()), t.value2()))))
		.as("triggers");

	private static SelectLimitStep<Record> query(DSLContext dsl, Condition condition) {
		return dsl.select(HOOK.fields()).select(TRIGGERS).from(HOOK).where(condition).orderBy(HOOK.ID);
	}

	private static Hook toHook(Record r) {
		String type = r.get(HOOK.TYPE);
		if (WEB.equals(type)) {
			return new WebHook(r.get(HOOK.ID), r.get(HOOK.VERSION), r.get(HOOK.CHANGE_DATE), r.get(HOOK.NAME), r.get(HOOK.ENABLED), r.get(TRIGGERS),
				r.get(HOOK.ACTION) == null ? null : WebHook.Action.valueOf(r.get(HOOK.ACTION)),
				r.get(HOOK.URL),
				HttpsCaTrustMode.valueOf(r.get(HOOK.HTTPS_CA_TRUST_MODE)),
				r.get(HOOK.HTTPS_CUSTOM_CA_CERTIFICATE));
		}
		throw new IllegalStateException("Unknown type of hook '%s'".formatted(type));
	}

	/** The fields the hook lists can be filtered and sorted on. */
	static final QueryMapping LIST = QueryMapping.builder(HOOK.ID)
		.string("name", HOOK.NAME, Option.SORTABLE, Option.SEARCHABLE)
		.string("type", HOOK.TYPE, Option.SORTABLE)
		.bool("enabled", HOOK.ENABLED, Option.SORTABLE)
		.string("url", HOOK.URL, Option.SEARCHABLE)
		.build();

	/**
	 * Lists the hooks matching a query, with their triggers.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query
	 * @return the hooks of the window of the query
	 */
	public List<Hook> findAll(DSLContext dsl, ListQuery query) {
		return query.page().apply(dsl.select(HOOK.fields()).select(TRIGGERS).from(HOOK)
			.where(LIST.condition(query))
			.orderBy(LIST.orderBy(query)))
			.fetch(HookRepository::toHook);
	}

	/**
	 * Counts the hooks matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query (its window is ignored)
	 * @return the number of hooks
	 */
	public long count(DSLContext dsl, ListQuery query) {
		return dsl.fetchCount(HOOK, LIST.condition(query));
	}

	/**
	 * Lists the hooks with their triggers, ordered by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the hooks
	 */
	public List<Hook> findAll(DSLContext dsl, Page page) {
		List<Hook> hooks = page.apply(query(dsl, DSL.noCondition())).fetch(HookRepository::toHook);
		if (page.isRestricted()) {
			// Quirk kept: the former (Hibernate) query paginated in memory and answered a sub list,
			// which XML publishes as <SubList> instead of <ArrayList>
			return hooks.subList(0, hooks.size());
		}
		return hooks;
	}

	/**
	 * Lists the enabled hooks with a given trigger.
	 *
	 * @param dsl the jOOQ context
	 * @param type the type of trigger
	 * @param item the item of the trigger
	 * @return the hooks
	 */
	public List<Hook> findEnabledByTrigger(DSLContext dsl, HookTrigger.TriggerType type, String item) {
		return query(dsl, HOOK.ENABLED.isTrue().andExists(selectOne().from(HOOK_TRIGGER)
				.where(HOOK_TRIGGER.HOOK.eq(HOOK.ID), HOOK_TRIGGER.TYPE.eq(type.name()), HOOK_TRIGGER.ITEM.eq(item))))
			.fetch(HookRepository::toHook);
	}

	/**
	 * Gets a hook.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID of the hook
	 * @return the hook, or null if there is no such hook
	 */
	public Hook find(DSLContext dsl, long id) {
		return query(dsl, HOOK.ID.eq(id)).fetchOne(HookRepository::toHook);
	}

	/**
	 * Stores a new hook and its triggers.
	 *
	 * @param dsl the jOOQ context
	 * @param hook the hook (its ID is ignored)
	 * @return the ID of the new hook
	 */
	public long add(DSLContext dsl, Hook hook) {
		long id = switch (hook) {
			case WebHook web -> dsl.insertInto(HOOK)
				.set(HOOK.VERSION, 0)
				.set(HOOK.CHANGE_DATE, Versions.now())
				.set(HOOK.TYPE, WEB)
				.set(HOOK.NAME, web.name())
				.set(HOOK.ENABLED, web.enabled())
				.set(HOOK.ACTION, web.action() == null ? null : web.action().name())
				.set(HOOK.URL, web.url())
				.set(HOOK.HTTPS_CA_TRUST_MODE, web.httpsCaTrustMode().name())
				.set(HOOK.HTTPS_CUSTOM_CA_CERTIFICATE, web.httpsCustomCaCertificate())
				.returning(HOOK.ID)
				.fetchOne(HOOK.ID);
		};
		insertTriggers(dsl, id, hook.triggers());
		return id;
	}

	/**
	 * Locks the row of a hook until the end of the transaction (see {@link Versions#lock}).
	 *
	 * @param dsl the jOOQ context
	 * @param id the hook ID
	 * @return the version of the hook, or null if there is no such hook
	 */
	public Integer lock(DSLContext dsl, long id) {
		return Versions.lock(dsl, HOOK, HOOK.ID, HOOK.VERSION, id);
	}

	/**
	 * Saves an existing hook and replaces its triggers (its version is incremented, its change date set).
	 *
	 * @param dsl the jOOQ context
	 * @param hook the hook
	 * @return false if there is no such hook (of the same type)
	 */
	public boolean save(DSLContext dsl, Hook hook) {
		int updated = switch (hook) {
			case WebHook web -> dsl.update(HOOK)
				.set(HOOK.VERSION, HOOK.VERSION.plus(1))
				.set(HOOK.CHANGE_DATE, Versions.now())
				.set(HOOK.NAME, web.name())
				.set(HOOK.ENABLED, web.enabled())
				.set(HOOK.ACTION, web.action() == null ? null : web.action().name())
				.set(HOOK.URL, web.url())
				.set(HOOK.HTTPS_CA_TRUST_MODE, web.httpsCaTrustMode().name())
				.set(HOOK.HTTPS_CUSTOM_CA_CERTIFICATE, web.httpsCustomCaCertificate())
				.where(HOOK.ID.eq(web.id()), HOOK.TYPE.eq(WEB))
				.execute();
		};
		if (updated == 0) {
			return false;
		}
		dsl.deleteFrom(HOOK_TRIGGER).where(HOOK_TRIGGER.HOOK.eq(hook.id())).execute();
		insertTriggers(dsl, hook.id(), hook.triggers());
		return true;
	}

	private static void insertTriggers(DSLContext dsl, long hookId, Set<HookTrigger> triggers) {
		if (triggers == null || triggers.isEmpty()) {
			return;
		}
		var insert = dsl.insertInto(HOOK_TRIGGER, HOOK_TRIGGER.HOOK, HOOK_TRIGGER.TYPE, HOOK_TRIGGER.ITEM);
		for (HookTrigger trigger : triggers) {
			insert = insert.values(hookId, trigger.type() == null ? null : trigger.type().name(), trigger.item());
		}
		insert.execute();
	}

	/**
	 * Deletes a hook (and its triggers).
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID of the hook
	 * @return false if there is no such hook
	 */
	public boolean remove(DSLContext dsl, long id) {
		return dsl.deleteFrom(HOOK).where(HOOK.ID.eq(id)).execute() > 0;
	}
}
