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

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.hibernate.Session;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.testing.DatabaseTest;

@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
public class TransactionsTest {

	private final Transactions transactions = new Transactions();

	private long countDomains(String name) {
		try (Session session = Database.getSession()) {
			return session.createQuery("select count(d) from Domain d where d.name = :name", Long.class)
				.setParameter("name", name).uniqueResult();
		}
	}

	@Test
	@DisplayName("write() commits and returns the result of the work")
	void writeCommits() {
		AtomicReference<Session> used = new AtomicReference<>();
		long id = this.transactions.write(session -> {
			used.set(session);
			Domain domain = new Domain("tx-commit", "committed", null, null);
			session.persist(domain);
			return domain.getId();
		});
		Assertions.assertTrue(id > 0);
		Assertions.assertEquals(1, this.countDomains("tx-commit"));
		Assertions.assertFalse(used.get().isOpen(), "The session must be closed");
	}

	@Test
	@DisplayName("run() commits")
	void runCommits() {
		this.transactions.run(session -> session.persist(new Domain("tx-run", "committed", null, null)));
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
				session.persist(new Domain("tx-rollback", "rolled back", null, null));
				session.flush();
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
				session.persist(new Domain("tx-run-rollback", "rolled back", null, null));
				session.flush();
				throw new UnsupportedOperationException();
			}));
		Assertions.assertEquals(0, this.countDomains("tx-run-rollback"));
	}

	@Test
	@DisplayName("write() rolls back when the commit itself fails (constraint violation)")
	void writeRollsBackOnFlushError() {
		this.transactions.run(session -> session.persist(new Domain("tx-unique", "first", null, null)));
		Assertions.assertThrows(RuntimeException.class,
			() -> this.transactions.run(session -> {
				session.persist(new Domain("tx-unique-other", "other", null, null));
				session.persist(new Domain("tx-unique", "duplicate", null, null));
			}));
		Assertions.assertEquals(1, this.countDomains("tx-unique"));
		Assertions.assertEquals(0, this.countDomains("tx-unique-other"));
	}

	@Test
	@DisplayName("read() uses a READ_ONLY tenant session, sees committed data and closes the session")
	void readUsesReadOnlyTenant() {
		this.transactions.run(session -> session.persist(new Domain("tx-read", "readable", null, null)));
		AtomicReference<Session> used = new AtomicReference<>();
		String description = this.transactions.read(session -> {
			used.set(session);
			Assertions.assertEquals(TenantIdentifier.READ_ONLY.toString(), session.unwrap(SharedSessionContractImplementor.class)
				.getTenantIdentifier());
			return session.createQuery("select d.description from Domain d where d.name = :name", String.class)
				.setParameter("name", "tx-read").uniqueResult();
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
		this.transactions.run(session -> session.persist(new Domain("dsl-read", "readable", null, null)));
		String description = this.transactions.readDsl(dsl -> dsl.select(Tables.DOMAIN.DESCRIPTION).from(Tables.DOMAIN)
			.where(Tables.DOMAIN.NAME.eq("dsl-read")).fetchOne(Tables.DOMAIN.DESCRIPTION));
		Assertions.assertEquals("readable", description);
	}

	@Test
	@DisplayName("In a Hibernate transaction, a jOOQ statement sees the changes of the session not flushed yet, in the same transaction")
	void dslSeesPendingSessionChanges() {
		Assertions.assertThrows(IllegalStateException.class, () -> this.transactions.run(session -> {
			session.persist(new Domain("dsl-pending", "pending", null, null));
			DSLContext dsl = Jooq.dsl(session);
			Assertions.assertEquals(1, dsl.fetchCount(Tables.DOMAIN, Tables.DOMAIN.NAME.eq("dsl-pending")));
			throw new IllegalStateException("rollback");
		}));
		Assertions.assertEquals(0, this.countDomains("dsl-pending"), "The jOOQ statement ran in the rolled back transaction");
	}

	@Test
	@DisplayName("jOOQ reads the dates and the addresses written by Hibernate as the same instants and addresses")
	void dslReadsHibernateValues() throws Exception {
		Domain domain = new Domain("dsl-values", "values", new Network4Address("10.20.30.40", 24), null);
		this.transactions.run(session -> session.persist(domain));
		var record = this.transactions.readDsl(dsl -> dsl.fetchOne(Tables.DOMAIN, Tables.DOMAIN.NAME.eq("dsl-values")));
		Assertions.assertNotNull(domain.getChangeDate());
		Assertions.assertEquals(domain.getChangeDate().toInstant(), record.getChangeDate());
		Assertions.assertEquals(InetAddress.getByName("10.20.30.40"), record.getIpv4Address());
		Assertions.assertEquals(24, record.getIpv4Pfxlen());
	}

}
