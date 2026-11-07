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
package net.netshot.netshot.aaa.user;

import static net.netshot.netshot.platform.database.schema.Tables.USER;

import java.util.Arrays;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Record;

import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.QueryMapping;
import net.netshot.netshot.platform.database.QueryMapping.Option;
import net.netshot.netshot.platform.database.Versions;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * The queries on the users. The {@link DSLContext} is always the first parameter: the caller
 * (the service) owns the transaction.
 */
public class UserRepository {

	private static UiUser toUser(Record r) {
		String[] oldHashes = r.get(USER.OLD_HASHED_PASSWORDS);
		return new UiUser(r.get(USER.ID), r.get(USER.VERSION), r.get(USER.CHANGE_DATE), r.get(USER.USERNAME), r.get(USER.LOCAL), r.get(USER.LEVEL),
			r.get(USER.HASHED_PASSWORD), oldHashes == null ? List.of() : Arrays.asList(oldHashes),
			r.get(USER.LAST_PASSWORD_CHANGE_DATE));
	}

	/** The fields the user lists can be filtered and sorted on. */
	static final QueryMapping LIST = QueryMapping.builder(USER.ID)
		.string("username", USER.USERNAME, Option.SORTABLE, Option.SEARCHABLE)
		.bool("local", USER.LOCAL, Option.SORTABLE)
		.integer("level", USER.LEVEL, Option.SORTABLE)
		.build();

	/**
	 * Lists the users matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query
	 * @return the users of the window of the query
	 */
	public List<UiUser> findAll(DSLContext dsl, ListQuery query) {
		return query.page().apply(dsl.select(USER.fields()).from(USER)
			.where(LIST.condition(query))
			.orderBy(LIST.orderBy(query)))
			.fetch(UserRepository::toUser);
	}

	/**
	 * Counts the users matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query (its window is ignored)
	 * @return the number of users
	 */
	public long count(DSLContext dsl, ListQuery query) {
		return dsl.fetchCount(USER, LIST.condition(query));
	}

	/**
	 * Lists the users.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the users
	 */
	public List<UiUser> list(DSLContext dsl, Page page) {
		return page.apply(dsl.select(USER.fields()).from(USER).orderBy(USER.ID)).fetch(UserRepository::toUser);
	}

	/**
	 * Finds a user by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param id the user ID
	 * @return the user, or null if there is no such user
	 */
	public UiUser find(DSLContext dsl, long id) {
		return dsl.select(USER.fields()).from(USER).where(USER.ID.eq(id)).fetchOne(UserRepository::toUser);
	}

	/**
	 * Finds a user by name.
	 *
	 * @param dsl the jOOQ context
	 * @param username the user name
	 * @return the user, or null if there is no such user
	 */
	public UiUser findByUsername(DSLContext dsl, String username) {
		return dsl.select(USER.fields()).from(USER).where(USER.USERNAME.eq(username)).fetchOne(UserRepository::toUser);
	}

	/**
	 * Stores a new user.
	 *
	 * @param dsl the jOOQ context
	 * @param user the user (its ID is ignored)
	 * @return the ID of the new user
	 */
	public long add(DSLContext dsl, UiUser user) {
		return dsl.insertInto(USER)
			.set(USER.VERSION, 0)
			.set(USER.CHANGE_DATE, Versions.now())
			.set(USER.USERNAME, user.username())
			.set(USER.LOCAL, user.local())
			.set(USER.LEVEL, user.level())
			.set(USER.HASHED_PASSWORD, user.hashedPassword())
			.set(USER.OLD_HASHED_PASSWORDS, user.oldHashedPasswords().toArray(String[]::new))
			.set(USER.LAST_PASSWORD_CHANGE_DATE, user.lastPasswordChangeDate())
			.returning(USER.ID)
			.fetchOne(USER.ID);
	}

	/**
	 * Locks the row of a user until the end of the transaction (see {@link Versions#lock}).
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID of the user
	 * @return the version of the user, or null if there is no such user
	 */
	public Integer lock(DSLContext dsl, long id) {
		return Versions.lock(dsl, USER, USER.ID, USER.VERSION, id);
	}

	/**
	 * Saves an existing user (its version is incremented, its change date set).
	 *
	 * @param dsl the jOOQ context
	 * @param user the user
	 * @return true if the user was found and saved
	 */
	public boolean save(DSLContext dsl, UiUser user) {
		return dsl.update(USER)
			.set(USER.VERSION, USER.VERSION.plus(1))
			.set(USER.CHANGE_DATE, Versions.now())
			.set(USER.USERNAME, user.username())
			.set(USER.LOCAL, user.local())
			.set(USER.LEVEL, user.level())
			.set(USER.HASHED_PASSWORD, user.hashedPassword())
			.set(USER.OLD_HASHED_PASSWORDS, user.oldHashedPasswords().toArray(String[]::new))
			.set(USER.LAST_PASSWORD_CHANGE_DATE, user.lastPasswordChangeDate())
			.where(USER.ID.eq(user.id()))
			.execute() > 0;
	}

	/**
	 * Removes a user.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID of the user
	 * @return true if the user was found and removed
	 */
	public boolean remove(DSLContext dsl, long id) {
		return dsl.deleteFrom(USER).where(USER.ID.eq(id)).execute() > 0;
	}
}
