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
package net.netshot.netshot.platform.database;

import java.util.concurrent.atomic.AtomicReference;

import org.hibernate.Session;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.testing.DatabaseTest;

@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
public class TransactionsTest {

	private final Transactions transactions = new Transactions();

	private long countDomains(String name) {
		return this.transactions.readDsl(dsl -> (long) dsl.fetchCount(Tables.DOMAIN, Tables.DOMAIN.NAME.eq(name)));
	}

	// Inserts a domain with the connection of a session
	private static int insertDomain(Session session, String name, String description) {
		return Jooq.dsl(session).insertInto(Tables.DOMAIN)
			.set(Tables.DOMAIN.NAME, name).set(Tables.DOMAIN.DESCRIPTION, description).set(Tables.DOMAIN.VERSION, 0)
			.execute();
	}

	@Test
	@DisplayName("write() commits and returns the result of the work")
	void writeCommits() {
		AtomicReference<Session> used = new AtomicReference<>();
		long id = this.transactions.write(session -> {
			used.set(session);
			insertDomain(session, "tx-commit", "committed");
			return Jooq.dsl(session).select(Tables.DOMAIN.ID).from(Tables.DOMAIN).where(Tables.DOMAIN.NAME.eq("tx-commit"))
				.fetchOne(Tables.DOMAIN.ID);
		});
		Assertions.assertTrue(id > 0);
		Assertions.assertEquals(1, this.countDomains("tx-commit"));
		Assertions.assertFalse(used.get().isOpen(), "The session must be closed");
	}

	@Test
	@DisplayName("run() commits")
	void runCommits() {
		this.transactions.run(session -> insertDomain(session, "tx-run", "committed"));
		Assertions.assertEquals(1, this.countDomains("tx-run"));
	}

	@Test
	@DisplayName("write() rolls back on exception, rethrows it unchanged and closes the session")
	void writeRollsBack() {
		AtomicReference<Session> used = new AtomicReference<>();
		IllegalStateException failure = new IllegalStateException("boom");
		IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
			() -> this.transactions.write(session -> {
				used.set(session);
				insertDomain(session, "tx-rollback", "rolled back");
				throw failure;
			}));
		Assertions.assertSame(failure, thrown);
		Assertions.assertEquals(0, this.countDomains("tx-rollback"));
		Assertions.assertFalse(used.get().isOpen(), "The session must be closed");
	}

	@Test
	@DisplayName("run() rolls back on exception")
	void runRollsBack() {
		Assertions.assertThrows(UnsupportedOperationException.class,
			() -> this.transactions.run(session -> {
				insertDomain(session, "tx-run-rollback", "rolled back");
				throw new UnsupportedOperationException();
			}));
		Assertions.assertEquals(0, this.countDomains("tx-run-rollback"));
	}

	@Test
	@DisplayName("write() rolls back when the commit itself fails (constraint violation)")
	void writeRollsBackOnFlushError() {
		this.transactions.run(session -> insertDomain(session, "tx-unique", "first"));
		Assertions.assertThrows(RuntimeException.class,
			() -> this.transactions.run(session -> {
				insertDomain(session, "tx-unique-other", "other");
				insertDomain(session, "tx-unique", "duplicate");
			}));
		Assertions.assertEquals(1, this.countDomains("tx-unique"));
		Assertions.assertEquals(0, this.countDomains("tx-unique-other"));
	}

	@Test
	@DisplayName("read() uses a READ_ONLY tenant session, sees committed data and closes the session")
	void readUsesReadOnlyTenant() {
		this.transactions.run(session -> insertDomain(session, "tx-read", "readable"));
		AtomicReference<Session> used = new AtomicReference<>();
		String description = this.transactions.read(session -> {
			used.set(session);
			Assertions.assertEquals(TenantIdentifier.READ_ONLY.toString(), session.unwrap(SharedSessionContractImplementor.class)
				.getTenantIdentifier());
			return Jooq.dsl(session).select(Tables.DOMAIN.DESCRIPTION).from(Tables.DOMAIN).where(Tables.DOMAIN.NAME.eq("tx-read"))
				.fetchOne(Tables.DOMAIN.DESCRIPTION);
		});
		Assertions.assertEquals("readable", description);
		Assertions.assertFalse(used.get().isOpen(), "The session must be closed");
	}

	@Test
	@DisplayName("read() has no transaction and does not commit; without netshot.db.readurl the read pool is the read-write database")
	void readHasNoTransaction() {
		this.transactions.read(session -> {
			Assertions.assertFalse(session.getTransaction().isActive());
			return null;
		});
	}

	@Test
	@DisplayName("writeDsl() commits the statements of the work")
	void writeDslCommits() {
		int inserted = this.transactions.writeDsl(dsl -> dsl.insertInto(Tables.DOMAIN)
			.set(Tables.DOMAIN.NAME, "dsl-commit").set(Tables.DOMAIN.DESCRIPTION, "committed").set(Tables.DOMAIN.VERSION, 0)
			.execute());
		Assertions.assertEquals(1, inserted);
		Assertions.assertEquals(1, this.countDomains("dsl-commit"));
	}

	@Test
	@DisplayName("runDsl() rolls back on exception and rethrows it unchanged")
	void runDslRollsBack() {
		IllegalStateException failure = new IllegalStateException("boom");
		IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
			() -> this.transactions.runDsl(dsl -> {
				dsl.insertInto(Tables.DOMAIN)
					.set(Tables.DOMAIN.NAME, "dsl-rollback").set(Tables.DOMAIN.DESCRIPTION, "rolled back").set(Tables.DOMAIN.VERSION, 0)
					.execute();
				throw failure;
			}));
		Assertions.assertSame(failure, thrown);
		Assertions.assertEquals(0, this.countDomains("dsl-rollback"));
	}

	@Test
	@DisplayName("readDsl() sees the committed data")
	void readDslSeesCommittedData() {
		this.transactions.run(session -> insertDomain(session, "dsl-read", "readable"));
		String description = this.transactions.readDsl(dsl -> dsl.select(Tables.DOMAIN.DESCRIPTION).from(Tables.DOMAIN)
			.where(Tables.DOMAIN.NAME.eq("dsl-read")).fetchOne(Tables.DOMAIN.DESCRIPTION));
		Assertions.assertEquals("readable", description);
	}
}
