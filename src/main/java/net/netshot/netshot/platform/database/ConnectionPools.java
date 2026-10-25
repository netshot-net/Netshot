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
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.Setting;

/**
 * The pools of database connections (c3p0), one per {@link PoolKind}: read-write, system
 * (small pool for task dispatch and cluster bookkeeping) and, if a read URL is configured, read-only.
 * They are configured by the {@code netshot.db.pooler.*} settings.
 */
@Slf4j
public final class ConnectionPools implements AutoCloseable {

	/** Min size of the main (read-write and read-only) pools. */
	static final Setting<Integer> MIN_POOL_SIZE = poolSetting("netshot.db.pooler.minpoolsize", 5, 0);

	/** Max size of the main (read-write and read-only) pools. */
	public static final Setting<Integer> MAX_POOL_SIZE = poolSetting("netshot.db.pooler.maxpoolsize", 50, 0);

	/** Min size of the system pool. */
	static final Setting<Integer> SYSTEM_MIN_POOL_SIZE = poolSetting("netshot.db.pooler.system.minpoolsize", 1, 0);

	/** Max size of the system pool. */
	static final Setting<Integer> SYSTEM_MAX_POOL_SIZE = poolSetting("netshot.db.pooler.system.maxpoolsize", 5, 1);

	/** Checkout timeout (ms, 0 = wait indefinitely). */
	static final Setting<Integer> CHECKOUT_TIMEOUT = poolSetting("netshot.db.pooler.checkouttimeout", 0, 0);

	/** Max idle time (s) of a pooled connection. */
	static final Setting<Integer> MAX_IDLE_TIMEOUT = poolSetting("netshot.db.pooler.maxidletimeout", 1800, 0);

	/** Max age (s) of a pooled connection. */
	static final Setting<Integer> MAX_CONNECTION_AGE = poolSetting("netshot.db.pooler.maxconnectionage", 0, 0);

	/** Whether the connections are tested on checkout. */
	static final Setting<Boolean> TEST_CONNECTION_ON_CHECKOUT = Setting.bool("netshot.db.pooler.testconnectiononcheckout")
		.defaultValue(true).restart();

	/** Whether the connections are tested on checkin. */
	static final Setting<Boolean> TEST_CONNECTION_ON_CHECKIN = Setting.bool("netshot.db.pooler.testconnectiononcheckin")
		.defaultValue(false).restart();

	/** Period (s) of the tests of the idle connections. */
	static final Setting<Integer> IDLE_CONNECTION_TEST_PERIOD = poolSetting("netshot.db.pooler.idleconnectiontestperiod", 0, 0);

	/** Timeout (s) of the connection validity check. */
	static final Setting<Integer> CONNECTION_IS_VALID_TIMEOUT = poolSetting("netshot.db.pooler.connectionisvalidtimeout", 0, 0);

	/** Max number of cached statements. */
	static final Setting<Integer> MAX_STATEMENTS = poolSetting("netshot.db.pooler.maxstatements", 50, 0);

	/** Timeout (s) after which a connection which wasn't returned to the pool is closed. */
	static final Setting<Integer> UNRETURNED_CONNECTION_TIMEOUT = poolSetting("netshot.db.pooler.unreturnedconnectiontimeout", 1800, 0);

	private static Setting<Integer> poolSetting(String key, int defaultValue, int min) {
		return Setting.integer(key).defaultValue(defaultValue).range(min, Integer.MAX_VALUE).restart();
	}

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
		Config config = Netshot.config().current();
		this.pools.put(PoolKind.READ_WRITE, create(config, PoolKind.READ_WRITE, driverClass, url, username, password,
			config.get(MIN_POOL_SIZE), config.get(MAX_POOL_SIZE)));
		this.pools.put(PoolKind.SYSTEM, create(config, PoolKind.SYSTEM, driverClass, url, username, password,
			config.get(SYSTEM_MIN_POOL_SIZE), config.get(SYSTEM_MAX_POOL_SIZE)));
		if (readUrl != null) {
			this.pools.put(PoolKind.READ_ONLY, create(config, PoolKind.READ_ONLY, driverClass, readUrl, username, password,
				config.get(MIN_POOL_SIZE), config.get(MAX_POOL_SIZE)));
		}
	}

	private static ComboPooledDataSource create(Config config, PoolKind identifier, String driverClass, String url,
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
		pool.setCheckoutTimeout(config.get(CHECKOUT_TIMEOUT));
		pool.setMaxIdleTime(config.get(MAX_IDLE_TIMEOUT));
		pool.setMaxConnectionAge(config.get(MAX_CONNECTION_AGE));
		pool.setTestConnectionOnCheckout(config.get(TEST_CONNECTION_ON_CHECKOUT));
		pool.setTestConnectionOnCheckin(config.get(TEST_CONNECTION_ON_CHECKIN));
		pool.setIdleConnectionTestPeriod(config.get(IDLE_CONNECTION_TEST_PERIOD));
		pool.setConnectionIsValidTimeout(config.get(CONNECTION_IS_VALID_TIMEOUT));
		pool.setMaxStatements(config.get(MAX_STATEMENTS));
		pool.setUnreturnedConnectionTimeout(config.get(UNRETURNED_CONNECTION_TIMEOUT));
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
