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
package net.netshot.netshot.domain;

import static net.netshot.netshot.platform.database.schema.Tables.DOMAIN;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.QueryMapping;
import net.netshot.netshot.platform.database.QueryMapping.Option;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.SortOrder;

/**
 * The queries on domains. The {@link DSLContext} is always the first parameter: the caller
 * (the service) owns the transaction.
 */
public class DomainRepository {

	/**
	 * Builds a domain from the columns of a (possibly aliased, possibly outer joined) domain table.
	 *
	 * @param r the record
	 * @param d the domain table the record was read from
	 * @return the domain, or null if the record has no domain (outer join)
	 */
	public static Domain toDomain(Record r, net.netshot.netshot.platform.database.schema.tables.Domain d) {
		Long id = r.get(d.ID);
		if (id == null) {
			return null;
		}
		return new Domain(id, r.get(d.VERSION), r.get(d.CHANGE_DATE), r.get(d.NAME), r.get(d.DESCRIPTION),
			Network4Address.fromColumns(r.get(d.IPV4_ADDRESS), null, null),
			Network6Address.fromColumns(r.get(d.IPV6_ADDRESS), null, null));
	}

	private static Domain toDomain(Record r) {
		return toDomain(r, DOMAIN);
	}

	/**
	 * The time of a change, to the second (as the former Hibernate interceptor did).
	 *
	 * @return the time
	 */
	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.SECONDS);
	}

	/** The fields the domain lists can be filtered and sorted on. */
	static final QueryMapping LIST = QueryMapping.builder(DOMAIN.ID)
		.string("name", DOMAIN.NAME, Option.SORTABLE, Option.SEARCHABLE)
		.string("description", DOMAIN.DESCRIPTION, Option.SEARCHABLE)
		.inet("server4Address", DOMAIN.IPV4_ADDRESS)
		.date("changeDate", DOMAIN.CHANGE_DATE, Option.SORTABLE)
		.defaultSort(SortOrder.asc("name"))
		.build();

	/**
	 * Lists the domains matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query
	 * @return the domains of the window of the query
	 */
	public List<Domain> findAll(DSLContext dsl, ListQuery query) {
		return query.page().apply(dsl.select(DOMAIN.fields()).from(DOMAIN)
			.where(LIST.condition(query))
			.orderBy(LIST.orderBy(query)))
			.fetch(DomainRepository::toDomain);
	}

	/**
	 * Counts the domains matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query (its window is ignored)
	 * @return the number of domains
	 */
	public long count(DSLContext dsl, ListQuery query) {
		return dsl.fetchCount(DOMAIN, LIST.condition(query));
	}

	/**
	 * Lists the domains, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the domains
	 */
	public List<Domain> findAll(DSLContext dsl, Page page) {
		return page.apply(dsl.select(DOMAIN.fields()).from(DOMAIN).orderBy(DOMAIN.ID.asc())).fetch(DomainRepository::toDomain);
	}

	/**
	 * Finds a domain.
	 *
	 * @param dsl the jOOQ context
	 * @param id the domain ID
	 * @return the domain, or null if there is no such domain
	 */
	public Domain find(DSLContext dsl, long id) {
		return dsl.select(DOMAIN.fields()).from(DOMAIN).where(DOMAIN.ID.eq(id)).fetchOne(DomainRepository::toDomain);
	}

	/**
	 * Stores a new domain (version 0, changed now).
	 *
	 * @param dsl the jOOQ context
	 * @param domain the domain (its ID, version and change date are ignored)
	 * @return the stored domain
	 */
	public Domain add(DSLContext dsl, Domain domain) {
		Network4Address v4 = domain.server4Address();
		Network6Address v6 = domain.server6Address();
		long id = dsl.insertInto(DOMAIN)
			.set(DOMAIN.VERSION, 0)
			.set(DOMAIN.CHANGE_DATE, now())
			.set(DOMAIN.NAME, domain.name())
			.set(DOMAIN.DESCRIPTION, domain.description())
			.set(DOMAIN.IPV4_ADDRESS, v4 == null ? null : v4.getAddress())
			.set(DOMAIN.IPV6_ADDRESS, v6 == null ? null : v6.getAddress())
			.returning(DOMAIN.ID)
			.fetchOne(DOMAIN.ID);
		return this.find(dsl, id);
	}

	/**
	 * Saves the name, description and IPv4 server address of a domain (its version is
	 * incremented, its change date set).
	 *
	 * @param dsl the jOOQ context
	 * @param domain the domain
	 * @return the saved domain, or null if there is no such domain
	 */
	public Domain save(DSLContext dsl, Domain domain) {
		return this.save(dsl, domain, null);
	}

	/**
	 * Saves the name, description and IPv4 server address of a domain if it is still at a given version (its
	 * version is incremented, its change date set).
	 *
	 * @param dsl the jOOQ context
	 * @param domain the domain
	 * @param expectedVersion the version the domain must be at, or null for any
	 * @return the saved domain, or null if there is no such domain (at that version)
	 */
	public Domain save(DSLContext dsl, Domain domain, Long expectedVersion) {
		Network4Address v4 = domain.server4Address();
		int updated = dsl.update(DOMAIN)
			.set(DOMAIN.VERSION, DOMAIN.VERSION.plus(1))
			.set(DOMAIN.CHANGE_DATE, now())
			.set(DOMAIN.NAME, domain.name())
			.set(DOMAIN.DESCRIPTION, domain.description())
			.set(DOMAIN.IPV4_ADDRESS, v4 == null ? null : v4.getAddress())
			.where(DOMAIN.ID.eq(domain.id()))
			.and(expectedVersion == null ? DSL.trueCondition() : DOMAIN.VERSION.cast(Long.class).eq(expectedVersion))
			.execute();
		return updated == 0 ? null : this.find(dsl, domain.id());
	}

	/**
	 * Removes a domain.
	 *
	 * @param dsl the jOOQ context
	 * @param id the domain ID
	 * @return true if the domain was found and removed
	 */
	public boolean remove(DSLContext dsl, long id) {
		return this.remove(dsl, id, null);
	}

	/**
	 * Removes a domain if it is at a given version.
	 *
	 * @param dsl the jOOQ context
	 * @param id the domain ID
	 * @param expectedVersion the version the domain must be at, or null for any
	 * @return true if the domain was found (at that version) and removed
	 */
	public boolean remove(DSLContext dsl, long id, Long expectedVersion) {
		return dsl.deleteFrom(DOMAIN).where(DOMAIN.ID.eq(id))
			.and(expectedVersion == null ? DSL.trueCondition() : DOMAIN.VERSION.cast(Long.class).eq(expectedVersion))
			.execute() > 0;
	}
}
