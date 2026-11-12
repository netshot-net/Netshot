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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestWiring;

/**
 * The sessions of Netshot on PostgreSQL run without JIT compilation (driver property {@code options}).
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("PostgreSQL sessions without JIT")
class DatabaseJitTest {

	private static String jit(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("show jit")) {
			result.next();
			return result.getString(1);
		}
	}

	@Test
	@DisplayName("The pooled connections have JIT off")
	void pooledConnection() {
		Assertions.assertEquals("off", TestWiring.get().transactions().read(dsl -> dsl.fetchValue("show jit", String.class)));
	}

	@Test
	@DisplayName("The unpooled connections (schema update, locks, notifications) have JIT off")
	void unpooledConnection() throws SQLException {
		try (Connection connection = Database.openUnpooledConnection()) {
			Assertions.assertEquals("off", jit(connection));
		}
	}

	@Test
	@DisplayName("An options parameter of the JDBC URL replaces the default one")
	void urlOptions() throws SQLException {
		String url = Database.getUrl();
		String withOptions = url + (url.contains("?") ? "&" : "?") + "options=-c%20jit%3Don";
		try (Connection connection = DriverManager.getConnection(withOptions,
				Database.connectionProperties(withOptions, Database.getUsername(), Database.getPassword()))) {
			Assertions.assertEquals("on", jit(connection));
		}
	}
}
