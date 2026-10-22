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

import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Connection pools")
public class ConnectionPoolsTest {

	private static final String URL = "jdbc:postgresql://db.invalid:5432/netshot";

	@Test
	@DisplayName("The pooler settings configure the read-write and read-only pools; the system pool has its own sizes")
	void settings() {
		Properties config = new Properties();
		config.setProperty("netshot.db.pooler.minpoolsize", "3");
		config.setProperty("netshot.db.pooler.maxpoolsize", "17");
		config.setProperty("netshot.db.pooler.system.minpoolsize", "2");
		config.setProperty("netshot.db.pooler.system.maxpoolsize", "4");
		config.setProperty("netshot.db.pooler.checkouttimeout", "1234");
		config.setProperty("netshot.db.pooler.maxidletimeout", "999");
		config.setProperty("netshot.db.pooler.maxconnectionage", "77");
		config.setProperty("netshot.db.pooler.testconnectiononcheckout", "false");
		config.setProperty("netshot.db.pooler.testconnectiononcheckin", "true");
		config.setProperty("netshot.db.pooler.idleconnectiontestperiod", "33");
		config.setProperty("netshot.db.pooler.connectionisvalidtimeout", "4");
		config.setProperty("netshot.db.pooler.maxstatements", "12");
		config.setProperty("netshot.db.pooler.unreturnedconnectiontimeout", "321");
		TestConfig.init(config);

		try (ConnectionPools pools = new ConnectionPools("org.postgresql.Driver", URL, URL + "-read", "user", "secret")) {
			ComboPooledDataSource readWrite = (ComboPooledDataSource) pools.get(TenantIdentifier.READ_WRITE);
			Assertions.assertEquals("ReadWrite", readWrite.getDataSourceName());
			Assertions.assertEquals(URL, readWrite.getJdbcUrl());
			Assertions.assertEquals("org.postgresql.Driver", readWrite.getDriverClass());
			Assertions.assertEquals("user", readWrite.getUser());
			Assertions.assertEquals("secret", readWrite.getPassword());
			Assertions.assertEquals(3, readWrite.getMinPoolSize());
			Assertions.assertEquals(3, readWrite.getInitialPoolSize());
			Assertions.assertEquals(17, readWrite.getMaxPoolSize());
			Assertions.assertEquals(1234, readWrite.getCheckoutTimeout());
			Assertions.assertEquals(999, readWrite.getMaxIdleTime());
			Assertions.assertEquals(77, readWrite.getMaxConnectionAge());
			Assertions.assertFalse(readWrite.isTestConnectionOnCheckout());
			Assertions.assertTrue(readWrite.isTestConnectionOnCheckin());
			Assertions.assertEquals(33, readWrite.getIdleConnectionTestPeriod());
			Assertions.assertEquals(4, readWrite.getConnectionIsValidTimeout());
			Assertions.assertEquals(12, readWrite.getMaxStatements());
			Assertions.assertEquals(321, readWrite.getUnreturnedConnectionTimeout());
			Assertions.assertTrue(readWrite.isDebugUnreturnedConnectionStackTraces());

			ComboPooledDataSource readOnly = (ComboPooledDataSource) pools.get(TenantIdentifier.READ_ONLY);
			Assertions.assertEquals("ReadOnly", readOnly.getDataSourceName());
			Assertions.assertEquals(URL + "-read", readOnly.getJdbcUrl());
			Assertions.assertEquals(17, readOnly.getMaxPoolSize());

			ComboPooledDataSource system = (ComboPooledDataSource) pools.get(TenantIdentifier.SYSTEM);
			Assertions.assertEquals("System", system.getDataSourceName());
			Assertions.assertEquals(URL, system.getJdbcUrl());
			Assertions.assertEquals(2, system.getMinPoolSize());
			Assertions.assertEquals(4, system.getMaxPoolSize());
			Assertions.assertEquals(1234, system.getCheckoutTimeout());
		}
	}

	@Test
	@DisplayName("Without read URL, the read-only connections come from the read-write pool")
	void noReadDatabase() {
		TestConfig.init(new Properties());
		try (ConnectionPools pools = new ConnectionPools("org.postgresql.Driver", URL, null, "user", "secret")) {
			Assertions.assertSame(pools.get(TenantIdentifier.READ_WRITE), pools.get(TenantIdentifier.READ_ONLY));
			ComboPooledDataSource readWrite = (ComboPooledDataSource) pools.get(TenantIdentifier.READ_WRITE);
			Assertions.assertEquals(5, readWrite.getMinPoolSize());
			Assertions.assertEquals(50, readWrite.getMaxPoolSize());
			Assertions.assertEquals(1800, readWrite.getMaxIdleTime());
			Assertions.assertTrue(readWrite.isTestConnectionOnCheckout());
		}
	}
}
