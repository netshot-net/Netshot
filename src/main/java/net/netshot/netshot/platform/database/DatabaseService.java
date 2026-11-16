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
import java.util.Map;

import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.lifecycle.Service;

/**
 * The database access as a service: creates the connection pools when started, closes them when stopped,
 * and gives the connections of the pools to {@link Transactions}.
 * Its statistics are the ones of the pools (connections total, busy, idle, threads waiting for one).
 */
public final class DatabaseService implements Service {

	/** The configuration store. */
	private final ConfigStore config;

	/** The connection pools, while started. */
	private volatile ConnectionPools pools;

	/**
	 * Creates the database access.
	 *
	 * @param config the configuration store (the database and the sizes of the pools)
	 */
	public DatabaseService(ConfigStore config) {
		this.config = config;
	}

	@Override
	public String id() {
		return "database";
	}

	@Override
	public String name() {
		return "Database";
	}

	@Override
	public synchronized void start() {
		if (this.pools == null) {
			Config current = this.config.current();
			this.pools = new ConnectionPools(current, Database.getDriverClass(current), Database.getUrl(current),
				Database.getReadUrl(current), Database.getUsername(current), Database.getPassword(current));
		}
	}

	@Override
	public synchronized void stop() {
		if (this.pools != null) {
			this.pools.close();
			this.pools = null;
		}
	}

	@Override
	public boolean isHealthy() {
		return this.pools != null;
	}

	@Override
	public Map<String, Object> stats() {
		ConnectionPools current = this.pools;
		return current == null ? Map.of() : current.stats();
	}

	/**
	 * Gets a connection from one of the pools.
	 *
	 * @param pool the kind of connection
	 * @return the connection (to be closed by the caller, which gives it back to the pool)
	 * @throws SQLException if no connection can be obtained
	 * @throws IllegalStateException if the service isn't started
	 */
	Connection getConnection(PoolKind pool) throws SQLException {
		ConnectionPools current = this.pools;
		if (current == null) {
			throw new IllegalStateException("The database access isn't started");
		}
		return current.get(pool).getConnection();
	}
}
