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

import java.util.function.Consumer;
import java.util.function.Function;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.jooq.DSLContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Transaction boundaries for the services: opens a session, runs the given
 * work, commits or rolls back, and always closes the session.
 *
 * <p>Any exception (or error) thrown by the work, or by the commit, rolls the
 * transaction back and is rethrown unchanged.</p>
 */
@Slf4j
public class Transactions {

	/**
	 * Runs some work with a session from the read-only pool (the same session as
	 * {@code Database.getSession(true)}). No transaction is started: the session is
	 * meant for queries. Note that the read-only pool only points to another
	 * database when {@code netshot.db.readurl} is set; otherwise it falls back to
	 * the read-write one, and nothing prevents the work from writing.
	 *
	 * @param <T> the type of the result
	 * @param work the work to run
	 * @return the result of the work
	 */
	public <T> T read(Function<Session, T> work) {
		try (Session session = Database.getSession(true)) {
			return work.apply(session);
		}
	}

	/**
	 * Runs some work in a read-write transaction, committed when the work
	 * completes, rolled back when it throws.
	 *
	 * @param <T> the type of the result
	 * @param work the work to run
	 * @return the result of the work
	 */
	public <T> T write(Function<Session, T> work) {
		try (Session session = Database.getSession(false)) {
			Transaction transaction = session.beginTransaction();
			try {
				T result = work.apply(session);
				transaction.commit();
				return result;
			}
			catch (Throwable t) {
				rollbackQuietly(transaction);
				throw t;
			}
		}
	}

	/**
	 * Runs some work in a read-write transaction, without result.
	 *
	 * @param work the work to run
	 * @see #write(Function)
	 */
	public void run(Consumer<Session> work) {
		this.write(session -> {
			work.accept(session);
			return null;
		});
	}

	/**
	 * Same as {@link #read(Function)}, with a jOOQ context (on the connection of the session).
	 *
	 * @param <T> the type of the result
	 * @param work the work to run
	 * @return the result of the work
	 */
	public <T> T readDsl(Function<DSLContext, T> work) {
		return this.read(session -> work.apply(Jooq.dsl(session)));
	}

	/**
	 * Same as {@link #write(Function)}, with a jOOQ context (on the connection of the
	 * session, in its transaction).
	 *
	 * @param <T> the type of the result
	 * @param work the work to run
	 * @return the result of the work
	 */
	public <T> T writeDsl(Function<DSLContext, T> work) {
		return this.write(session -> work.apply(Jooq.dsl(session)));
	}

	/**
	 * Same as {@link #run(Consumer)}, with a jOOQ context (on the connection of the
	 * session, in its transaction).
	 *
	 * @param work the work to run
	 */
	public void runDsl(Consumer<DSLContext> work) {
		this.run(session -> work.accept(Jooq.dsl(session)));
	}

	private static void rollbackQuietly(Transaction transaction) {
		try {
			if (transaction.isActive()) {
				transaction.rollback();
			}
		}
		catch (Exception e) {
			log.error("Error during transaction rollback.", e);
		}
	}

}
