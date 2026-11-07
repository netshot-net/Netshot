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
package net.netshot.netshot.platform.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;

import lombok.extern.slf4j.Slf4j;

/**
 * Transaction boundaries for the services: takes a connection from a pool, runs the
 * given work with a jOOQ context on it, commits or rolls back, and always gives the
 * connection back.
 *
 * <p>Any exception (or error) thrown by the work, or by the commit, rolls the
 * transaction back and is rethrown unchanged (a failing commit as a jOOQ
 * {@link DataAccessException}).</p>
 */
@Slf4j
public class Transactions {

	/** The database access, which gives the connections. */
	private final DatabaseService database;

	/**
	 * Instantiates the transaction support.
	 *
	 * @param database the database access, which gives the connections
	 */
	public Transactions(DatabaseService database) {
		this.database = database;
	}

	/**
	 * Runs some work with a connection of the read-only pool. The work is meant for
	 * queries: whatever it does is rolled back at the end. Note that the read-only pool
	 * only points to another database when {@code netshot.db.readurl} is set; otherwise
	 * it falls back to the read-write one.
	 *
	 * @param <T> the type of the result
	 * @param work the work to run
	 * @return the result of the work
	 */
	public <T> T read(Function<DSLContext, T> work) {
		return inTransaction(PoolKind.READ_ONLY, false, work);
	}

	/**
	 * Runs some work in a read-write transaction, committed when the work
	 * completes, rolled back when it throws.
	 *
	 * @param <T> the type of the result
	 * @param work the work to run
	 * @return the result of the work
	 */
	public <T> T write(Function<DSLContext, T> work) {
		return inTransaction(PoolKind.READ_WRITE, true, work);
	}

	/**
	 * Runs some work in a read-write transaction, without result.
	 *
	 * @param work the work to run
	 * @see #write(Function)
	 */
	public void run(Consumer<DSLContext> work) {
		this.write(dsl -> {
			work.accept(dsl);
			return null;
		});
	}

	/**
	 * Runs some work in a read-write transaction of the small system pool. Reserved for
	 * the short task dispatch and cluster bookkeeping transactions (they don't compete with
	 * the task threads for the main pool): never run device I/O, hooks or any other
	 * potentially long operation in it.
	 *
	 * @param <T> the type of the result
	 * @param work the work to run
	 * @return the result of the work
	 */
	public <T> T system(Function<DSLContext, T> work) {
		return inTransaction(PoolKind.SYSTEM, true, work);
	}

	private <T> T inTransaction(PoolKind pool, boolean commit, Function<DSLContext, T> work) {
		try (Connection connection = this.database.getConnection(pool)) {
			connection.setAutoCommit(false);
			try {
				T result = work.apply(Jooq.dsl(connection));
				if (commit) {
					connection.commit();
				}
				else {
					connection.rollback();
				}
				return result;
			}
			catch (Throwable t) {
				rollbackQuietly(connection);
				throw t;
			}
		}
		catch (SQLException e) {
			throw new DataAccessException("Database transaction failure: " + e.getMessage(), e);
		}
	}

	private static void rollbackQuietly(Connection connection) {
		try {
			connection.rollback();
		}
		catch (SQLException e) {
			log.error("Error during transaction rollback.", e);
		}
	}

}
