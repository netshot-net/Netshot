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
package net.netshot.netshot.userscript;

import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_JS_SCRIPT;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Record;

import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.QueryMapping;
import net.netshot.netshot.platform.database.QueryMapping.Option;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * The queries on the device scripts. The {@link DSLContext} is always the first parameter: the
 * caller (the service) owns the transaction.
 */
public class ScriptRepository {

	private static DeviceJsScript toScript(Record r) {
		return new DeviceJsScript(r.get(DEVICE_JS_SCRIPT.ID), r.get(DEVICE_JS_SCRIPT.NAME), r.get(DEVICE_JS_SCRIPT.FOLDER),
			r.get(DEVICE_JS_SCRIPT.SCRIPT), r.get(DEVICE_JS_SCRIPT.DEVICE_DRIVER), r.get(DEVICE_JS_SCRIPT.AUTHOR), null);
	}

	/** The fields the script lists can be filtered and sorted on. */
	static final QueryMapping LIST = QueryMapping.builder(DEVICE_JS_SCRIPT.ID)
		.string("name", DEVICE_JS_SCRIPT.NAME, Option.SORTABLE, Option.SEARCHABLE)
		.string("folder", DEVICE_JS_SCRIPT.FOLDER, Option.SORTABLE, Option.SEARCHABLE)
		.string("deviceDriver", DEVICE_JS_SCRIPT.DEVICE_DRIVER, Option.SORTABLE)
		.string("author", DEVICE_JS_SCRIPT.AUTHOR, Option.SORTABLE, Option.SEARCHABLE)
		.build();

	/**
	 * Lists the scripts matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query
	 * @return the scripts of the window of the query
	 */
	public List<DeviceJsScript> findAll(DSLContext dsl, ListQuery query) {
		return query.page().apply(dsl.select(DEVICE_JS_SCRIPT.fields()).from(DEVICE_JS_SCRIPT)
			.where(LIST.condition(query))
			.orderBy(LIST.orderBy(query)))
			.fetch(ScriptRepository::toScript);
	}

	/**
	 * Counts the scripts matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query (its window is ignored)
	 * @return the number of scripts
	 */
	public long count(DSLContext dsl, ListQuery query) {
		return dsl.fetchCount(DEVICE_JS_SCRIPT, LIST.condition(query));
	}

	/**
	 * Lists the scripts, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the scripts
	 */
	public List<DeviceJsScript> findAll(DSLContext dsl, Page page) {
		return page.apply(dsl.select(DEVICE_JS_SCRIPT.fields()).from(DEVICE_JS_SCRIPT).orderBy(DEVICE_JS_SCRIPT.ID))
			.fetch(ScriptRepository::toScript);
	}

	/**
	 * Gets a script.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @return the script, or null if there is no such script
	 */
	public DeviceJsScript find(DSLContext dsl, long id) {
		return dsl.select(DEVICE_JS_SCRIPT.fields()).from(DEVICE_JS_SCRIPT).where(DEVICE_JS_SCRIPT.ID.eq(id))
			.fetchOne(ScriptRepository::toScript);
	}

	/**
	 * Stores a new script.
	 *
	 * @param dsl the jOOQ context
	 * @param script the script (its ID is ignored)
	 * @return the ID of the new script
	 */
	public long add(DSLContext dsl, DeviceJsScript script) {
		return dsl.insertInto(DEVICE_JS_SCRIPT)
			.set(DEVICE_JS_SCRIPT.NAME, script.name())
			.set(DEVICE_JS_SCRIPT.FOLDER, script.folder())
			.set(DEVICE_JS_SCRIPT.SCRIPT, script.script())
			.set(DEVICE_JS_SCRIPT.DEVICE_DRIVER, script.deviceDriver())
			.set(DEVICE_JS_SCRIPT.AUTHOR, script.author())
			.returning(DEVICE_JS_SCRIPT.ID)
			.fetchOne(DEVICE_JS_SCRIPT.ID);
	}

	/**
	 * Removes a script.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @return true if the script was found and removed
	 */
	public boolean remove(DSLContext dsl, long id) {
		return dsl.deleteFrom(DEVICE_JS_SCRIPT).where(DEVICE_JS_SCRIPT.ID.eq(id)).execute() > 0;
	}
}
