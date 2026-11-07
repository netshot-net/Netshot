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
package net.netshot.netshot.aaa.user;

import static net.netshot.netshot.platform.database.schema.Tables.API_TOKEN;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Record;

import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.QueryMapping;
import net.netshot.netshot.platform.database.QueryMapping.Option;
import net.netshot.netshot.platform.database.Versions;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * The queries on the API tokens. The {@link DSLContext} is always the first parameter: the caller
 * (the service) owns the transaction.
 */
public class ApiTokenRepository {

	private static ApiToken toApiToken(Record r) {
		return new ApiToken(r.get(API_TOKEN.ID), r.get(API_TOKEN.DESCRIPTION), r.get(API_TOKEN.HASHED_TOKEN), r.get(API_TOKEN.LEVEL),
			r.get(API_TOKEN.CHANGE_DATE));
	}

	/** The fields the API token lists can be filtered and sorted on. */
	static final QueryMapping LIST = QueryMapping.builder(API_TOKEN.ID)
		.string("description", API_TOKEN.DESCRIPTION, Option.SORTABLE, Option.SEARCHABLE)
		.integer("level", API_TOKEN.LEVEL, Option.SORTABLE)
		.build();

	/**
	 * Lists the API tokens matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query
	 * @return the API tokens of the window of the query
	 */
	public List<ApiToken> findAll(DSLContext dsl, ListQuery query) {
		return query.page().apply(dsl.select(API_TOKEN.fields()).from(API_TOKEN)
			.where(LIST.condition(query))
			.orderBy(LIST.orderBy(query)))
			.fetch(ApiTokenRepository::toApiToken);
	}

	/**
	 * Counts the API tokens matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query (its window is ignored)
	 * @return the number of API tokens
	 */
	public long count(DSLContext dsl, ListQuery query) {
		return dsl.fetchCount(API_TOKEN, LIST.condition(query));
	}

	/**
	 * Lists the API tokens.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the API tokens
	 */
	public List<ApiToken> list(DSLContext dsl, Page page) {
		return page.apply(dsl.select(API_TOKEN.fields()).from(API_TOKEN).orderBy(API_TOKEN.ID)).fetch(ApiTokenRepository::toApiToken);
	}

	/**
	 * Finds an API token by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param id the token ID
	 * @return the API token, or null if there is no such token
	 */
	public ApiToken find(DSLContext dsl, long id) {
		return dsl.select(API_TOKEN.fields()).from(API_TOKEN).where(API_TOKEN.ID.eq(id)).fetchOne(ApiTokenRepository::toApiToken);
	}

	/**
	 * Finds an API token by the hash of its value.
	 *
	 * @param dsl the jOOQ context
	 * @param hash the hash of the token value
	 * @return the API token, or null if there is no such token
	 */
	public ApiToken findByHash(DSLContext dsl, String hash) {
		return dsl.select(API_TOKEN.fields()).from(API_TOKEN).where(API_TOKEN.HASHED_TOKEN.eq(hash)).fetchOne(ApiTokenRepository::toApiToken);
	}

	/**
	 * Tells whether an API token with the given hash already exists.
	 *
	 * @param dsl the jOOQ context
	 * @param hash the hash of the token value
	 * @return true if at least one API token has this hash
	 */
	public boolean existsByHash(DSLContext dsl, String hash) {
		return dsl.fetchExists(API_TOKEN, API_TOKEN.HASHED_TOKEN.eq(hash));
	}

	/**
	 * Stores a new API token.
	 *
	 * @param dsl the jOOQ context
	 * @param apiToken the API token (its ID is ignored)
	 * @return the ID of the new API token
	 */
	public long add(DSLContext dsl, ApiToken apiToken) {
		return dsl.insertInto(API_TOKEN)
			.set(API_TOKEN.DESCRIPTION, apiToken.description())
			.set(API_TOKEN.HASHED_TOKEN, apiToken.hashedToken())
			.set(API_TOKEN.LEVEL, apiToken.level())
			.set(API_TOKEN.CHANGE_DATE, Versions.now())
			.returning(API_TOKEN.ID)
			.fetchOne(API_TOKEN.ID);
	}

	/**
	 * Removes an API token.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID of the API token
	 * @return true if the API token was found and removed
	 */
	public boolean remove(DSLContext dsl, long id) {
		return dsl.deleteFrom(API_TOKEN).where(API_TOKEN.ID.eq(id)).execute() > 0;
	}
}
