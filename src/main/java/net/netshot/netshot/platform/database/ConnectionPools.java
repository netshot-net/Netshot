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

import java.beans.PropertyVetoException;
import java.util.EnumMap;
import java.util.Map;

import javax.sql.DataSource;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;

/**
 * The pools of database connections (c3p0), one per {@link PoolKind}: read-write, system
 * (small pool for task dispatch and cluster bookkeeping) and, if a read URL is configured, read-only.
 * They are configured by the {@code netshot.db.pooler.*} settings.
 */
@Slf4j
public final class ConnectionPools implements AutoCloseable {

	/** The pools. */
	private final Map<PoolKind, ComboPooledDataSource> pools = new EnumMap<>(PoolKind.class);

	/**
	 * Creates the pools.
	 *
	 * @param driverClass the JDBC driver class
	 * @param url the URL of the (read-write) database
	 * @param readUrl the URL of the read-only database, or null to read from the read-write one
	 * @param username the user name
	 * @param password the password
	 */
	public ConnectionPools(String driverClass, String url, String readUrl, String username, String password) {
		this.pools.put(PoolKind.READ_WRITE, create(PoolKind.READ_WRITE, driverClass, url, username, password,
			Netshot.getConfig("netshot.db.pooler.minpoolsize", 5, 0, Integer.MAX_VALUE),
			Netshot.getConfig("netshot.db.pooler.maxpoolsize", 50, 0, Integer.MAX_VALUE)));
		this.pools.put(PoolKind.SYSTEM, create(PoolKind.SYSTEM, driverClass, url, username, password,
			Netshot.getConfig("netshot.db.pooler.system.minpoolsize", 1, 0, Integer.MAX_VALUE),
			Netshot.getConfig("netshot.db.pooler.system.maxpoolsize", 5, 1, Integer.MAX_VALUE)));
		if (readUrl != null) {
			this.pools.put(PoolKind.READ_ONLY, create(PoolKind.READ_ONLY, driverClass, readUrl, username, password,
				Netshot.getConfig("netshot.db.pooler.minpoolsize", 5, 0, Integer.MAX_VALUE),
				Netshot.getConfig("netshot.db.pooler.maxpoolsize", 50, 0, Integer.MAX_VALUE)));
		}
	}

	private static ComboPooledDataSource create(PoolKind identifier, String driverClass, String url,
		String username, String password, int minPoolSize, int maxPoolSize) {
		ComboPooledDataSource pool = new ComboPooledDataSource(false);
		try {
			pool.setDriverClass(driverClass);
		}
		catch (PropertyVetoException e) {
			throw new IllegalArgumentException("Invalid JDBC driver class " + driverClass, e);
		}
		pool.setDataSourceName(identifier.getSourceName());
		pool.setJdbcUrl(url);
		pool.setUser(username);
		pool.setPassword(password);
		pool.setMinPoolSize(minPoolSize);
		pool.setInitialPoolSize(minPoolSize);
		pool.setMaxPoolSize(maxPoolSize);
		pool.setCheckoutTimeout(Netshot.getConfig("netshot.db.pooler.checkouttimeout", 0, 0, Integer.MAX_VALUE));
		pool.setMaxIdleTime(Netshot.getConfig("netshot.db.pooler.maxidletimeout", 1800, 0, Integer.MAX_VALUE));
		pool.setMaxConnectionAge(Netshot.getConfig("netshot.db.pooler.maxconnectionage", 0, 0, Integer.MAX_VALUE));
		pool.setTestConnectionOnCheckout(Netshot.getConfig("netshot.db.pooler.testconnectiononcheckout", true));
		pool.setTestConnectionOnCheckin(Netshot.getConfig("netshot.db.pooler.testconnectiononcheckin", false));
		pool.setIdleConnectionTestPeriod(Netshot.getConfig("netshot.db.pooler.idleconnectiontestperiod", 0, 0, Integer.MAX_VALUE));
		pool.setConnectionIsValidTimeout(Netshot.getConfig("netshot.db.pooler.connectionisvalidtimeout", 0, 0, Integer.MAX_VALUE));
		pool.setMaxStatements(Netshot.getConfig("netshot.db.pooler.maxstatements", 50, 0, Integer.MAX_VALUE));
		pool.setUnreturnedConnectionTimeout(Netshot.getConfig("netshot.db.pooler.unreturnedconnectiontimeout", 1800, 0, Integer.MAX_VALUE));
		pool.setDebugUnreturnedConnectionStackTraces(true);
		return pool;
	}

	/**
	 * Gets the pool for a kind of connection. Without read-only database, the read-only
	 * connections come from the read-write pool.
	 *
	 * @param identifier the kind of connection
	 * @return the pool
	 */
	public DataSource get(PoolKind identifier) {
		ComboPooledDataSource pool = this.pools.get(identifier);
		return pool == null ? this.pools.get(PoolKind.READ_WRITE) : pool;
	}

	/**
	 * Closes the pools (and their connections).
	 */
	@Override
	public void close() {
		for (ComboPooledDataSource pool : this.pools.values()) {
			try {
				pool.close();
			}
			catch (RuntimeException e) {
				log.warn("Error while closing the {} connection pool", pool.getDataSourceName(), e);
			}
		}
		this.pools.clear();
	}
}
