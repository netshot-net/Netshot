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
package net.netshot.netshot.work;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.database.Database;

/**
 * Task and device locks, preventing a task from running twice, and two tasks from working on the
 * same device at the same time.
 *
 * <p>Locks are always held in memory (exclusion within this Netshot instance). In cluster mode,
 * they are additionally held as PostgreSQL session-level advisory locks, on a dedicated
 * (unpooled) connection, for exclusion across the cluster members. PostgreSQL releases them
 * automatically if this instance dies (TCP keepalives are enabled on the connection so that a
 * dead peer is detected within about a minute).
 *
 * <p>Advisory locks being re-entrant within a database session, and all the locks of this
 * instance sharing the same connection, the in-memory map is what prevents two local tasks from
 * holding the same lock. Only non-blocking lock functions are used: a blocking call would freeze
 * every task thread behind the shared connection.
 */
@Slf4j
public class TaskLockManager {

	/** Lock namespace for tasks (lock ID = task ID). */
	public static final int NS_TASK = 1;

	/** Lock namespace for exclusive access to a device (lock ID = device ID). */
	public static final int NS_DEVICE = 2;

	/** Lock namespace for snapshot tasks running or waiting on a device (lock ID = device ID). */
	public static final int NS_SNAPSHOT = 3;

	/** Lock namespace for diagnostic tasks running or waiting on a device (lock ID = device ID). */
	public static final int NS_DIAGNOSTICS = 4;

	/** TCP keepalive settings applied to the lock connection, so that the server detects dead peers. */
	private static final String[] CONNECTION_SETTINGS = {
		"SET application_name = 'Netshot task locks'",
		"SET tcp_keepalives_idle = 30",
		"SET tcp_keepalives_interval = 10",
		"SET tcp_keepalives_count = 3",
	};

	/** Network timeout on the lock connection (ms). */
	private static final int NETWORK_TIMEOUT = 15000;

	/** The lock manager of this instance (in-memory only until {@link #init(boolean)} is called). */
	private static volatile TaskLockManager instance = new TaskLockManager(false, null);

	/**
	 * Initializes the lock manager of this instance.
	 * @param distributed true to use database locks (cluster mode), false for in-memory locks only
	 */
	public static void init(boolean distributed) {
		log.info("Initializing the task lock manager ({} mode).", distributed ? "distributed" : "local");
		TaskLockManager.instance = new TaskLockManager(distributed, () -> {
			try {
				return Database.getConnection(false);
			}
			catch (SQLException e) {
				throw new IllegalStateException("Unable to open the task lock DB connection", e);
			}
		});
	}

	/**
	 * Gets the lock manager of this instance.
	 * @return the lock manager
	 */
	public static TaskLockManager get() {
		return TaskLockManager.instance;
	}

	/**
	 * Lock key (two-int form of PostgreSQL advisory locks).
	 * @param namespace the lock namespace
	 * @param id the ID of the locked object
	 */
	private record LockKey(int namespace, int id) {
		static LockKey of(int namespace, long id) {
			return new LockKey(namespace, Math.toIntExact(id));
		}
	}

	/** Whether locks are also held in the database (cluster mode). */
	private final boolean distributed;

	/** Provides new DB connections (distributed mode). */
	private final Supplier<Connection> connectionSupplier;

	/** Locks held by this instance, with the ID of the owning task. */
	private final Map<LockKey, Long> heldLocks = new HashMap<>();

	/** The DB connection holding the advisory locks (distributed mode), opened lazily. */
	private Connection connection;

	/**
	 * Creates a lock manager.
	 * @param distributed true to use database locks
	 * @param connectionSupplier provider of DB connections (used in distributed mode only)
	 */
	TaskLockManager(boolean distributed, Supplier<Connection> connectionSupplier) {
		this.distributed = distributed;
		this.connectionSupplier = connectionSupplier;
	}

	/**
	 * Tries to acquire a lock, without waiting.
	 * @param namespace the lock namespace
	 * @param id the ID of the object to lock
	 * @param ownerTaskId the ID of the task acquiring the lock
	 * @return true if the lock was acquired, false if it's already held (locally or by another instance)
	 * @throws SQLException in case of database error (distributed mode)
	 */
	public synchronized boolean tryLock(int namespace, long id, long ownerTaskId) throws SQLException {
		LockKey key = LockKey.of(namespace, id);
		if (this.heldLocks.containsKey(key)) {
			log.debug("Lock {} is already held locally by task {}.", key, this.heldLocks.get(key));
			return false;
		}
		if (this.distributed && !this.withConnectionRetry(conn -> this.tryLockInDatabase(conn, key))) {
			log.debug("Lock {} is held by another cluster member.", key);
			return false;
		}
		this.heldLocks.put(key, ownerTaskId);
		log.trace("Lock {} acquired by task {}.", key, ownerTaskId);
		return true;
	}

	/**
	 * Releases a lock.
	 * @param namespace the lock namespace
	 * @param id the ID of the locked object
	 * @param ownerTaskId the ID of the task which acquired the lock
	 */
	public synchronized void unlock(int namespace, long id, long ownerTaskId) {
		LockKey key = LockKey.of(namespace, id);
		Long owner = this.heldLocks.get(key);
		if (owner == null || owner != ownerTaskId) {
			log.warn("Task {} is trying to release lock {} which it doesn't hold (owner {}).", ownerTaskId, key, owner);
			return;
		}
		this.heldLocks.remove(key);
		log.trace("Lock {} released by task {}.", key, ownerTaskId);
		if (this.distributed && this.connection != null) {
			try {
				try (PreparedStatement statement = this.connection.prepareStatement("SELECT pg_advisory_unlock(?, ?)")) {
					statement.setInt(1, key.namespace());
					statement.setInt(2, key.id());
					statement.executeQuery().close();
				}
			}
			catch (SQLException e) {
				// Closing the connection releases all its locks: reconnect and re-acquire the other ones
				log.error("Error while releasing lock {}, resetting the lock connection.", key, e);
				this.closeConnection();
				try {
					this.getConnection();
				}
				catch (SQLException e1) {
					log.error("Unable to reopen the task lock connection.", e1);
				}
			}
		}
	}

	/**
	 * Gets the ID of the local task holding a lock.
	 * @param namespace the lock namespace
	 * @param id the ID of the locked object
	 * @return the ID of the owning task, or null if the lock is not held by this instance
	 */
	public synchronized Long getLocalOwner(int namespace, long id) {
		return this.heldLocks.get(LockKey.of(namespace, id));
	}

	/**
	 * Checks whether a lock is held, by this instance or (distributed mode) by any other instance.
	 * Read-only: doesn't try to acquire the lock (which could make a legitimate holder fail).
	 * @param namespace the lock namespace
	 * @param id the ID of the locked object
	 * @return true if the lock is held
	 * @throws SQLException in case of database error (distributed mode)
	 */
	public synchronized boolean isHeld(int namespace, long id) throws SQLException {
		LockKey key = LockKey.of(namespace, id);
		if (this.heldLocks.containsKey(key)) {
			return true;
		}
		if (!this.distributed) {
			return false;
		}
		return this.withConnectionRetry(conn -> {
			try (PreparedStatement statement = conn.prepareStatement(
				"SELECT 1 FROM pg_locks WHERE locktype = 'advisory' AND granted "
					+ "AND database = (SELECT oid FROM pg_database WHERE datname = current_database()) "
					+ "AND classid::int8 = ? AND objid::int8 = ? AND objsubid = 2")) {
				statement.setLong(1, key.namespace());
				statement.setLong(2, key.id());
				try (ResultSet result = statement.executeQuery()) {
					return result.next();
				}
			}
		});
	}

	/**
	 * Checks the health of the lock connection (distributed mode), reconnecting (and re-acquiring
	 * the held locks) if needed. To be called periodically, so that a lost connection is noticed
	 * even when no task starts or ends.
	 */
	public synchronized void check() {
		if (!this.distributed || this.connection == null) {
			return;
		}
		try {
			this.withConnectionRetry(conn -> {
				try (Statement statement = conn.createStatement()) {
					statement.executeQuery("SELECT 1").close();
				}
				return true;
			});
		}
		catch (SQLException e) {
			log.error("The task lock connection is not working.", e);
		}
	}

	/**
	 * Closes the lock connection (releasing all the database locks).
	 */
	synchronized void close() {
		this.closeConnection();
	}

	/**
	 * An operation on the lock connection.
	 * @param <T> the type of the operation result
	 */
	@FunctionalInterface
	private interface ConnectionOperation<T> {
		T run(Connection connection) throws SQLException;
	}

	/**
	 * Runs an operation on the lock connection; on error, reconnects (re-acquiring the held locks)
	 * and retries once.
	 * @param <T> the type of the operation result
	 * @param operation the operation
	 * @return the result of the operation
	 * @throws SQLException if the operation failed again after reconnecting
	 */
	private <T> T withConnectionRetry(ConnectionOperation<T> operation) throws SQLException {
		try {
			return operation.run(this.getConnection());
		}
		catch (SQLException e) {
			log.warn("Error on the task lock connection, will reconnect and retry.", e);
			this.closeConnection();
			return operation.run(this.getConnection());
		}
	}

	private boolean tryLockInDatabase(Connection conn, LockKey key) throws SQLException {
		try (PreparedStatement statement = conn.prepareStatement("SELECT pg_try_advisory_lock(?, ?)")) {
			statement.setInt(1, key.namespace());
			statement.setInt(2, key.id());
			try (ResultSet result = statement.executeQuery()) {
				return result.next() && result.getBoolean(1);
			}
		}
	}

	/**
	 * Gets the lock connection, opening it (and re-acquiring the locks held in memory) if needed.
	 * @return the connection
	 * @throws SQLException in case of error while opening the connection
	 */
	private Connection getConnection() throws SQLException {
		if (this.connection != null) {
			return this.connection;
		}
		Connection conn;
		try {
			conn = this.connectionSupplier.get();
		}
		catch (IllegalStateException e) {
			if (e.getCause() instanceof SQLException sqlException) {
				throw sqlException;
			}
			throw e;
		}
		try {
			conn.setNetworkTimeout(Runnable::run, NETWORK_TIMEOUT);
			try (Statement statement = conn.createStatement()) {
				for (String setting : CONNECTION_SETTINGS) {
					statement.execute(setting);
				}
			}
			// After a reconnection, re-acquire the locks still held by local tasks
			for (Map.Entry<LockKey, Long> heldLock : this.heldLocks.entrySet()) {
				if (!this.tryLockInDatabase(conn, heldLock.getKey())) {
					log.error("Lock {} (held by local task {}) couldn't be re-acquired after reconnection: "
						+ "it is now held by another cluster member.", heldLock.getKey(), heldLock.getValue());
				}
			}
		}
		catch (SQLException e) {
			try {
				conn.close();
			}
			catch (SQLException e1) {
				// Ignore
			}
			throw e;
		}
		if (!this.heldLocks.isEmpty()) {
			log.warn("Task lock connection reopened, {} held lock(s) re-acquired.", this.heldLocks.size());
		}
		this.connection = conn;
		return conn;
	}

	private void closeConnection() {
		if (this.connection != null) {
			try {
				this.connection.close();
			}
			catch (SQLException e) {
				log.debug("Error while closing the task lock connection.", e);
			}
			this.connection = null;
		}
	}
}
