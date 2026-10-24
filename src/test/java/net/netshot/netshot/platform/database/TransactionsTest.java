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

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicReference;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
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
		return this.transactions.read(dsl -> (long) dsl.fetchCount(Tables.DOMAIN, Tables.DOMAIN.NAME.eq(name)));
	}

	private static int insertDomain(DSLContext dsl, String name, String description) {
		return dsl.insertInto(Tables.DOMAIN)
			.set(Tables.DOMAIN.NAME, name).set(Tables.DOMAIN.DESCRIPTION, description).set(Tables.DOMAIN.VERSION, 0)
			.execute();
	}

	// The connection the context works on
	private static Connection connectionOf(DSLContext dsl) {
		return dsl.configuration().connectionProvider().acquire();
	}

	@Test
	@DisplayName("write() commits, returns the result of the work and gives the connection back")
	void writeCommits() throws Exception {
		AtomicReference<Connection> used = new AtomicReference<>();
		long id = this.transactions.write(dsl -> {
			used.set(connectionOf(dsl));
			insertDomain(dsl, "tx-commit", "committed");
			return dsl.select(Tables.DOMAIN.ID).from(Tables.DOMAIN).where(Tables.DOMAIN.NAME.eq("tx-commit"))
				.fetchOne(Tables.DOMAIN.ID);
		});
		Assertions.assertTrue(id > 0);
		Assertions.assertEquals(1, this.countDomains("tx-commit"));
		Assertions.assertTrue(used.get().isClosed(), "The connection must be given back");
	}

	@Test
	@DisplayName("write() runs the work in a transaction (no auto-commit)")
	void writeHasTransaction() {
		this.transactions.run(dsl -> Assertions.assertDoesNotThrow(() -> Assertions.assertFalse(connectionOf(dsl).getAutoCommit())));
	}

	@Test
	@DisplayName("run() commits")
	void runCommits() {
		this.transactions.run(dsl -> insertDomain(dsl, "tx-run", "committed"));
		Assertions.assertEquals(1, this.countDomains("tx-run"));
	}

	@Test
	@DisplayName("write() rolls back on exception, rethrows it unchanged and gives the connection back")
	void writeRollsBack() throws Exception {
		AtomicReference<Connection> used = new AtomicReference<>();
		IllegalStateException failure = new IllegalStateException("boom");
		IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
			() -> this.transactions.write(dsl -> {
				used.set(connectionOf(dsl));
				insertDomain(dsl, "tx-rollback", "rolled back");
				throw failure;
			}));
		Assertions.assertSame(failure, thrown);
		Assertions.assertEquals(0, this.countDomains("tx-rollback"));
		Assertions.assertTrue(used.get().isClosed(), "The connection must be given back");
	}

	@Test
	@DisplayName("run() rolls back on exception")
	void runRollsBack() {
		Assertions.assertThrows(UnsupportedOperationException.class,
			() -> this.transactions.run(dsl -> {
				insertDomain(dsl, "tx-run-rollback", "rolled back");
				throw new UnsupportedOperationException();
			}));
		Assertions.assertEquals(0, this.countDomains("tx-run-rollback"));
	}

	@Test
	@DisplayName("A failing statement rolls back the whole transaction (constraint violation)")
	void constraintViolationRollsBack() {
		this.transactions.run(dsl -> insertDomain(dsl, "tx-unique", "first"));
		DataAccessException thrown = Assertions.assertThrows(DataAccessException.class,
			() -> this.transactions.run(dsl -> {
				insertDomain(dsl, "tx-unique-other", "other");
				insertDomain(dsl, "tx-unique", "duplicate");
			}));
		Assertions.assertTrue(DatabaseErrors.isUniqueViolation(thrown));
		Assertions.assertEquals(1, this.countDomains("tx-unique"));
		Assertions.assertEquals(0, this.countDomains("tx-unique-other"));
	}

	@Test
	@DisplayName("read() sees the committed data and gives the connection back")
	void readSeesCommittedData() throws Exception {
		this.transactions.run(dsl -> insertDomain(dsl, "tx-read", "readable"));
		AtomicReference<Connection> used = new AtomicReference<>();
		String description = this.transactions.read(dsl -> {
			used.set(connectionOf(dsl));
			return dsl.select(Tables.DOMAIN.DESCRIPTION).from(Tables.DOMAIN).where(Tables.DOMAIN.NAME.eq("tx-read"))
				.fetchOne(Tables.DOMAIN.DESCRIPTION);
		});
		Assertions.assertEquals("readable", description);
		Assertions.assertTrue(used.get().isClosed(), "The connection must be given back");
	}

	@Test
	@DisplayName("read() never commits; without netshot.db.readurl the read pool is the read-write database")
	void readDoesNotCommit() {
		int inserted = this.transactions.read(dsl -> insertDomain(dsl, "tx-read-only", "not committed"));
		Assertions.assertEquals(1, inserted);
		Assertions.assertEquals(0, this.countDomains("tx-read-only"));
	}

	@Test
	@DisplayName("system() commits, and rolls back on exception")
	void systemCommitsAndRollsBack() {
		this.transactions.system(dsl -> insertDomain(dsl, "tx-system", "committed"));
		Assertions.assertEquals(1, this.countDomains("tx-system"));
		Assertions.assertThrows(IllegalStateException.class, () -> this.transactions.system(dsl -> {
			insertDomain(dsl, "tx-system-rollback", "rolled back");
			throw new IllegalStateException();
		}));
		Assertions.assertEquals(0, this.countDomains("tx-system-rollback"));
	}
}
